package dev.saibon.market.ui

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.mixin.AbstractContainerScreenAccessor
import dev.saibon.search.query.SearchParser
import dev.saibon.search.query.SearchQuery
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import java.util.IdentityHashMap

/**
 * Client-side re-skin of Hypixel's real Auction House / Bazaar browse GUIs
 * (`docs/planning/NEU_FEATURE_PARITY.md` #2's "custom AH/BZ search screen").
 *
 * The originally-planned approach — reposition real [Slot]s into a
 * freshly-sorted grid and let vanilla's own click routing follow — turned
 * out not to be buildable: `Slot.x`/`Slot.y` are `public final` in this MC
 * build (verified against the actual decompiled 26.2 jar), not mutable like
 * older MC versions this technique is known from. Rather than hand-roll new
 * click-dispatch logic to fake a relayout (untestable in a sandbox with no
 * live Hypixel session, and a bad place to get click-mapping wrong — real
 * coins), this stays render-only: every real listing/product stays in its
 * real, server-assigned slot, exactly where a click already lands correctly.
 * Search highlights the cheapest match and dims non-matches, and AH listings
 * priced far above the known lowest BIN get an overpay outline. Same proven
 * technique as [dev.saibon.ui.overlay.InventorySearchOverlay] (dim/outline
 * over real slots, zero clicks, zero mutation).
 *
 * TODO verify against a live server before relying on it: the title regexes
 * and the lore price-line pattern below are based on Hypixel's historically-
 * known AH/Bazaar GUI conventions, not confirmed live from this sandbox (no
 * reachable Minecraft/Hypixel session here).
 */
object MarketMenuOverlay {
    private val AH_TITLE = Regex("Auction House|BIN Auction View|Auctions Browser|Manage Auctions", RegexOption.IGNORE_CASE)
    private val BAZAAR_TITLE = Regex("^Bazaar(\\s|$)", RegexOption.IGNORE_CASE)
    private val PRICE_LINE = Regex("([\\d,]+(?:\\.\\d+)?)\\s*coins", RegexOption.IGNORE_CASE)

    private const val SLOT_SIZE = 16
    private const val BAR_HEIGHT = 14
    private const val DIM_COLOR = 0x90000000.toInt()
    private const val MATCH_COLOR = 0x8055FF55.toInt()
    private const val BEST_MATCH_COLOR = 0xFFC8942A.toInt()
    private const val OVERPAY_COLOR = 0xFFFF5555.toInt()

    private class State {
        var query: SearchQuery = SearchQuery.Bare("")
        var isAuctionHouse: Boolean = false
    }

    private val states = IdentityHashMap<Screen, State>()

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            if (!Saibon.config.data.market.menuOverlayEnabled) return@register
            if (!isOnHypixel()) return@register
            val title = screen.title.string
            val isAh = AH_TITLE.containsMatchIn(title)
            if (!isAh && !BAZAAR_TITLE.containsMatchIn(title)) return@register
            attach(screen, isAh)
        }
    }

    private fun isOnHypixel(): Boolean =
        Minecraft.getInstance().currentServer?.ip?.contains("hypixel.net", ignoreCase = true) == true

    private fun attach(screen: AbstractContainerScreen<*>, isAuctionHouse: Boolean) {
        val state = State()
        state.isAuctionHouse = isAuctionHouse
        states[screen] = state

        val accessor = screen as AbstractContainerScreenAccessor
        val barX = accessor.getLeftPos()
        val barY = accessor.getTopPos() + accessor.getImageHeight() + 4
        val barWidth = accessor.getImageWidth()

        val box = EditBox(Minecraft.getInstance().font, barX, barY, barWidth, BAR_HEIGHT, Component.literal("Search"))
        box.setHint(Component.literal("Highlight this page's listings... (minprice:1000000)"))
        box.setResponder { text -> state.query = SearchParser.parse(text) }
        Screens.getWidgets(screen).add(box)

        ScreenEvents.remove(screen).register { states.remove(screen) }
        ScreenEvents.afterExtract(screen).register { _, extractor, _, _, _ -> render(screen, state, extractor) }
    }

    private fun render(screen: AbstractContainerScreen<*>, state: State, extractor: GuiGraphicsExtractor) {
        val accessor = screen as AbstractContainerScreenAccessor
        val player = Minecraft.getInstance().player
        val listingSlots = screen.menu.slots.filter { it.container !== player?.inventory && !it.item.isEmpty }

        renderSearchHighlight(listingSlots, state, accessor, extractor)
        if (state.isAuctionHouse) renderOverpayBadges(listingSlots, accessor, extractor)
    }

    private fun renderSearchHighlight(
        listingSlots: List<Slot>,
        state: State,
        accessor: AbstractContainerScreenAccessor,
        extractor: GuiGraphicsExtractor
    ) {
        val query = state.query
        if (query is SearchQuery.Bare && query.value.isBlank()) return

        val matches = listingSlots.filter { matchesQuery(it, query) }
        val cheapest = matches.minByOrNull { parsedPrice(it) ?: Double.MAX_VALUE }

        for (slot in listingSlots) {
            val absX = accessor.getLeftPos() + slot.x
            val absY = accessor.getTopPos() + slot.y
            when {
                slot === cheapest -> extractor.outline(absX - 1, absY - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, BEST_MATCH_COLOR)
                slot in matches -> extractor.outline(absX - 1, absY - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, MATCH_COLOR)
                else -> extractor.fill(absX, absY, absX + SLOT_SIZE, absY + SLOT_SIZE, DIM_COLOR)
            }
        }
    }

    /**
     * Only meaningful for the Auction House: flags a real BIN listing whose
     * displayed price is far above the repo's known lowest BIN for that item.
     * Item id is resolved from the live display name against the data-repo
     * catalog (exact match, falling back to a suffix match so a reforge/star
     * prefix like "Fabled Mithril Coat" still resolves to "Mithril Coat") —
     * best-effort, since live listings don't carry a structured item id here.
     */
    private fun renderOverpayBadges(listingSlots: List<Slot>, accessor: AbstractContainerScreenAccessor, extractor: GuiGraphicsExtractor) {
        val threshold = Saibon.config.data.market.overpayWarningThreshold

        for (slot in listingSlots) {
            val price = parsedPrice(slot) ?: continue
            val displayName = slot.item.hoverName.string
            val item = DataRepository.allItems().firstOrNull { it.name.equals(displayName, ignoreCase = true) }
                ?: DataRepository.allItems().firstOrNull { displayName.endsWith(it.name, ignoreCase = true) }
                ?: continue
            val lowestBin = AuctionPriceRepository.lowestBin(item.id)?.lowestBin ?: continue
            if (lowestBin <= 0 || price <= lowestBin * threshold) continue

            val absX = accessor.getLeftPos() + slot.x
            val absY = accessor.getTopPos() + slot.y
            extractor.outline(absX - 1, absY - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, OVERPAY_COLOR)
        }
    }

    private fun parsedPrice(slot: Slot): Double? {
        val lore = slot.item.get(DataComponents.LORE)?.lines()?.joinToString(" ") { it.string } ?: return null
        val match = PRICE_LINE.find(lore) ?: return null
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    private fun matchesQuery(slot: Slot, query: SearchQuery): Boolean {
        val name = slot.item.hoverName.string
        val price = parsedPrice(slot)
        return LiveListingMatcher.matches(name, price, query)
    }
}
