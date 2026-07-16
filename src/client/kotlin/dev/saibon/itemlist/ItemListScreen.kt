package dev.saibon.itemlist

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.data.model.Recipe
import dev.saibon.data.model.RecipeType
import dev.saibon.data.model.SkyblockItem
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.MarketPriceRepository
import dev.saibon.search.query.SearchParser
import dev.saibon.search.query.SearchQuery
import dev.saibon.search.query.SkyblockItemMatcher
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.DropdownWidget
import dev.saibon.ui.widget.SearchEditBox
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * NEU's core feature: a browsable grid of every item in the Saibon data
 * repo, with a search/filter bar and a detail panel showing recipe, "used
 * in" reverse lookup, NPC price, Bazaar buy/sell ([MarketPriceRepository]),
 * AH lowest-BIN + flip margin ([AuctionPriceRepository]), and a wiki link
 * for the selected item.
 *
 * Deliberately out of scope for this first pass (each needs a subsystem
 * this feature doesn't have yet, tracked separately in
 * `docs/planning/NEU_FEATURE_PARITY.md`):
 * - "Supercraft" (needs real crafting-grid slot interaction).
 * - In-game embedded wiki view (needs a web-view rendering library);
 *   the wiki button opens the system browser instead.
 */
class ItemListScreen(private val initialItemId: String? = null) : Screen(Component.literal("Item List")) {

    companion object {
        private const val MARGIN = 8
        private const val TOP_BAR_HEIGHT = 20
        private const val DETAIL_WIDTH = 190
        private const val MINIMIZE_BUTTON_WIDTH = 70
        private const val TILE_SIZE = 20
        private const val TILE_GAP = 2
        private const val ROW_HEIGHT = 12
        private const val ALL = "All"
        private const val TEXT_COLOR = 0xFFE0E0E0.toInt()
        private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
        private const val PRICE_COLOR = 0xFFFFFF55.toInt()
        private const val FLIP_COLOR = 0xFF55FF55.toInt()
    }

    private data class DetailLabel(val x: Int, val y: Int, val text: String, val color: Int)

    private val gridTiles = mutableListOf<ItemTileWidget>()
    private val detailWidgets = mutableListOf<AbstractWidget>()
    private val detailLabels = mutableListOf<DetailLabel>()

    private lateinit var searchBox: EditBox
    private var query: SearchQuery = SearchQuery.Bare("")
    private var categoryFilter: String = ALL
    private var tierFilter: String = ALL
    private var scrollRows: Int = 0
    private var filteredItems: List<SkyblockItem> = emptyList()

    private var selectedItem: SkyblockItem? = null
    private var selectedRecipeIndex: Int = 0
    private val history = mutableListOf<String>()
    private var historyIndex: Int = -1

    private val gridAreaX get() = MARGIN
    private val gridAreaWidth get() = width - DETAIL_WIDTH - MARGIN * 3
    private val gridAreaY get() = MARGIN * 2 + TOP_BAR_HEIGHT
    private val gridAreaHeight get() = height - gridAreaY - MARGIN
    private val detailX get() = gridAreaX + gridAreaWidth + MARGIN

    private fun gridColumns(): Int = max(1, gridAreaWidth / (TILE_SIZE + TILE_GAP))
    private fun gridVisibleRows(): Int = max(1, gridAreaHeight / (TILE_SIZE + TILE_GAP))

    override fun init() {
        val dropdownWidth = 90
        searchBox = SearchEditBox(
            font, gridAreaX, MARGIN, gridAreaWidth - dropdownWidth * 2 - MARGIN * 2, TOP_BAR_HEIGHT,
            Component.literal("Search")
        )
        searchBox.setHint(Component.literal("name, rarity:legendary, category:weapon..."))
        searchBox.setResponder { text -> query = SearchParser.parse(text); rebuildGrid() }
        addRenderableWidget(searchBox)

        val categories = listOf(ALL) + DataRepository.allItems().map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        val tiers = listOf(ALL) + DataRepository.allItems().map { it.tier }.filter { it.isNotBlank() }.distinct().sorted()

        addRenderableWidget(
            DropdownWidget.create(
                gridAreaX + gridAreaWidth - dropdownWidth * 2 - MARGIN, MARGIN, dropdownWidth, TOP_BAR_HEIGHT,
                Component.literal("Category"), categories, categoryFilter, { Component.literal(it) }
            ) { categoryFilter = it; rebuildGrid() }
        )
        addRenderableWidget(
            DropdownWidget.create(
                gridAreaX + gridAreaWidth - dropdownWidth, MARGIN, dropdownWidth, TOP_BAR_HEIGHT,
                Component.literal("Rarity"), tiers, tierFilter, { Component.literal(it) }
            ) { tierFilter = it; rebuildGrid() }
        )

        addRenderableWidget(
            Button.builder(Component.literal("Minimize")) { minimize() }
                .bounds(detailX + DETAIL_WIDTH - MINIMIZE_BUTTON_WIDTH, MARGIN, MINIMIZE_BUTTON_WIDTH, TOP_BAR_HEIGHT).build()
        )

        rebuildGrid()
        rebuildDetail()

        initialItemId?.let { id -> DataRepository.item(id)?.let { select(it) } }
    }

    /**
     * Returns to the player's own inventory — the sidebar's docking point
     * ([ItemListSidebarOverlay]) reattaches automatically once it's open
     * again, so this is the fullscreen mode's other half of that toggle.
     */
    private fun minimize() {
        val player = Minecraft.getInstance().player ?: return
        Minecraft.getInstance().setScreenAndShow(InventoryScreen(player))
    }

    private fun computeFilteredItems(): List<SkyblockItem> =
        DataRepository.allItems()
            .filter { categoryFilter == ALL || it.category == categoryFilter }
            .filter { tierFilter == ALL || it.tier == tierFilter }
            .filter { SkyblockItemMatcher.matches(it, query) }
            .sortedBy { it.name }

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

    private fun select(item: SkyblockItem, pushHistory: Boolean = true) {
        selectedItem = item
        selectedRecipeIndex = 0
        if (pushHistory) {
            if (historyIndex < history.size - 1) {
                while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
            }
            history += item.id
            historyIndex = history.size - 1
        }
        gridTiles.forEach { it.setSelected(it.item.id == item.id) }
        rebuildDetail()
    }

    private fun navigateBack() {
        if (historyIndex <= 0) return
        historyIndex--
        val item = DataRepository.item(history[historyIndex]) ?: return
        selectedItem = item
        selectedRecipeIndex = 0
        gridTiles.forEach { it.setSelected(it.item.id == item.id) }
        rebuildDetail()
    }

    private fun navigateForward() {
        if (historyIndex >= history.size - 1) return
        historyIndex++
        val item = DataRepository.item(history[historyIndex]) ?: return
        selectedItem = item
        selectedRecipeIndex = 0
        gridTiles.forEach { it.setSelected(it.item.id == item.id) }
        rebuildDetail()
    }

    private fun rebuildDetail() {
        detailWidgets.forEach { removeWidget(it) }
        detailWidgets.clear()
        detailLabels.clear()

        val item = selectedItem
        if (item == null) {
            detailLabels += DetailLabel(detailX, MARGIN, "Select an item to see details", MUTED_TEXT_COLOR)
            return
        }

        var y = MARGIN

        detailWidgets += button(detailX, y, 20, TOP_BAR_HEIGHT, "<") { navigateBack() }
        detailWidgets += button(detailX + 22, y, 20, TOP_BAR_HEIGHT, ">") { navigateForward() }
        detailLabels += DetailLabel(detailX + 48, y + (TOP_BAR_HEIGHT - 8) / 2, item.name, RarityColors.of(item.tier))
        y += TOP_BAR_HEIGHT + MARGIN

        detailLabels += DetailLabel(detailX, y, "${item.tier} ${item.category}".trim(), MUTED_TEXT_COLOR)
        y += ROW_HEIGHT
        detailLabels += DetailLabel(detailX, y, item.id, MUTED_TEXT_COLOR)
        y += ROW_HEIGHT + MARGIN / 2

        if (item.npcSellPrice > 0) {
            detailLabels += DetailLabel(detailX, y, "NPC sell: ${formatPrice(item.npcSellPrice)} coins", PRICE_COLOR)
            y += ROW_HEIGHT + MARGIN / 2
        }

        val bazaar = MarketPriceRepository.bazaarPrice(item.id)
        if (bazaar != null) {
            detailLabels += DetailLabel(detailX, y, "Bazaar buy: ${formatPrice(bazaar.buyPrice)} coins", PRICE_COLOR)
            y += ROW_HEIGHT
            detailLabels += DetailLabel(detailX, y, "Bazaar sell: ${formatPrice(bazaar.sellPrice)} coins", PRICE_COLOR)
            y += ROW_HEIGHT

            val margin = bazaar.sellPrice - bazaar.buyPrice
            val marginPercent = if (bazaar.buyPrice > 0) margin / bazaar.buyPrice * 100 else 0.0
            if (margin > 0 && marginPercent >= Saibon.config.data.market.flipMinMarginPercent) {
                detailLabels += DetailLabel(
                    detailX, y,
                    "Flip margin: ${formatPrice(margin)} coins (${"%.0f".format(marginPercent)}%)",
                    FLIP_COLOR
                )
                y += ROW_HEIGHT
            }
            y += MARGIN / 2
        }

        val auction = AuctionPriceRepository.lowestBin(item.id)
        if (auction != null) {
            detailLabels += DetailLabel(
                detailX, y,
                "AH lowest BIN: ${formatPrice(auction.lowestBin.toDouble())} coins (${auction.activeBinCount} active)",
                PRICE_COLOR
            )
            y += ROW_HEIGHT + MARGIN / 2
        }

        val wikiUrl = item.wikiUrl
        if (wikiUrl != null) {
            detailWidgets += Button.builder(Component.literal("Open Wiki Page")) {
                Util.getPlatform().openUri(wikiUrl)
            }.bounds(detailX, y, DETAIL_WIDTH, TOP_BAR_HEIGHT).build().also { addRenderableWidget(it) }
            y += TOP_BAR_HEIGHT + MARGIN
        }

        y = layoutRecipe(item, y)
        layoutUsedIn(item, y)
    }

    private fun layoutRecipe(item: SkyblockItem, startY: Int): Int {
        var y = startY
        val recipes = DataRepository.recipesFor(item.id)
        detailLabels += DetailLabel(detailX, y, "Recipe", 0xFFC8942A.toInt())
        y += ROW_HEIGHT

        if (recipes.isEmpty()) {
            detailLabels += DetailLabel(detailX, y, "No known recipe", MUTED_TEXT_COLOR)
            return y + ROW_HEIGHT + MARGIN
        }

        selectedRecipeIndex = selectedRecipeIndex.coerceIn(0, recipes.size - 1)
        val recipe = recipes[selectedRecipeIndex]

        if (recipes.size > 1) {
            detailWidgets += button(detailX, y, 20, ROW_HEIGHT + 4, "<") {
                selectedRecipeIndex = (selectedRecipeIndex - 1 + recipes.size) % recipes.size
                rebuildDetail()
            }
            detailWidgets += button(detailX + DETAIL_WIDTH - 20, y, 20, ROW_HEIGHT + 4, ">") {
                selectedRecipeIndex = (selectedRecipeIndex + 1) % recipes.size
                rebuildDetail()
            }
            detailLabels += DetailLabel(
                detailX + 24, y + 2,
                "${recipe.type.name.lowercase().replaceFirstChar { it.uppercase() }} (${selectedRecipeIndex + 1}/${recipes.size})",
                MUTED_TEXT_COLOR
            )
        } else {
            detailLabels += DetailLabel(detailX, y, recipe.type.name.lowercase().replaceFirstChar { it.uppercase() }, MUTED_TEXT_COLOR)
        }
        y += ROW_HEIGHT + 4 + MARGIN / 2

        when (recipe.type) {
            RecipeType.NPC -> {
                detailLabels += DetailLabel(detailX, y, "Cost: ${formatPrice(recipe.npcCost ?: 0.0)} coins", PRICE_COLOR)
                y += ROW_HEIGHT + MARGIN
            }
            RecipeType.CRAFTING, RecipeType.FORGE -> {
                y = layoutIngredientGrid(recipe, y)
            }
        }

        return y
    }

    private fun layoutIngredientGrid(recipe: Recipe, startY: Int): Int {
        var y = startY
        var col = 0
        val columns = max(1, DETAIL_WIDTH / (TILE_SIZE + TILE_GAP))
        for (ingredient in recipe.ingredients) {
            val ingredientItem = DataRepository.item(ingredient.itemId) ?: SkyblockItem(id = ingredient.itemId, name = ingredient.itemId)
            val tileX = detailX + col * (TILE_SIZE + TILE_GAP)
            detailWidgets += ItemTileWidget(tileX, y, TILE_SIZE, ingredientItem, false) { picked -> select(picked) }
                .also { addRenderableWidget(it) }
            detailLabels += DetailLabel(tileX + TILE_SIZE - 6, y + TILE_SIZE - 8, "x${ingredient.amount}", TEXT_COLOR)
            col++
            if (col >= columns) {
                col = 0
                y += TILE_SIZE + TILE_GAP
            }
        }
        if (col != 0) y += TILE_SIZE + TILE_GAP
        return y + MARGIN
    }

    private fun layoutUsedIn(item: SkyblockItem, startY: Int) {
        var y = startY
        val usedIn = DataRepository.allRecipes()
            .filter { recipe -> recipe.ingredients.any { it.itemId.equals(item.id, ignoreCase = true) } }
            .mapNotNull { DataRepository.item(it.itemId) }
            .distinctBy { it.id }
            .sortedBy { it.name }

        detailLabels += DetailLabel(detailX, y, "Used in", 0xFFC8942A.toInt())
        y += ROW_HEIGHT

        if (usedIn.isEmpty()) {
            detailLabels += DetailLabel(detailX, y, "Not used in any known recipe", MUTED_TEXT_COLOR)
            return
        }

        var col = 0
        val columns = max(1, DETAIL_WIDTH / (TILE_SIZE + TILE_GAP))
        for (usedInItem in usedIn) {
            val tileX = detailX + col * (TILE_SIZE + TILE_GAP)
            detailWidgets += ItemTileWidget(tileX, y, TILE_SIZE, usedInItem, false) { picked -> select(picked) }
                .also { addRenderableWidget(it) }
            col++
            if (col >= columns) {
                col = 0
                y += TILE_SIZE + TILE_GAP
            }
        }
    }

    private fun button(x: Int, y: Int, w: Int, h: Int, label: String, onClick: () -> Unit): Button {
        val built = Button.builder(Component.literal(label)) { onClick() }.bounds(x, y, w, h).build()
        addRenderableWidget(built)
        return built
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

        detailLabels.forEach { extractor.text(font, it.text, it.x, it.y, it.color, false) }

        if (filteredItems.isEmpty()) {
            val message = if (DataRepository.allItems().isEmpty()) "No item data loaded yet" else "No items match your filters"
            extractor.text(font, message, gridAreaX, gridAreaY, MUTED_TEXT_COLOR, false)
        }

        (gridTiles.asSequence() + detailWidgets.asSequence().filterIsInstance<ItemTileWidget>())
            .firstOrNull { it.isHovered }
            ?.let { drawTooltip(extractor, it.item, mouseX, mouseY) }
    }

    private fun drawTooltip(extractor: GuiGraphicsExtractor, item: SkyblockItem, mouseX: Int, mouseY: Int) {
        val lines = buildList {
            add(item.name to RarityColors.of(item.tier))
            add("${item.tier} ${item.category}".trim() to MUTED_TEXT_COLOR)
            if (item.npcSellPrice > 0) add("NPC sell: ${formatPrice(item.npcSellPrice)} coins" to PRICE_COLOR)
            MarketPriceRepository.bazaarPrice(item.id)?.let { bazaar ->
                add("Bazaar buy: ${formatPrice(bazaar.buyPrice)} coins" to PRICE_COLOR)
                add("Bazaar sell: ${formatPrice(bazaar.sellPrice)} coins" to PRICE_COLOR)
            }
            AuctionPriceRepository.lowestBin(item.id)?.let { auction ->
                add("AH lowest BIN: ${formatPrice(auction.lowestBin.toDouble())} coins" to PRICE_COLOR)
            }
        }
        val boxWidth = lines.maxOf { font.width(it.first) } + 8
        val boxHeight = lines.size * 10 + 6
        var boxX = mouseX + 12
        var boxY = mouseY - 4
        if (boxX + boxWidth > width) boxX = mouseX - boxWidth - 12
        if (boxY + boxHeight > height) boxY = height - boxHeight

        Panel.draw(extractor, boxX, boxY, boxWidth, boxHeight)
        lines.forEachIndexed { index, (text, color) ->
            extractor.text(font, text, boxX + 4, boxY + 3 + index * 10, color, false)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
