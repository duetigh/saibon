package dev.saibon.slayer.voidgloom

import dev.saibon.client.chat.SaibonChat
import dev.saibon.core.Saibon
import dev.saibon.slayer.TrackedPlayerState
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.SearchEditBox
import dev.saibon.util.ColorCodes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * `/saibonvoidgloom` GUI half of the boss-owner kill tracker: add/remove tracked usernames
 * and see each one's running kill count next to their real player head, instead of the plain
 * chat-line `list` command. The head icon is a normal vanilla player-head [ItemStack]
 * ([ResolvableProfile.createUnresolved]) — the same client-side name-to-skin resolution
 * vanilla already does for any player-head item, no new network call of Saibon's own.
 */
class VoidgloomTrackerScreen : Screen(Component.literal("Voidgloom Boss Tracker")) {

    companion object {
        private const val MARGIN = 8
        private const val TOP_BAR_HEIGHT = 20
        private const val ROW_HEIGHT = 22
        private const val ICON_SIZE = 16
        private const val REMOVE_BUTTON_WIDTH = 60
        private const val TEXT_COLOR = 0xFFE0E0E0.toInt()
        private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
        private const val KILLS_COLOR = 0xFFFFFF55.toInt()
    }

    private data class Row(val icon: ItemStack, val name: String, val kills: Int, val x: Int, val y: Int)

    private lateinit var nameField: EditBox
    private val rowWidgets = mutableListOf<AbstractWidget>()
    private val rows = mutableListOf<Row>()
    private var page = 0
    private var pendingName = ""

    private val listAreaX get() = MARGIN
    private val listAreaY get() = MARGIN * 2 + TOP_BAR_HEIGHT
    private val listAreaWidth get() = width - MARGIN * 2
    private val listAreaHeight get() = height - listAreaY - MARGIN * 2 - TOP_BAR_HEIGHT

    private fun visibleRows(): Int = max(1, listAreaHeight / ROW_HEIGHT)

    override fun init() {
        nameField = SearchEditBox(font, listAreaX, MARGIN, 160, TOP_BAR_HEIGHT, Component.literal("Name"))
        nameField.setHint(Component.literal("Minecraft username..."))
        nameField.setResponder { text -> pendingName = text }
        addRenderableWidget(nameField)

        addRenderableWidget(
            Button.builder(Component.literal("Add")) { addPending() }
                .bounds(listAreaX + 164, MARGIN, 60, TOP_BAR_HEIGHT).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Close")) { onClose() }
                .bounds(listAreaX + listAreaWidth - 60, MARGIN, 60, TOP_BAR_HEIGHT).build()
        )

        rebuildList()
    }

    private fun addPending() {
        val name = pendingName.trim()
        if (name.isEmpty()) return
        val key = name.lowercase()
        if (!Saibon.config.data.voidgloom.trackedPlayers.containsKey(key)) {
            Saibon.config.data.voidgloom.trackedPlayers[key] = TrackedPlayerState(name)
            Saibon.config.save()
        }
        nameField.setValue("")
        pendingName = ""
        rebuildList()
    }

    private fun remove(key: String) {
        Saibon.config.data.voidgloom.trackedPlayers.remove(key)
        Saibon.config.save()
        rebuildList()
    }

    private fun rebuildList() {
        rowWidgets.forEach { removeWidget(it) }
        rowWidgets.clear()
        rows.clear()

        val tracked = Saibon.config.data.voidgloom.trackedPlayers.entries.sortedBy { it.value.displayName.lowercase() }
        val maxPage = max(0, ceil(tracked.size / visibleRows().toDouble()).toInt() - 1)
        page = page.coerceIn(0, maxPage)

        val startIndex = page * visibleRows()
        val endIndex = min(tracked.size, startIndex + visibleRows())
        for (i in startIndex until endIndex) {
            val (key, state) = tracked[i]
            val rowIndex = i - startIndex
            val y = listAreaY + rowIndex * ROW_HEIGHT
            rows += Row(headIcon(state.displayName), state.displayName, state.kills, listAreaX, y)

            rowWidgets += Button.builder(Component.literal("Remove")) { remove(key) }
                .bounds(listAreaX + listAreaWidth - REMOVE_BUTTON_WIDTH, y + (ROW_HEIGHT - TOP_BAR_HEIGHT) / 2, REMOVE_BUTTON_WIDTH, TOP_BAR_HEIGHT)
                .build().also { addRenderableWidget(it) }
        }

        if (maxPage > 0) {
            val pagerY = listAreaY + listAreaHeight + MARGIN / 2
            rowWidgets += Button.builder(Component.literal("<")) { page--; rebuildList() }
                .bounds(listAreaX, pagerY, 20, TOP_BAR_HEIGHT).build().also { addRenderableWidget(it) }
            rowWidgets += Button.builder(Component.literal(">")) { page++; rebuildList() }
                .bounds(listAreaX + 24, pagerY, 20, TOP_BAR_HEIGHT).build().also { addRenderableWidget(it) }
        }
    }

    private fun headIcon(name: String): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name))
        return stack
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        Panel.draw(extractor, listAreaX - MARGIN / 2, listAreaY - MARGIN / 2, listAreaWidth + MARGIN, listAreaHeight + MARGIN)

        super.extractRenderState(extractor, mouseX, mouseY, delta)

        if (rows.isEmpty()) {
            extractor.text(font, "No tracked players yet — add a username above.", listAreaX, listAreaY, MUTED_TEXT_COLOR, false)
            return
        }

        for (row in rows) {
            extractor.item(row.icon, row.x, row.y + (ROW_HEIGHT - ICON_SIZE) / 2)
            ColorCodes.drawText(extractor, font, row.name, row.x + ICON_SIZE + MARGIN / 2, row.y + 2, TEXT_COLOR, false)
            ColorCodes.drawText(
                extractor, font, "${row.kills} kills",
                row.x + ICON_SIZE + MARGIN / 2, row.y + 2 + font.lineHeight, KILLS_COLOR, false
            )
        }
    }

    override fun isPauseScreen(): Boolean = false
}
