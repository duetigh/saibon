package dev.saibon.market.ui

import dev.saibon.data.DataRepository
import dev.saibon.data.model.SkyblockItem
import dev.saibon.itemlist.ItemTileWidget
import dev.saibon.itemlist.RarityColors
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.MarketItemMatcher
import dev.saibon.mixin.AbstractContainerScreenAccessor
import dev.saibon.search.query.SearchParser
import dev.saibon.search.query.SearchQuery
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.DropdownWidget
import dev.saibon.ui.widget.SearchEditBox
import dev.saibon.util.ColorCodes
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

/**
 * Read-only "browse every AH item without paging through vanilla's own
 * category screens" panel, drawn beside the real Auction House menu by
 * [MarketMenuOverlay]. Populated entirely from [AuctionPriceRepository]'s
 * already-independently-swept lowest-BIN data — never scanned from the real
 * screen's live slots, so unlike [MarketMenuOverlay]'s overpay badges this
 * introduces no live-slot-text-matching risk. No buying/bidding here; that's
 * deliberately out of scope (a misclick on a specific AH listing has no
 * undo, unlike a bazaar order-book quick-buy).
 */
class AuctionHouseListingPanel(private val screen: AbstractContainerScreen<*>) {

    companion object {
        private const val PANEL_WIDTH = 188
        private const val MARGIN = 4
        private const val ROW_HEIGHT = 16
        private const val TILE_SIZE = 18
        private const val TILE_GAP = 2
        private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
        private const val PRICE_COLOR = 0xFFFFFF55.toInt()
        private const val ALL_CATEGORIES = "All"
    }

    private enum class SortOrder(val label: String) {
        PRICE_ASC("Price: Low-High"),
        PRICE_DESC("Price: High-Low"),
        NAME("Name")
    }

    private val widgets = Screens.getWidgets(screen)
    private val managedWidgets = mutableListOf<AbstractWidget>()
    private val gridTiles = mutableListOf<ItemTileWidget>()

    private var query: SearchQuery = SearchQuery.Bare("")
    private var category: String = ALL_CATEGORIES
    private var sortOrder: SortOrder = SortOrder.PRICE_ASC
    private var page: Int = 0
    private var filteredItems: List<SkyblockItem> = emptyList()

    private val accessor get() = screen as AbstractContainerScreenAccessor
    private val originX get() = accessor.getLeftPos() + accessor.getImageWidth() + MARGIN * 2
    private val originY get() = accessor.getTopPos()
    private val gridColumns get() = max(1, (PANEL_WIDTH - MARGIN * 2 + TILE_GAP) / (TILE_SIZE + TILE_GAP))
    private val gridRows get() = 5
    private val panelHeight get() = MARGIN + ROW_HEIGHT * 2 + MARGIN + gridRows * (TILE_SIZE + TILE_GAP) + ROW_HEIGHT + MARGIN

    fun attach() {
        val categories = listOf(ALL_CATEGORIES) + DataRepository.allItems()
            .mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }
            .distinct().sorted()

        val searchBox = SearchEditBox(
            Minecraft.getInstance().font,
            originX, originY + MARGIN, PANEL_WIDTH - MARGIN * 2, ROW_HEIGHT,
            Component.literal("Search")
        )
        searchBox.setHint(Component.literal("Search all AH items..."))
        searchBox.setResponder { text -> query = SearchParser.parse(text); page = 0; rebuildGrid() }
        addManaged(searchBox)

        val halfWidth = (PANEL_WIDTH - MARGIN * 3) / 2
        addManaged(
            DropdownWidget.create(
                screen, originX, originY + MARGIN * 2 + ROW_HEIGHT, halfWidth, ROW_HEIGHT,
                Component.literal("Category"), categories, category, { Component.literal(it) }
            ) { category = it; page = 0; rebuildGrid() }
        )
        addManaged(
            DropdownWidget.create(
                screen, originX + halfWidth + MARGIN, originY + MARGIN * 2 + ROW_HEIGHT, halfWidth, ROW_HEIGHT,
                Component.literal("Sort"), SortOrder.entries.toList(), sortOrder, { Component.literal(it.label) }
            ) { sortOrder = it; rebuildGrid() }
        )

        val pageRowY = originY + MARGIN * 3 + ROW_HEIGHT * 2 + gridRows * (TILE_SIZE + TILE_GAP)
        addManaged(
            Button.builder(Component.literal("<")) { page = max(0, page - 1); rebuildGrid() }
                .bounds(originX, pageRowY, 20, ROW_HEIGHT).build()
        )
        addManaged(
            Button.builder(Component.literal(">")) { page++; rebuildGrid() }
                .bounds(originX + PANEL_WIDTH - MARGIN * 2 - 20, pageRowY, 20, ROW_HEIGHT).build()
        )

        rebuildGrid()
    }

    private fun addManaged(widget: AbstractWidget) {
        managedWidgets += widget
        widgets += widget
    }

    fun detach() {
        gridTiles.forEach { widgets.remove(it) }
        gridTiles.clear()
        managedWidgets.forEach { widgets.remove(it) }
        managedWidgets.clear()
    }

    private fun lowestBin(item: SkyblockItem): Double? = AuctionPriceRepository.lowestBin(item.id)?.lowestBin?.toDouble()

    private fun rebuildGrid() {
        gridTiles.forEach { widgets.remove(it) }
        gridTiles.clear()

        val matching = DataRepository.allItems()
            .filter { AuctionPriceRepository.lowestBin(it.id) != null }
            .filter { category == ALL_CATEGORIES || it.category.equals(category, ignoreCase = true) }
            .filter { MarketItemMatcher.matches(it, query, ::lowestBin) { null } }
        filteredItems = when (sortOrder) {
            SortOrder.PRICE_ASC -> matching.sortedBy { lowestBin(it) ?: Double.MAX_VALUE }
            SortOrder.PRICE_DESC -> matching.sortedByDescending { lowestBin(it) ?: 0.0 }
            SortOrder.NAME -> matching.sortedBy { it.name }
        }

        val perPage = gridColumns * gridRows
        val maxPage = max(0, (filteredItems.size - 1) / max(1, perPage))
        page = page.coerceIn(0, maxPage)

        val gridY = originY + MARGIN * 3 + ROW_HEIGHT * 2
        val startIndex = page * perPage
        val endIndex = min(filteredItems.size, startIndex + perPage)
        for (i in startIndex until endIndex) {
            val item = filteredItems[i]
            val slot = i - startIndex
            val row = slot / gridColumns
            val col = slot % gridColumns
            val tile = ItemTileWidget(
                originX + col * (TILE_SIZE + TILE_GAP),
                gridY + row * (TILE_SIZE + TILE_GAP),
                TILE_SIZE, item, false
            ) { }
            gridTiles += tile
            widgets += tile
        }
    }

    fun renderBackground(extractor: GuiGraphicsExtractor) {
        Panel.draw(extractor, originX - MARGIN, originY - MARGIN, PANEL_WIDTH, panelHeight)
    }

    fun render(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font

        if (filteredItems.isEmpty()) {
            val message = when {
                AuctionPriceRepository.isRefreshing -> "Fetching auction prices..."
                AuctionPriceRepository.lastRefreshed == null -> "Enable AH price refresh in Auction House Prices settings"
                else -> "No items match"
            }
            extractor.text(font, message, originX, originY + MARGIN * 3 + ROW_HEIGHT * 2, MUTED_TEXT_COLOR, false)
        }

        gridTiles.firstOrNull { it.isHovered }?.let { tile -> drawTooltip(extractor, font, tile.item, mouseX, mouseY) }
    }

    private fun drawTooltip(extractor: GuiGraphicsExtractor, font: Font, item: SkyblockItem, mouseX: Int, mouseY: Int) {
        val auction = AuctionPriceRepository.lowestBin(item.id)
        val lines = buildList {
            add(item.name to RarityColors.of(item.tier))
            add("${item.tier} ${item.category}".trim() to MUTED_TEXT_COLOR)
            if (auction != null) {
                add("Lowest BIN: %,d coins".format(auction.lowestBin) to PRICE_COLOR)
                add("${auction.activeBinCount} active BIN listings" to MUTED_TEXT_COLOR)
            }
        }
        val boxWidth = lines.maxOf { ColorCodes.width(font, it.first) } + 8
        val boxHeight = lines.size * 10 + 6
        var boxX = mouseX + 12
        var boxY = mouseY - 4
        if (boxX + boxWidth > screen.width) boxX = mouseX - boxWidth - 12
        if (boxY + boxHeight > screen.height) boxY = screen.height - boxHeight

        Panel.draw(extractor, boxX, boxY, boxWidth, boxHeight)
        lines.forEachIndexed { index, (text, color) ->
            ColorCodes.drawText(extractor, font, text, boxX + 4, boxY + 3 + index * 10, color, false)
        }
    }
}
