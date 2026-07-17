package dev.saibon.ui.overlay

import dev.saibon.calculator.MathExpressionEvaluator
import dev.saibon.client.chat.SaibonChat
import dev.saibon.core.Saibon
import dev.saibon.mixin.AbstractContainerScreenAccessor
import dev.saibon.search.extract.SkyblockItemExtractor
import dev.saibon.search.query.SearchMatcher
import dev.saibon.search.query.SearchParser
import dev.saibon.search.query.SearchQuery
import dev.saibon.ui.widget.SearchEditBox
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * NEU-style search/highlight overlay: a collapsed tab docked under any
 * Skyblock inventory (own inventory, storage, Auction House, Bazaar — all
 * are ordinary [AbstractContainerScreen]s server-side, so no per-screen-type
 * special-casing is needed) that expands into a query box on a single click.
 * A non-blank query highlights matching slots and dims the rest — this only
 * ever reads what's already rendered, per the information-only rule in
 * `docs/planning/PLAN.md`; it never clicks or moves anything.
 *
 * The query text and filter toggle live in this object rather than per
 * `Screen`, since Minecraft constructs a brand-new `Screen` instance every
 * time a container reopens — this is what lets a typed query survive
 * switching AH pages, backpacks, or warping servers. Once there's carried-
 * over text, the box shows expanded immediately on the next inventory
 * rather than starting collapsed again.
 */
object InventorySearchOverlay {
    private const val SLOT_SIZE = 16
    private const val BAR_HEIGHT = 14

    // Held for the life of the client session (not per-Screen, since a new
    // Screen instance is created every time a container reopens) so a typed
    // query and its filter toggle survive switching AH pages, backpacks, or
    // warping to a different server area.
    private var queryText: String = ""
    private var query: SearchQuery = SearchQuery.Bare("")
    private var filterEnabled: Boolean = false

    /**
     * Double-click on the expanded box toggles whether a non-blank query
     * dims/highlights slots, independent of typing/focus — lets a query be
     * typed (e.g. scratch math) without it filtering the inventory, while a
     * single click still just focuses the box for typing like any text field.
     */
    private class FilterToggleSearchBox(
        font: Font, x: Int, y: Int, width: Int, height: Int,
        private val onToggleFilter: () -> Unit
    ) : SearchEditBox(font, x, y, width, height, Component.literal("Search")) {
        override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
            super.onClick(event, doubled)
            if (doubled) onToggleFilter()
        }
    }

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            if (!Saibon.config.data.search.enabled) return@register
            if (!isOnHypixel()) return@register
            attach(screen)
        }
    }

    private fun isOnHypixel(): Boolean =
        Minecraft.getInstance().currentServer?.ip?.contains("hypixel.net", ignoreCase = true) == true

    private fun attach(screen: AbstractContainerScreen<*>) {
        val accessor = screen as AbstractContainerScreenAccessor
        val barX = accessor.getLeftPos()
        val barY = accessor.getTopPos() + accessor.getImageHeight() + 4
        val barWidth = accessor.getImageWidth()

        if (queryText.isNotBlank()) {
            // Reopening with a carried-over query: show it expanded right
            // away rather than making the player click through the
            // collapsed tab again to see what's already typed. Left
            // unfocused so normal inventory keys (hotbar swaps, drop, etc.)
            // keep working until the player deliberately clicks the box.
            expand(screen, barX, barY, barWidth, focus = false)
        } else {
            lateinit var toggle: SearchToggleWidget
            toggle = SearchToggleWidget(barX, barY, barWidth, BAR_HEIGHT) {
                toggle.visible = false
                toggle.active = false
                expand(screen, barX, barY, barWidth, focus = true)
            }
            Screens.getWidgets(screen).add(toggle)
        }

        ScreenEvents.afterExtract(screen).register { _, extractor, _, _, _ -> renderHighlights(screen, extractor) }
    }

    private fun expand(screen: AbstractContainerScreen<*>, x: Int, y: Int, width: Int, focus: Boolean) {
        val box = FilterToggleSearchBox(Minecraft.getInstance().font, x, y, width, BAR_HEIGHT) {
            filterEnabled = !filterEnabled
            val status = if (filterEnabled) "on" else "off"
            Minecraft.getInstance().player?.sendSystemMessage(SaibonChat.message("Search filter $status"))
        }
        box.setHint(Component.literal("prot&gro, enchant:sharpness, rarity:legendary, 2+2m..."))
        box.setResponder { text -> onQueryChanged(box, text) }
        if (queryText.isNotEmpty()) {
            box.setValue(queryText)
            onQueryChanged(box, queryText)
        }
        Screens.getWidgets(screen).add(box)
        if (focus) {
            screen.setFocused(box)
            box.isFocused = true
        }
    }

    /**
     * NEU-style calculator: a query that's pure arithmetic (`2+2*8`) is
     * evaluated instead of parsed as an item filter — the result is shown as
     * grayed-out suggestion text after the cursor, and the query is treated
     * as blank so the inventory renders unfiltered underneath it.
     */
    private fun onQueryChanged(box: SearchEditBox, text: String) {
        queryText = text
        if (MathExpressionEvaluator.looksLikeMath(text)) {
            val result = MathExpressionEvaluator.evaluate(text)
            if (result != null) {
                box.setSuggestion("  = ${MathExpressionEvaluator.format(result)}")
                query = SearchQuery.Bare("")
                return
            }
        }
        box.setSuggestion(null)
        query = SearchParser.parse(text)
    }

    private fun renderHighlights(screen: AbstractContainerScreen<*>, extractor: GuiGraphicsExtractor) {
        if (!filterEnabled) return
        val activeQuery = query
        if (activeQuery is SearchQuery.Bare && activeQuery.value.isBlank()) return

        val accessor = screen as AbstractContainerScreenAccessor
        val config = Saibon.config.data.search

        for (slot in screen.menu.slots) {
            val stack = slot.item
            if (stack.isEmpty) continue

            val absX = accessor.getLeftPos() + slot.x
            val absY = accessor.getTopPos() + slot.y
            val info = SkyblockItemExtractor.extract(stack)

            if (SearchMatcher.matches(info, activeQuery)) {
                extractor.outline(absX - 1, absY - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, config.matchColor)
            } else {
                extractor.fill(absX, absY, absX + SLOT_SIZE, absY + SLOT_SIZE, config.dimColor)
            }
        }
    }
}
