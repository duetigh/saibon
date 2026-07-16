package dev.saibon.market.ui

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.data.model.SkyblockItem
import dev.saibon.itemlist.ItemTileWidget
import dev.saibon.itemlist.RarityColors
import dev.saibon.market.BazaarFlipRanking
import dev.saibon.market.CraftFlipRanking
import dev.saibon.market.MarketItemMatcher
import dev.saibon.market.MarketPriceRepository
import dev.saibon.mixin.AbstractContainerScreenAccessor
import dev.saibon.search.query.SearchParser
import dev.saibon.search.query.SearchQuery
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.DropdownWidget
import dev.saibon.ui.widget.SearchEditBox
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * The Bazaar-menu real-screen overlay: category tabs matching the real
 * Bazaar's own grouping, an all-items grid (no more paging through vanilla's
 * category screens one at a time), and three flip rankings
 * ([BazaarFlipRanking], [CraftFlipRanking]). Split out from
 * [MarketMenuOverlay], which now owns only the AH side. Buy/sell/order/offer
 * execution is handled by [BazaarActionController] — read-only browse/flip
 * logic here, click execution lives in [BazaarActionNavigator].
 */
object BazaarMenuOverlay {
    private val BAZAAR_TITLE = Regex("^Bazaar(\\s|$)", RegexOption.IGNORE_CASE)

    private const val VIEW_ALL = "All"
    private const val VIEW_FLIPS_MARGIN = "Flips: Bazaar Margin"
    private const val VIEW_FLIPS_NPC = "Flips: Buy Order -> NPC"
    private const val VIEW_FLIPS_CRAFT = "Flips: Craft"

    private const val MARGIN = 4
    private const val ROW_HEIGHT = 16
    private const val GRID_WIDTH = 160
    private const val DETAIL_WIDTH = 170
    private const val TILE_SIZE = 18
    private const val TILE_GAP = 2
    private const val GRID_ROWS = 6
    private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
    private const val PRICE_COLOR = 0xFFFFFF55.toInt()
    private const val PROFIT_COLOR = 0xFF55FF55.toInt()
    private const val LOSS_COLOR = 0xFFFF5555.toInt()

    private enum class SortOrder(val label: String) {
        BUY_ASC("Buy: Low-High"),
        SELL_DESC("Sell: High-Low"),
        MARGIN_DESC("Margin: High-Low"),
        NAME("Name")
    }

    private data class DetailLine(val text: String, val color: Int)

    private class State(val screen: AbstractContainerScreen<*>) {
        val widgets = Screens.getWidgets(screen)
        val managedWidgets = mutableListOf<AbstractWidget>()
        val gridTiles = mutableListOf<ItemTileWidget>()
        val confirmWidgets = mutableListOf<AbstractWidget>()

        var query: SearchQuery = SearchQuery.Bare("")
        var view: String = VIEW_ALL
        var sortOrder: SortOrder = SortOrder.MARGIN_DESC
        var page: Int = 0
        var filteredItems: List<SkyblockItem> = emptyList()
        var selectedItem: SkyblockItem? = null
        var pendingConfirm: PendingBazaarConfirm? = null

        val accessor get() = screen as AbstractContainerScreenAccessor
        val originX get() = accessor.getLeftPos() + accessor.getImageWidth() + MARGIN * 2
        val originY get() = accessor.getTopPos()
        val gridColumns get() = max(1, (GRID_WIDTH - MARGIN) / (TILE_SIZE + TILE_GAP))
        val detailX get() = originX + GRID_WIDTH + MARGIN
        val basePanelHeight get() = ROW_HEIGHT * 2 + MARGIN * 3 + GRID_ROWS * (TILE_SIZE + TILE_GAP)
        val panelHeight get() = basePanelHeight + if (pendingConfirm != null) (ROW_HEIGHT + MARGIN) * 2 else 0
        val gridY get() = originY + ROW_HEIGHT * 2 + MARGIN * 3
    }

    private val states = IdentityHashMap<Screen, State>()

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            if (!Saibon.config.data.market.bazaarOverlayEnabled) return@register
            if (!isOnHypixel()) return@register
            if (!BAZAAR_TITLE.containsMatchIn(screen.title.string)) return@register
            attach(screen)
        }
    }

    private fun isOnHypixel(): Boolean =
        Minecraft.getInstance().currentServer?.ip?.contains("hypixel.net", ignoreCase = true) == true

    private fun attach(screen: AbstractContainerScreen<*>) {
        val state = State(screen)
        states[screen] = state

        val views = listOf(VIEW_ALL) + DataRepository.allItems()
            .mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }
            .distinct().sorted() + listOf(VIEW_FLIPS_MARGIN, VIEW_FLIPS_NPC, VIEW_FLIPS_CRAFT)

        val searchBox = SearchEditBox(
            Minecraft.getInstance().font, state.originX, state.originY, GRID_WIDTH, ROW_HEIGHT,
            Component.literal("Search")
        )
        searchBox.setHint(Component.literal("Search all Bazaar items..."))
        searchBox.setResponder { text -> state.query = SearchParser.parse(text); state.page = 0; rebuildGrid(state) }
        addManaged(state, searchBox)

        val halfWidth = (GRID_WIDTH - MARGIN) / 2
        addManaged(
            state,
            DropdownWidget.create(
                state.originX, state.originY + ROW_HEIGHT + MARGIN, halfWidth, ROW_HEIGHT,
                Component.literal("View"), views, state.view, { Component.literal(it) }
            ) { state.view = it; state.page = 0; rebuildGrid(state) }
        )
        addManaged(
            state,
            DropdownWidget.create(
                state.originX + halfWidth + MARGIN, state.originY + ROW_HEIGHT + MARGIN, halfWidth, ROW_HEIGHT,
                Component.literal("Sort"), SortOrder.entries.toList(), state.sortOrder, { Component.literal(it.label) }
            ) { state.sortOrder = it; rebuildGrid(state) }
        )

        val pageRowY = state.gridY + GRID_ROWS * (TILE_SIZE + TILE_GAP)
        addManaged(
            state,
            Button.builder(Component.literal("<")) { state.page = max(0, state.page - 1); rebuildGrid(state) }
                .bounds(state.originX, pageRowY, 20, ROW_HEIGHT).build()
        )
        addManaged(
            state,
            Button.builder(Component.literal(">")) { state.page++; rebuildGrid(state) }
                .bounds(state.originX + GRID_WIDTH - 20, pageRowY, 20, ROW_HEIGHT).build()
        )

        val buttonY = state.originY + state.basePanelHeight - ROW_HEIGHT * 2 - MARGIN
        val buttonWidth = (DETAIL_WIDTH - MARGIN) / 2
        for ((index, action) in BazaarAction.entries.withIndex()) {
            val row = index / 2
            val col = index % 2
            addManaged(
                state,
                Button.builder(Component.literal(action.label)) {
                    state.selectedItem?.let { requestAction(state, action, it) }
                }.bounds(
                    state.detailX + col * (buttonWidth + MARGIN),
                    buttonY + row * (ROW_HEIGHT + MARGIN),
                    buttonWidth, ROW_HEIGHT
                ).build()
            )
        }

        ScreenEvents.remove(screen).register {
            detach(state)
            states.remove(screen)
        }
        ScreenEvents.beforeExtract(screen).register { _, extractor, _, _, _ -> renderBackground(state, extractor) }
        ScreenEvents.afterExtract(screen).register { _, extractor, mouseX, mouseY, _ -> render(state, extractor, mouseX, mouseY) }

        rebuildGrid(state)
    }

    private fun addManaged(state: State, widget: AbstractWidget) {
        state.managedWidgets += widget
        state.widgets += widget
    }

    private fun detach(state: State) {
        state.gridTiles.forEach { state.widgets.remove(it) }
        state.gridTiles.clear()
        state.managedWidgets.forEach { state.widgets.remove(it) }
        state.managedWidgets.clear()
        clearConfirm(state)
    }

    private fun buyPrice(item: SkyblockItem): Double? = MarketPriceRepository.bazaarPrice(item.id)?.buyPrice
    private fun sellPrice(item: SkyblockItem): Double? = MarketPriceRepository.bazaarPrice(item.id)?.sellPrice
    private fun margin(item: SkyblockItem): Double? {
        val price = MarketPriceRepository.bazaarPrice(item.id) ?: return null
        return price.sellPrice - price.buyPrice
    }

    private fun rebuildGrid(state: State) {
        state.gridTiles.forEach { state.widgets.remove(it) }
        state.gridTiles.clear()

        val config = Saibon.config.data.market
        val candidates: List<SkyblockItem> = when (state.view) {
            VIEW_FLIPS_MARGIN -> BazaarFlipRanking.marginFlips(DataRepository.allItems(), ::buyPrice, ::sellPrice, config.flipMinMarginPercent).map { it.item }
            VIEW_FLIPS_NPC -> BazaarFlipRanking.npcSellFlips(DataRepository.allItems(), ::buyPrice, config.buyOrderToNpcMinMarginPercent).map { it.item }
            VIEW_FLIPS_CRAFT -> CraftFlipRanking.bestFlips(
                DataRepository.allItems(),
                recipeOf = { DataRepository.recipesFor(it).firstOrNull() },
                marketCostOf = { id -> DataRepository.item(id)?.let { buyPrice(it) } },
                sellPriceOf = { item -> sellPrice(item) },
                minMarginPercent = config.craftFlipMinMarginPercent
            ).map { it.item }
            else -> {
                val matching = DataRepository.allItems()
                    .filter { MarketPriceRepository.bazaarPrice(it.id) != null }
                    .filter { state.view == VIEW_ALL || it.category.equals(state.view, ignoreCase = true) }
                when (state.sortOrder) {
                    SortOrder.BUY_ASC -> matching.sortedBy { buyPrice(it) ?: Double.MAX_VALUE }
                    SortOrder.SELL_DESC -> matching.sortedByDescending { sellPrice(it) ?: 0.0 }
                    SortOrder.MARGIN_DESC -> matching.sortedByDescending { margin(it) ?: 0.0 }
                    SortOrder.NAME -> matching.sortedBy { it.name }
                }
            }
        }
        state.filteredItems = candidates.filter { MarketItemMatcher.matches(it, state.query, ::buyPrice, ::margin) }

        val perPage = state.gridColumns * GRID_ROWS
        val maxPage = max(0, (state.filteredItems.size - 1) / max(1, perPage))
        state.page = state.page.coerceIn(0, maxPage)

        val startIndex = state.page * perPage
        val endIndex = min(state.filteredItems.size, startIndex + perPage)
        for (i in startIndex until endIndex) {
            val item = state.filteredItems[i]
            val slot = i - startIndex
            val row = slot / state.gridColumns
            val col = slot % state.gridColumns
            val tile = ItemTileWidget(
                state.originX + col * (TILE_SIZE + TILE_GAP),
                state.gridY + row * (TILE_SIZE + TILE_GAP),
                TILE_SIZE, item, item.id == state.selectedItem?.id
            ) { picked -> select(state, picked) }
            state.gridTiles += tile
            state.widgets += tile
        }
    }

    private fun select(state: State, item: SkyblockItem) {
        state.selectedItem = item
        state.gridTiles.forEach { it.setSelected(it.item.id == item.id) }
    }

    private fun requestAction(state: State, action: BazaarAction, item: SkyblockItem) {
        if (Saibon.config.data.market.bazaarActionConfirmRequired) {
            showConfirm(state, action, item)
        } else {
            BazaarActionController.execute(action, state.screen, item)
        }
    }

    private fun showConfirm(state: State, action: BazaarAction, item: SkyblockItem) {
        clearConfirm(state)
        state.pendingConfirm = PendingBazaarConfirm(action, item)

        val buttonWidth = (DETAIL_WIDTH - MARGIN) / 2
        val confirmY = state.originY + state.panelHeight - ROW_HEIGHT - MARGIN
        val confirmButton = Button.builder(Component.literal("Confirm")) {
            state.pendingConfirm?.let { pending ->
                clearConfirm(state)
                BazaarActionController.execute(pending.action, state.screen, pending.item)
            }
        }.bounds(state.detailX, confirmY, buttonWidth, ROW_HEIGHT).build()
        val cancelButton = Button.builder(Component.literal("Cancel")) { clearConfirm(state) }
            .bounds(state.detailX + buttonWidth + MARGIN, confirmY, buttonWidth, ROW_HEIGHT).build()

        state.confirmWidgets += confirmButton
        state.confirmWidgets += cancelButton
        state.widgets += confirmButton
        state.widgets += cancelButton
    }

    private fun clearConfirm(state: State) {
        state.confirmWidgets.forEach { state.widgets.remove(it) }
        state.confirmWidgets.clear()
        state.pendingConfirm = null
    }

    private fun renderBackground(state: State, extractor: GuiGraphicsExtractor) {
        Panel.draw(extractor, state.originX - MARGIN, state.originY - MARGIN, GRID_WIDTH + DETAIL_WIDTH + MARGIN * 3, state.panelHeight + MARGIN * 2)
    }

    private fun render(state: State, extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font

        if (state.filteredItems.isEmpty()) {
            extractor.text(font, "No items match", state.originX, state.gridY, MUTED_TEXT_COLOR, false)
        }

        renderDetail(state, extractor, font)
        state.pendingConfirm?.let { pending ->
            val textY = state.originY + state.panelHeight - (ROW_HEIGHT + MARGIN) * 2 + MARGIN
            extractor.text(font, pending.promptText(), state.detailX, textY, PRICE_COLOR, false)
        }
        state.gridTiles.firstOrNull { it.isHovered }?.let { drawTooltip(state, extractor, font, it.item, mouseX, mouseY) }
    }

    private fun renderDetail(state: State, extractor: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font) {
        val item = state.selectedItem
        var y = state.originY
        if (item == null) {
            extractor.text(font, "Select an item", state.detailX, y, MUTED_TEXT_COLOR, false)
            return
        }

        val lines = buildDetailLines(state, item)
        for (line in lines) {
            extractor.text(font, line.text, state.detailX, y, line.color, false)
            y += ROW_HEIGHT - 4
        }
    }

    private fun buildDetailLines(state: State, item: SkyblockItem): List<DetailLine> = buildList {
        add(DetailLine(item.name, RarityColors.of(item.tier)))
        add(DetailLine("${item.tier} ${item.category}".trim(), MUTED_TEXT_COLOR))

        val price = MarketPriceRepository.bazaarPrice(item.id)
        if (price != null) {
            add(DetailLine("Buy: %,.1f".format(price.buyPrice), PRICE_COLOR))
            add(DetailLine("Sell: %,.1f".format(price.sellPrice), PRICE_COLOR))
            val margin = price.sellPrice - price.buyPrice
            add(DetailLine("Margin: %,.1f".format(margin), if (margin > 0) PROFIT_COLOR else LOSS_COLOR))
        }

        when (state.view) {
            VIEW_FLIPS_NPC -> if (item.npcSellPrice > 0 && price != null) {
                val profit = item.npcSellPrice - price.buyPrice
                add(DetailLine("NPC sell: %,.1f".format(item.npcSellPrice), PRICE_COLOR))
                add(DetailLine("Profit: %,.1f".format(profit), if (profit > 0) PROFIT_COLOR else LOSS_COLOR))
            }
            VIEW_FLIPS_CRAFT -> {
                val recipe = DataRepository.recipesFor(item.id).firstOrNull()
                if (recipe != null) {
                    val cost = CraftFlipRanking.bestFlips(
                        listOf(item),
                        recipeOf = { DataRepository.recipesFor(it).firstOrNull() },
                        marketCostOf = { id -> DataRepository.item(id)?.let { buyPrice(it) } },
                        sellPriceOf = { i -> sellPrice(i) },
                        minMarginPercent = Double.NEGATIVE_INFINITY
                    ).firstOrNull()
                    if (cost != null) {
                        add(DetailLine("Craft cost: %,.1f".format(cost.craftCost), PRICE_COLOR))
                        add(DetailLine("Profit: %,.1f".format(cost.profit), if (cost.profit > 0) PROFIT_COLOR else LOSS_COLOR))
                    }
                }
            }
        }
    }

    private fun drawTooltip(state: State, extractor: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, item: SkyblockItem, mouseX: Int, mouseY: Int) {
        val lines = buildDetailLines(state, item)
        val boxWidth = lines.maxOf { font.width(it.text) } + 8
        val boxHeight = lines.size * 10 + 6
        var boxX = mouseX + 12
        var boxY = mouseY - 4
        if (boxX + boxWidth > state.screen.width) boxX = mouseX - boxWidth - 12
        if (boxY + boxHeight > state.screen.height) boxY = state.screen.height - boxHeight

        Panel.draw(extractor, boxX, boxY, boxWidth, boxHeight)
        lines.forEachIndexed { index, line ->
            extractor.text(font, line.text, boxX + 4, boxY + 3 + index * 10, line.color, false)
        }
    }
}
