package dev.saibon.market.ui

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.data.model.SkyblockItem
import dev.saibon.itemlist.ItemTileWidget
import dev.saibon.itemlist.RarityColors
import dev.saibon.market.AuctionFlip
import dev.saibon.market.AuctionFlipRanking
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.AuctionSalesHistoryRepository
import dev.saibon.market.MarketItemMatcher
import dev.saibon.search.query.SearchParser
import dev.saibon.search.query.SearchQuery
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.DropdownWidget
import dev.saibon.ui.widget.SearchEditBox
import dev.saibon.util.ColorCodes
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * `/saibonah` — the "best AH flips" finder (`docs/planning/PLAN.md`'s
 * SkyCofl-style ask): ranks each active low-BIN listing against a locally
 * computed reference resale price ([AuctionSalesHistoryRepository]) and
 * sorts by estimated profit. Same grid/detail layout as the retired
 * `AuctionSearchScreen` (which this replaces); browsing AH prices by
 * category now lives in the real-menu overlay ([AuctionHouseListingPanel])
 * instead of a standalone screen.
 */
class AuctionFlipScreen : Screen(Component.literal("Auction Flip Finder")) {

    companion object {
        private const val MARGIN = 8
        private const val TOP_BAR_HEIGHT = 20
        private const val DETAIL_WIDTH = 200
        private const val TILE_SIZE = 20
        private const val TILE_GAP = 2
        private const val ROW_HEIGHT = 12
        private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
        private const val PRICE_COLOR = 0xFFFFFF55.toInt()
        private const val PROFIT_COLOR = 0xFF55FF55.toInt()
        private const val LOSS_COLOR = 0xFFFF5555.toInt()
        private const val DISCLAIMER_COLOR = 0xFF808080.toInt()
        private const val DISCLAIMER = "Estimates use item-id-level median price; ignores enchants/reforges/stars/pets — not a per-listing appraisal."
    }

    private enum class SortOrder(val label: String) {
        PROFIT_DESC("Profit: High to Low"),
        PROFIT_PERCENT_DESC("Profit %: High to Low"),
        NAME("Name")
    }

    private data class DetailLabel(val x: Int, val y: Int, val text: String, val color: Int)

    private val gridTiles = mutableListOf<ItemTileWidget>()
    private val detailLabels = mutableListOf<DetailLabel>()

    private lateinit var searchBox: EditBox
    private var query: SearchQuery = SearchQuery.Bare("")
    private var sortOrder: SortOrder = SortOrder.PROFIT_DESC
    private var scrollRows: Int = 0
    private var flips: List<AuctionFlip> = emptyList()
    private var flipsByItemId: Map<String, AuctionFlip> = emptyMap()
    private var filteredItems: List<SkyblockItem> = emptyList()
    private var selectedItem: SkyblockItem? = null

    private val gridAreaX get() = MARGIN
    private val gridAreaWidth get() = width - DETAIL_WIDTH - MARGIN * 3
    private val gridAreaY get() = MARGIN * 2 + TOP_BAR_HEIGHT
    private val gridAreaHeight get() = height - gridAreaY - MARGIN - ROW_HEIGHT
    private val detailX get() = gridAreaX + gridAreaWidth + MARGIN

    private fun gridColumns(): Int = max(1, (gridAreaWidth + TILE_GAP) / (TILE_SIZE + TILE_GAP))
    private fun gridVisibleRows(): Int = max(1, gridAreaHeight / (TILE_SIZE + TILE_GAP))

    override fun init() {
        val dropdownWidth = 150
        searchBox = SearchEditBox(
            font, gridAreaX, MARGIN, gridAreaWidth - dropdownWidth - MARGIN, TOP_BAR_HEIGHT,
            Component.literal("Search")
        )
        searchBox.setHint(Component.literal("name, rarity:legendary, minmargin:100000..."))
        searchBox.setResponder { text -> query = SearchParser.parse(text); rebuildGrid() }
        addRenderableWidget(searchBox)

        addRenderableWidget(
            DropdownWidget.create(
                this, gridAreaX + gridAreaWidth - dropdownWidth, MARGIN, dropdownWidth, TOP_BAR_HEIGHT,
                Component.literal("Sort"), SortOrder.entries.toList(), sortOrder, { Component.literal(it.label) }
            ) { sortOrder = it; rebuildGrid() }
        )

        rebuildGrid()
        rebuildDetail()
    }

    private fun computeFlips(): List<AuctionFlip> {
        val config = Saibon.config.data.market
        return AuctionFlipRanking.bestFlips(
            DataRepository.allItems(),
            lowestBinOf = { AuctionPriceRepository.lowestBin(it.id)?.lowestBin?.toDouble() },
            referenceMedianOf = { AuctionSalesHistoryRepository.saleReference(it.id)?.median },
            sampleCountOf = { AuctionSalesHistoryRepository.saleReference(it.id)?.sampleCount ?: 0 },
            taxRatePercent = config.ahTaxRatePercent,
            minSamples = config.salesHistoryMinSamples
        )
    }

    private fun rebuildGrid() {
        gridTiles.forEach { removeWidget(it) }
        gridTiles.clear()

        flips = computeFlips()
        flipsByItemId = flips.associateBy { it.item.id }
        val matching = flips
            .map { it.item }
            .filter { MarketItemMatcher.matches(it, query, { item -> flipsByItemId[item.id]?.lowestBin }) { item -> flipsByItemId[item.id]?.estimatedProfit } }
        filteredItems = when (sortOrder) {
            SortOrder.PROFIT_DESC -> matching.sortedByDescending { flipsByItemId[it.id]?.estimatedProfit ?: Double.MIN_VALUE }
            SortOrder.PROFIT_PERCENT_DESC -> matching.sortedByDescending { flipsByItemId[it.id]?.profitPercent ?: Double.MIN_VALUE }
            SortOrder.NAME -> matching.sortedBy { it.name }
        }

        val columns = gridColumns()
        val totalRows = ceil(filteredItems.size / columns.toDouble()).toInt()
        val maxScroll = max(0, totalRows - gridVisibleRows())
        scrollRows = scrollRows.coerceIn(0, maxScroll)

        val startIndex = scrollRows * columns
        val endIndex = min(filteredItems.size, startIndex + columns * gridVisibleRows())
        for (i in startIndex until endIndex) {
            val item = filteredItems[i]
            val row = i / columns - scrollRows
            val col = i % columns
            val tile = ItemTileWidget(
                gridAreaX + col * (TILE_SIZE + TILE_GAP),
                gridAreaY + row * (TILE_SIZE + TILE_GAP),
                TILE_SIZE,
                item,
                item.id == selectedItem?.id
            ) { picked -> select(picked) }
            gridTiles += tile
            addRenderableWidget(tile)
        }
    }

    private fun select(item: SkyblockItem) {
        selectedItem = item
        gridTiles.forEach { it.setSelected(it.item.id == item.id) }
        rebuildDetail()
    }

    private fun rebuildDetail() {
        detailLabels.clear()
        val item = selectedItem
        if (item == null) {
            detailLabels += DetailLabel(detailX, MARGIN, "Select an item to see its flip estimate", MUTED_TEXT_COLOR)
            return
        }

        var y = MARGIN
        detailLabels += DetailLabel(detailX, y, item.name, RarityColors.of(item.tier))
        y += ROW_HEIGHT
        detailLabels += DetailLabel(detailX, y, "${item.tier} ${item.category}".trim(), MUTED_TEXT_COLOR)
        y += ROW_HEIGHT + MARGIN

        val flip = flipsByItemId[item.id]
        if (flip != null) {
            detailLabels += DetailLabel(detailX, y, "Lowest BIN: ${formatPrice(flip.lowestBin)} coins", PRICE_COLOR)
            y += ROW_HEIGHT
            detailLabels += DetailLabel(detailX, y, "Reference median: ${formatPrice(flip.referenceMedian)} coins", PRICE_COLOR)
            y += ROW_HEIGHT
            detailLabels += DetailLabel(detailX, y, "(median of ${flip.sampleCount} recent sales)", MUTED_TEXT_COLOR)
            y += ROW_HEIGHT + MARGIN
            val color = if (flip.estimatedProfit > 0) PROFIT_COLOR else LOSS_COLOR
            detailLabels += DetailLabel(detailX, y, "Est. profit: ${formatPrice(flip.estimatedProfit)} coins", color)
            y += ROW_HEIGHT
            detailLabels += DetailLabel(detailX, y, "(${"%.1f".format(flip.profitPercent)}%, after est. AH tax)", color)
        }
    }

    private fun formatPrice(value: Double): String = "%,.1f".format(value)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX < detailX && scrollY != 0.0) {
            scrollRows += if (scrollY > 0) -1 else 1
            rebuildGrid()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        Panel.draw(extractor, gridAreaX - MARGIN / 2, MARGIN * 2 + TOP_BAR_HEIGHT - MARGIN / 2, gridAreaWidth + MARGIN, gridAreaHeight + MARGIN)
        Panel.draw(extractor, detailX - MARGIN / 2, MARGIN / 2, DETAIL_WIDTH + MARGIN, height - MARGIN)

        super.extractRenderState(extractor, mouseX, mouseY, delta)

        detailLabels.forEach { ColorCodes.drawText(extractor, font, it.text, it.x, it.y, it.color, false) }
        extractor.textWithWordWrap(font, Component.literal(DISCLAIMER), gridAreaX, height - ROW_HEIGHT, gridAreaWidth, DISCLAIMER_COLOR)

        if (filteredItems.isEmpty()) {
            val message = when {
                !Saibon.config.data.market.ahAutoRefresh -> "Enable AH price refresh in Auction House Prices settings"
                !Saibon.config.data.market.salesHistoryAutoRefresh -> "Enable sales tracking in Auction Flip Finder settings"
                AuctionPriceRepository.isRefreshing || AuctionSalesHistoryRepository.isRefreshing -> "Fetching auction data..."
                else -> "No flips found yet — try again after a few refresh cycles"
            }
            extractor.text(font, message, gridAreaX, gridAreaY, MUTED_TEXT_COLOR, false)
        }

        gridTiles.firstOrNull { it.isHovered }?.let { drawTooltip(extractor, it.item, mouseX, mouseY) }
    }

    private fun drawTooltip(extractor: GuiGraphicsExtractor, item: SkyblockItem, mouseX: Int, mouseY: Int) {
        val flip = flipsByItemId[item.id]
        val lines = buildList {
            add(item.name to RarityColors.of(item.tier))
            add("${item.tier} ${item.category}".trim() to MUTED_TEXT_COLOR)
            if (flip != null) {
                add("Lowest BIN: ${formatPrice(flip.lowestBin)} coins" to PRICE_COLOR)
                add("Est. profit: ${formatPrice(flip.estimatedProfit)} coins" to if (flip.estimatedProfit > 0) PROFIT_COLOR else LOSS_COLOR)
            }
        }
        val boxWidth = lines.maxOf { ColorCodes.width(font, it.first) } + 8
        val boxHeight = lines.size * 10 + 6
        var boxX = mouseX + 12
        var boxY = mouseY - 4
        if (boxX + boxWidth > width) boxX = mouseX - boxWidth - 12
        if (boxY + boxHeight > height) boxY = height - boxHeight

        Panel.draw(extractor, boxX, boxY, boxWidth, boxHeight)
        lines.forEachIndexed { index, (text, color) ->
            ColorCodes.drawText(extractor, font, text, boxX + 4, boxY + 3 + index * 10, color, false)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
