package dev.saibon.market.ui

import dev.saibon.core.Saibon
import dev.saibon.mixin.AbstractContainerScreenAccessor
import dev.saibon.search.query.SearchQuery
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.DropdownWidget
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import kotlin.math.max

/**
 * NEU-style interactive relayout of the *current page* of a real Hypixel AH
 * screen's listings — opt-in (`Saibon.config.data.market.ahRelayoutEnabled`,
 * default off), unlike [MarketMenuOverlay]'s default render-only dim/outline
 * pass. See [MarketMenuOverlay]'s doc comment for the full rationale and the
 * `Slot.x`/`Slot.y` immutability constraint this works around.
 *
 * Every tile here is backed by a real, currently-open [Slot] — nothing is
 * fetched or synthesized independently (unlike [AuctionHouseListingPanel],
 * which is informational-only and spans the whole catalog via
 * [dev.saibon.market.AuctionPriceRepository]). A tile click never fires the
 * real click directly: it goes through [requestClick], which — unless
 * `ahRelayoutConfirmRequired` is off — requires an explicit Confirm/Cancel
 * before [AbstractContainerScreenAccessor.invokeSlotClicked] is called on
 * that real backing slot, the same call path a genuine mouse click on that
 * real slot would take.
 */
class AuctionRelayoutPanel(private val screen: AbstractContainerScreen<*>) {

    companion object {
        private const val MARGIN = 4
        private const val ROW_HEIGHT = 16
        private const val TILE_SIZE = 18
        private const val TILE_GAP = 2
        private const val DIM_COLOR = 0x90000000.toInt()
        private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
        private const val PRICE_COLOR = 0xFFFFFF55.toInt()
    }

    private enum class SortOrder(val label: String) {
        PRICE_ASC("Price: Low-High"),
        PRICE_DESC("Price: High-Low"),
        NAME("Name")
    }

    private data class PendingRelayoutConfirm(val slot: Slot, val name: String)

    private class AuctionListingTileWidget(
        x: Int, y: Int, size: Int,
        val slot: Slot,
        private val onSelect: (Slot) -> Unit
    ) : AbstractWidget(x, y, size, size, slot.item.hoverName) {
        override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
            onSelect(slot)
        }

        override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            Panel.draw(extractor, x, y, width, height, if (isHovered) Panel.HOVER_BACKGROUND else Panel.BACKGROUND)
            extractor.item(slot.item, x + (width - 16) / 2, y + (height - 16) / 2)
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            output.add(NarratedElementType.TITLE, message)
        }
    }

    private val accessor get() = screen as AbstractContainerScreenAccessor
    private val widgets = Screens.getWidgets(screen)
    private val managedWidgets = mutableListOf<AbstractWidget>()
    private val gridTiles = mutableListOf<AuctionListingTileWidget>()
    private val confirmWidgets = mutableListOf<AbstractWidget>()

    private var query: SearchQuery = SearchQuery.Bare("")
    private var sortOrder: SortOrder = SortOrder.PRICE_ASC
    private var filteredListings: List<Slot> = emptyList()
    private var pendingConfirm: PendingRelayoutConfirm? = null

    private val gridAreaX get() = accessor.getLeftPos()
    private val gridAreaY get() = accessor.getTopPos()
    private val gridAreaWidth get() = accessor.getImageWidth()
    private val gridAreaHeight get() = accessor.getImageHeight()
    private val controlsHeight get() = ROW_HEIGHT + MARGIN
    private val footerHeight get() = ROW_HEIGHT + MARGIN
    private val gridY get() = gridAreaY + controlsHeight
    private val gridBottom get() = gridAreaY + gridAreaHeight - footerHeight
    private val gridColumns get() = max(1, gridAreaWidth / (TILE_SIZE + TILE_GAP))

    fun attach() {
        addManaged(
            DropdownWidget.create(
                gridAreaX, gridAreaY, gridAreaWidth, ROW_HEIGHT,
                Component.literal("Sort"), SortOrder.entries.toList(), sortOrder, { Component.literal(it.label) }
            ) { sortOrder = it; rebuildGrid() }
        )
        rebuildGrid()
    }

    fun detach() {
        gridTiles.forEach { widgets.remove(it) }
        gridTiles.clear()
        managedWidgets.forEach { widgets.remove(it) }
        managedWidgets.clear()
        clearConfirm()
    }

    fun onQueryChanged(newQuery: SearchQuery) {
        query = newQuery
        rebuildGrid()
    }

    private fun addManaged(widget: AbstractWidget) {
        managedWidgets += widget
        widgets += widget
    }

    private fun rebuildGrid() {
        gridTiles.forEach { widgets.remove(it) }
        gridTiles.clear()

        val player = Minecraft.getInstance().player
        val listings = screen.menu.slots
            .filter { it.container !== player?.inventory && !it.item.isEmpty }
            .filter { slot -> LiveListingMatcher.matches(slot.item.hoverName.string, LiveListingMatcher.priceOf(slot), query) }
        filteredListings = when (sortOrder) {
            SortOrder.PRICE_ASC -> listings.sortedBy { LiveListingMatcher.priceOf(it) ?: Double.MAX_VALUE }
            SortOrder.PRICE_DESC -> listings.sortedByDescending { LiveListingMatcher.priceOf(it) ?: 0.0 }
            SortOrder.NAME -> listings.sortedBy { it.item.hoverName.string }
        }

        for ((i, slot) in filteredListings.withIndex()) {
            val row = i / gridColumns
            val col = i % gridColumns
            val tileX = gridAreaX + col * (TILE_SIZE + TILE_GAP)
            val tileY = gridY + row * (TILE_SIZE + TILE_GAP)
            if (tileY + TILE_SIZE > gridBottom) break

            val tile = AuctionListingTileWidget(tileX, tileY, TILE_SIZE, slot) { picked -> requestClick(picked) }
            gridTiles += tile
            widgets += tile
        }
    }

    private fun requestClick(slot: Slot) {
        if (Saibon.config.data.market.ahRelayoutConfirmRequired) {
            showConfirm(slot)
        } else {
            fireClick(slot)
        }
    }

    private fun showConfirm(slot: Slot) {
        clearConfirm()
        pendingConfirm = PendingRelayoutConfirm(slot, slot.item.hoverName.string)

        val buttonWidth = (gridAreaWidth - MARGIN) / 2
        val confirmY = gridAreaY + gridAreaHeight - ROW_HEIGHT
        val confirmButton = Button.builder(Component.literal("Confirm")) {
            pendingConfirm?.let { pending -> clearConfirm(); fireClick(pending.slot) }
        }.bounds(gridAreaX, confirmY, buttonWidth, ROW_HEIGHT).build()
        val cancelButton = Button.builder(Component.literal("Cancel")) { clearConfirm() }
            .bounds(gridAreaX + buttonWidth + MARGIN, confirmY, buttonWidth, ROW_HEIGHT).build()

        confirmWidgets += confirmButton
        confirmWidgets += cancelButton
        widgets += confirmButton
        widgets += cancelButton
    }

    private fun clearConfirm() {
        confirmWidgets.forEach { widgets.remove(it) }
        confirmWidgets.clear()
        pendingConfirm = null
    }

    /** Same call path a genuine mouse click on [slot] would take — see this class's doc comment. */
    private fun fireClick(slot: Slot) {
        accessor.invokeSlotClicked(slot, slot.index, 0, ContainerInput.PICKUP)
    }

    fun renderBackground(extractor: GuiGraphicsExtractor) {
        extractor.fill(gridAreaX, gridAreaY, gridAreaX + gridAreaWidth, gridAreaY + gridAreaHeight, DIM_COLOR)
        Panel.draw(extractor, gridAreaX - MARGIN, gridAreaY - MARGIN, gridAreaWidth + MARGIN * 2, gridAreaHeight + MARGIN * 2)
    }

    fun render(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font

        if (filteredListings.isEmpty()) {
            extractor.text(font, "No listings on this page match", gridAreaX, gridY, MUTED_TEXT_COLOR, false)
        }

        pendingConfirm?.let { pending ->
            extractor.text(font, "Buy \"${pending.name}\"?", gridAreaX, gridAreaY + gridAreaHeight - ROW_HEIGHT * 2 - MARGIN, PRICE_COLOR, false)
        }

        gridTiles.firstOrNull { it.isHovered }?.let { tile -> drawTooltip(extractor, font, tile.slot, mouseX, mouseY) }
    }

    private fun drawTooltip(extractor: GuiGraphicsExtractor, font: Font, slot: Slot, mouseX: Int, mouseY: Int) {
        val price = LiveListingMatcher.priceOf(slot)
        val lines = buildList {
            add(slot.item.hoverName.string to 0xFFFFFFFF.toInt())
            if (price != null) add("%,.1f coins".format(price) to PRICE_COLOR)
        }
        val boxWidth = lines.maxOf { font.width(it.first) } + 8
        val boxHeight = lines.size * 10 + 6
        var boxX = mouseX + 12
        var boxY = mouseY - 4
        if (boxX + boxWidth > screen.width) boxX = mouseX - boxWidth - 12
        if (boxY + boxHeight > screen.height) boxY = screen.height - boxHeight

        Panel.draw(extractor, boxX, boxY, boxWidth, boxHeight)
        lines.forEachIndexed { index, (text, color) ->
            extractor.text(font, text, boxX + 4, boxY + 3 + index * 10, color, false)
        }
    }
}
