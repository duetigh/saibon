package dev.saibon.itemlist

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.data.model.SkyblockItem
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.MarketPriceRepository
import dev.saibon.search.query.SearchParser
import dev.saibon.search.query.SearchQuery
import dev.saibon.search.query.SkyblockItemMatcher
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.DropdownWidget
import dev.saibon.ui.widget.SearchEditBox
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

/**
 * Right-docked "quick browse" companion to the full [ItemListScreen], drawn
 * over the player's own inventory screen by [ItemListSidebarOverlay] instead
 * of replacing it. Narrower than the full screen has room for, so this is
 * grid + search + category/rarity filters only — picking a tile or hitting
 * the expand button opens [ItemListScreen] (pre-selecting that item for the
 * former) for the recipe/used-in/price detail pane this panel has no room
 * for. [ItemListScreen] mirrors that back with a "Minimize" button that
 * returns here.
 */
class ItemListSidebarPanel(private val screen: Screen) {

    companion object {
        private const val MARGIN = 4
        private const val ROW_HEIGHT = 16
        private const val EXPAND_BUTTON_WIDTH = 20
        private const val TILE_SIZE = 18
        private const val TILE_GAP = 2
        private const val ALL = "All"
        private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
        private const val PRICE_COLOR = 0xFFFFFF55.toInt()
    }

    private val widgets = Screens.getWidgets(screen)
    private val managedWidgets = mutableListOf<AbstractWidget>()
    private val gridTiles = mutableListOf<ItemTileWidget>()

    private var query: SearchQuery = SearchQuery.Bare("")
    private var categoryFilter: String = ALL
    private var tierFilter: String = ALL
    private var page: Int = 0
    private var filteredItems: List<SkyblockItem> = emptyList()

    private val panelWidth get() = Saibon.config.data.itemList.sidebarWidth
    private val originX get() = screen.width - panelWidth - MARGIN
    private val originY get() = MARGIN
    private val panelHeight get() = screen.height - MARGIN * 2
    private val headerHeight get() = ROW_HEIGHT * 2 + MARGIN * 2
    private val gridY get() = originY + headerHeight + MARGIN
    private val gridColumns get() = max(1, (panelWidth - MARGIN * 2) / (TILE_SIZE + TILE_GAP))
    private val gridAreaHeight get() = panelHeight - headerHeight - ROW_HEIGHT - MARGIN * 3
    private val gridRows get() = max(1, gridAreaHeight / (TILE_SIZE + TILE_GAP))
    private val pageRowY get() = originY + panelHeight - ROW_HEIGHT - MARGIN

    fun attach() {
        val categories = listOf(ALL) + DataRepository.allItems().map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        val tiers = listOf(ALL) + DataRepository.allItems().map { it.tier }.filter { it.isNotBlank() }.distinct().sorted()

        val searchBox = SearchEditBox(
            Minecraft.getInstance().font,
            originX, originY, panelWidth - MARGIN - EXPAND_BUTTON_WIDTH, ROW_HEIGHT,
            Component.literal("Search")
        )
        searchBox.setHint(Component.literal("Search items..."))
        searchBox.setResponder { text -> query = SearchParser.parse(text); page = 0; rebuildGrid() }
        addManaged(searchBox)

        addManaged(
            Button.builder(Component.literal("⤢")) {
                Minecraft.getInstance().setScreenAndShow(ItemListScreen())
            }.bounds(originX + panelWidth - EXPAND_BUTTON_WIDTH, originY, EXPAND_BUTTON_WIDTH, ROW_HEIGHT).build()
        )

        val halfWidth = (panelWidth - MARGIN) / 2
        addManaged(
            DropdownWidget.create(
                originX, originY + ROW_HEIGHT + MARGIN, halfWidth, ROW_HEIGHT,
                Component.literal("Category"), categories, categoryFilter, { Component.literal(it) }
            ) { categoryFilter = it; page = 0; rebuildGrid() }
        )
        addManaged(
            DropdownWidget.create(
                originX + halfWidth + MARGIN, originY + ROW_HEIGHT + MARGIN, halfWidth, ROW_HEIGHT,
                Component.literal("Rarity"), tiers, tierFilter, { Component.literal(it) }
            ) { tierFilter = it; page = 0; rebuildGrid() }
        )

        addManaged(
            Button.builder(Component.literal("<")) { page = max(0, page - 1); rebuildGrid() }
                .bounds(originX, pageRowY, 20, ROW_HEIGHT).build()
        )
        addManaged(
            Button.builder(Component.literal(">")) { page++; rebuildGrid() }
                .bounds(originX + panelWidth - 20, pageRowY, 20, ROW_HEIGHT).build()
        )

        rebuildGrid()
    }

    fun detach() {
        gridTiles.forEach { widgets.remove(it) }
        gridTiles.clear()
        managedWidgets.forEach { widgets.remove(it) }
        managedWidgets.clear()
    }

    private fun addManaged(widget: AbstractWidget) {
        managedWidgets += widget
        widgets += widget
    }

    private fun computeFilteredItems(): List<SkyblockItem> =
        DataRepository.allItems()
            .filter { categoryFilter == ALL || it.category == categoryFilter }
            .filter { tierFilter == ALL || it.tier == tierFilter }
            .filter { SkyblockItemMatcher.matches(it, query) }
            .sortedBy { it.name }

    private fun rebuildGrid() {
        gridTiles.forEach { widgets.remove(it) }
        gridTiles.clear()

        filteredItems = computeFilteredItems()
        val perPage = gridColumns * gridRows
        val maxPage = max(0, (filteredItems.size - 1) / max(1, perPage))
        page = page.coerceIn(0, maxPage)

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
            ) { picked -> Minecraft.getInstance().setScreenAndShow(ItemListScreen(picked.id)) }
            gridTiles += tile
            widgets += tile
        }
    }

    fun render(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font
        Panel.draw(extractor, originX - MARGIN, originY - MARGIN, panelWidth + MARGIN * 2, panelHeight + MARGIN)

        if (filteredItems.isEmpty()) {
            val message = if (DataRepository.allItems().isEmpty()) "No item data loaded yet" else "No items match"
            extractor.text(font, message, originX, gridY, MUTED_TEXT_COLOR, false)
        }

        gridTiles.firstOrNull { it.isHovered }?.let { tile -> drawTooltip(extractor, font, tile.item, mouseX, mouseY) }
    }

    private fun drawTooltip(extractor: GuiGraphicsExtractor, font: Font, item: SkyblockItem, mouseX: Int, mouseY: Int) {
        val lines = buildList {
            add(item.name to RarityColors.of(item.tier))
            add("${item.tier} ${item.category}".trim() to MUTED_TEXT_COLOR)
            MarketPriceRepository.bazaarPrice(item.id)?.let { bazaar ->
                add("Bazaar buy: %,.1f coins".format(bazaar.buyPrice) to PRICE_COLOR)
            }
            AuctionPriceRepository.lowestBin(item.id)?.let { auction ->
                add("AH lowest BIN: %,.1f coins".format(auction.lowestBin.toDouble()) to PRICE_COLOR)
            }
        }
        val boxWidth = lines.maxOf { font.width(it.first) } + 8
        val boxHeight = lines.size * 10 + 6
        var boxX = mouseX - boxWidth - 12
        var boxY = mouseY - 4
        if (boxX < 0) boxX = mouseX + 12
        if (boxY + boxHeight > screen.height) boxY = screen.height - boxHeight

        Panel.draw(extractor, boxX, boxY, boxWidth, boxHeight)
        lines.forEachIndexed { index, (text, color) ->
            extractor.text(font, text, boxX + 4, boxY + 3 + index * 10, color, false)
        }
    }
}
