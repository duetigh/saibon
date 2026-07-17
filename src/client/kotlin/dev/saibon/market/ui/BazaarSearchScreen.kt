package dev.saibon.market.ui

import dev.saibon.data.DataRepository
import dev.saibon.data.model.SkyblockItem
import dev.saibon.itemlist.ItemTileWidget
import dev.saibon.itemlist.RarityColors
import dev.saibon.market.BazaarFlipRanking
import dev.saibon.market.MarketItemMatcher
import dev.saibon.market.MarketPriceRepository
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
 * Read-only Bazaar price browser (`docs/planning/NEU_FEATURE_PARITY.md` #2),
 * sourced from [MarketPriceRepository] — modeled on
 * [dev.saibon.itemlist.ItemListScreen]'s grid, minus recipe/used-in
 * navigation which doesn't apply here. This never touches the real vanilla
 * Bazaar menu; that's [MarketMenuOverlay]'s job.
 */
class BazaarSearchScreen : Screen(Component.literal("Bazaar Prices")) {

    companion object {
        private const val MARGIN = 8
        private const val TOP_BAR_HEIGHT = 20
        private const val DETAIL_WIDTH = 190
        private const val TILE_SIZE = 20
        private const val TILE_GAP = 2
        private const val ROW_HEIGHT = 12
        private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
        private const val PRICE_COLOR = 0xFFFFFF55.toInt()
        private const val FLIP_COLOR = 0xFF55FF55.toInt()
    }

    private enum class SortOrder(val label: String) {
        BUY_ASC("Buy: Low to High"),
        SELL_DESC("Sell: High to Low"),
        MARGIN_DESC("Flip Margin: High to Low"),
        NAME("Name")
    }

    private data class DetailLabel(val x: Int, val y: Int, val text: String, val color: Int)

    private val gridTiles = mutableListOf<ItemTileWidget>()
    private val detailLabels = mutableListOf<DetailLabel>()

    private lateinit var searchBox: EditBox
    private var query: SearchQuery = SearchQuery.Bare("")
    private var sortOrder: SortOrder = SortOrder.MARGIN_DESC
    private var scrollRows: Int = 0
    private var filteredItems: List<SkyblockItem> = emptyList()
    private var selectedItem: SkyblockItem? = null

    private val gridAreaX get() = MARGIN
    private val gridAreaWidth get() = width - DETAIL_WIDTH - MARGIN * 3
    private val gridAreaY get() = MARGIN * 2 + TOP_BAR_HEIGHT
    private val gridAreaHeight get() = height - gridAreaY - MARGIN
    private val detailX get() = gridAreaX + gridAreaWidth + MARGIN

    private fun gridColumns(): Int = max(1, (gridAreaWidth + TILE_GAP) / (TILE_SIZE + TILE_GAP))
    private fun gridVisibleRows(): Int = max(1, gridAreaHeight / (TILE_SIZE + TILE_GAP))

    override fun init() {
        val dropdownWidth = 130
        searchBox = SearchEditBox(
            font, gridAreaX, MARGIN, gridAreaWidth - dropdownWidth - MARGIN, TOP_BAR_HEIGHT,
            Component.literal("Search")
        )
        searchBox.setHint(Component.literal("name, rarity:legendary, minmargin:1000..."))
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

    private fun buyPrice(item: SkyblockItem): Double? = MarketPriceRepository.bazaarPrice(item.id)?.buyPrice?.takeIf { it > 0 }
    private fun sellPrice(item: SkyblockItem): Double? = MarketPriceRepository.bazaarPrice(item.id)?.sellPrice?.takeIf { it > 0 }
    private fun margin(item: SkyblockItem): Double? = BazaarFlipRanking.margin(buyPrice(item), sellPrice(item))

    private fun computeFilteredItems(): List<SkyblockItem> {
        val matching = DataRepository.allItems()
            .filter { MarketPriceRepository.bazaarPrice(it.id) != null }
            .filter { MarketItemMatcher.matches(it, query, ::buyPrice, ::margin) }
        return when (sortOrder) {
            SortOrder.BUY_ASC -> matching.sortedWith(compareBy<SkyblockItem> { buyPrice(it) == null }.thenBy { buyPrice(it) })
            SortOrder.SELL_DESC -> matching.sortedWith(compareBy<SkyblockItem> { sellPrice(it) == null }.thenByDescending { sellPrice(it) })
            SortOrder.MARGIN_DESC -> matching.sortedWith(compareBy<SkyblockItem> { margin(it) == null }.thenByDescending { margin(it) })
            SortOrder.NAME -> matching.sortedBy { it.name }
        }
    }

    private fun rebuildGrid() {
        gridTiles.forEach { removeWidget(it) }
        gridTiles.clear()

        filteredItems = computeFilteredItems()
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
            detailLabels += DetailLabel(detailX, MARGIN, "Select an item to see its Bazaar price", MUTED_TEXT_COLOR)
            return
        }

        var y = MARGIN
        detailLabels += DetailLabel(detailX, y, item.name, RarityColors.of(item.tier))
        y += ROW_HEIGHT
        detailLabels += DetailLabel(detailX, y, "${item.tier} ${item.category}".trim(), MUTED_TEXT_COLOR)
        y += ROW_HEIGHT + MARGIN

        val buy = buyPrice(item)
        val sell = sellPrice(item)
        if (buy != null || sell != null) {
            detailLabels += DetailLabel(detailX, y, "Buy: ${buy?.let { "${formatPrice(it)} coins" } ?: "N/A"}", PRICE_COLOR)
            y += ROW_HEIGHT
            detailLabels += DetailLabel(detailX, y, "Sell: ${sell?.let { "${formatPrice(it)} coins" } ?: "N/A"}", PRICE_COLOR)
            y += ROW_HEIGHT
            val margin = margin(item)
            detailLabels += DetailLabel(
                detailX, y, "Flip margin: ${margin?.let { "${formatPrice(it)} coins" } ?: "N/A"}",
                if (margin != null && margin > 0) FLIP_COLOR else MUTED_TEXT_COLOR
            )
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

        if (filteredItems.isEmpty()) {
            extractor.text(font, "No items match your filters", gridAreaX, gridAreaY, MUTED_TEXT_COLOR, false)
        }

        gridTiles.firstOrNull { it.isHovered }?.let { drawTooltip(extractor, it.item, mouseX, mouseY) }
    }

    private fun drawTooltip(extractor: GuiGraphicsExtractor, item: SkyblockItem, mouseX: Int, mouseY: Int) {
        val buy = buyPrice(item)
        val sell = sellPrice(item)
        val lines = buildList {
            add(item.name to RarityColors.of(item.tier))
            add("${item.tier} ${item.category}".trim() to MUTED_TEXT_COLOR)
            if (buy != null || sell != null) {
                add("Buy: ${buy?.let { "${formatPrice(it)} coins" } ?: "N/A"}" to PRICE_COLOR)
                add("Sell: ${sell?.let { "${formatPrice(it)} coins" } ?: "N/A"}" to PRICE_COLOR)
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
