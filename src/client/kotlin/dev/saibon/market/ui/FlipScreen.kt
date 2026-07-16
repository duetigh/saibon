package dev.saibon.market.ui

import dev.saibon.core.Saibon
import dev.saibon.itemlist.ItemTileWidget
import dev.saibon.itemlist.RarityColors
import dev.saibon.market.flip.FlipCandidate
import dev.saibon.market.flip.FlipEngine
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.DropdownWidget
import dev.saibon.util.ColorCodes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * `/saibonflips` — the unified multi-strategy flip table (spec §4.3),
 * replacing the single-strategy `/saibonah` as the main flip-browsing screen
 * (`/saibonah` itself stays as-is — see `AuctionFlipScreen` — since it's
 * still a valid narrower view). Reads [FlipEngine.latestCandidates] (already
 * scanned in the background on its own schedule) rather than computing
 * anything itself; a "Copy /viewauction command" button appears only for
 * candidates backed by one real listing ([FlipCandidate.auctionUuid] != null)
 * — Bazaar/craft/NPC flips have no single listing to point at.
 */
class FlipScreen : Screen(Component.literal("Flip Finder")) {

    companion object {
        private const val MARGIN = 8
        private const val TOP_BAR_HEIGHT = 20
        private const val DETAIL_WIDTH = 210
        private const val TILE_SIZE = 20
        private const val TILE_GAP = 2
        private const val ROW_HEIGHT = 12
        private const val MUTED_TEXT_COLOR = 0xFFA0A0A0.toInt()
        private const val PRICE_COLOR = 0xFFFFFF55.toInt()
        private const val PROFIT_COLOR = 0xFF55FF55.toInt()
        private const val LOSS_COLOR = 0xFFFF5555.toInt()
    }

    private enum class SourceFilter(val label: String) {
        ALL("All finders"),
        AUCTION_HOUSE("Auction House"),
        BAZAAR_MARGIN("Bazaar Margin"),
        NPC_FLIP("NPC Flip"),
        CRAFT_FLIP("Craft Flip")
    }

    private data class DetailLabel(val x: Int, val y: Int, val text: String, val color: Int)

    private val gridTiles = mutableListOf<ItemTileWidget>()
    private val tileCandidates = mutableMapOf<ItemTileWidget, FlipCandidate>()
    private val detailLabels = mutableListOf<DetailLabel>()

    private var sourceFilter: SourceFilter = SourceFilter.ALL
    private var scrollRows: Int = 0
    private var displayedCandidates: List<FlipCandidate> = emptyList()
    private var selected: FlipCandidate? = null
    private var copyButton: Button? = null

    private val gridAreaX get() = MARGIN
    private val gridAreaWidth get() = width - DETAIL_WIDTH - MARGIN * 3
    private val gridAreaY get() = MARGIN * 2 + TOP_BAR_HEIGHT
    private val gridAreaHeight get() = height - gridAreaY - MARGIN
    private val detailX get() = gridAreaX + gridAreaWidth + MARGIN

    private fun gridColumns(): Int = max(1, (gridAreaWidth + TILE_GAP) / (TILE_SIZE + TILE_GAP))
    private fun gridVisibleRows(): Int = max(1, gridAreaHeight / (TILE_SIZE + TILE_GAP))

    override fun init() {
        val dropdownWidth = 160
        addRenderableWidget(
            DropdownWidget.create(
                this, gridAreaX + gridAreaWidth - dropdownWidth, MARGIN, dropdownWidth, TOP_BAR_HEIGHT,
                Component.literal("Finder"), SourceFilter.entries.toList(), sourceFilter, { Component.literal(it.label) }
            ) { sourceFilter = it; rebuildGrid() }
        )
        addRenderableWidget(
            Button.builder(Component.literal("Rescan")) { FlipEngine.scanNow(); rebuildGrid() }
                .bounds(gridAreaX, MARGIN, 70, TOP_BAR_HEIGHT).build()
        )

        rebuildGrid()
        rebuildDetail()
    }

    private fun rebuildGrid() {
        gridTiles.forEach { removeWidget(it) }
        gridTiles.clear()
        tileCandidates.clear()

        val all = FlipEngine.latestCandidates()
        displayedCandidates = when (sourceFilter) {
            SourceFilter.ALL -> all
            else -> all.filter { it.sourceFinder == sourceFilter.label }
        }

        val columns = gridColumns()
        val totalRows = ceil(displayedCandidates.size / columns.toDouble()).toInt()
        val maxScroll = max(0, totalRows - gridVisibleRows())
        scrollRows = scrollRows.coerceIn(0, maxScroll)

        val startIndex = scrollRows * columns
        val endIndex = min(displayedCandidates.size, startIndex + columns * gridVisibleRows())
        for (i in startIndex until endIndex) {
            val candidate = displayedCandidates[i]
            val row = i / columns - scrollRows
            val col = i % columns
            val tile = ItemTileWidget(
                gridAreaX + col * (TILE_SIZE + TILE_GAP),
                gridAreaY + row * (TILE_SIZE + TILE_GAP),
                TILE_SIZE,
                candidate.item,
                candidate === selected
            ) { selectCandidate(candidate) }
            gridTiles += tile
            tileCandidates[tile] = candidate
            addRenderableWidget(tile)
        }
    }

    private fun selectCandidate(candidate: FlipCandidate) {
        selected = candidate
        rebuildDetail()
    }

    private fun rebuildDetail() {
        detailLabels.clear()
        copyButton?.let { removeWidget(it) }
        copyButton = null

        val candidate = selected
        if (candidate == null) {
            detailLabels += DetailLabel(detailX, MARGIN, "Select a flip to see its breakdown", MUTED_TEXT_COLOR)
            return
        }

        var y = MARGIN
        detailLabels += DetailLabel(detailX, y, candidate.item.name, RarityColors.of(candidate.item.tier))
        y += ROW_HEIGHT
        detailLabels += DetailLabel(detailX, y, "via ${candidate.sourceFinder}", MUTED_TEXT_COLOR)
        y += ROW_HEIGHT + MARGIN

        detailLabels += DetailLabel(detailX, y, "Cost: ${format(candidate.cost)} coins", PRICE_COLOR)
        y += ROW_HEIGHT
        detailLabels += DetailLabel(detailX, y, "Est. value: ${format(candidate.estimatedValue)} coins", PRICE_COLOR)
        y += ROW_HEIGHT + MARGIN

        val profitColor = if (candidate.estimatedProfit > 0) PROFIT_COLOR else LOSS_COLOR
        detailLabels += DetailLabel(detailX, y, "Est. profit: ${format(candidate.estimatedProfit)} coins", profitColor)
        y += ROW_HEIGHT
        detailLabels += DetailLabel(detailX, y, "(${"%.1f".format(candidate.marginPercent)}% margin)", profitColor)
        y += ROW_HEIGHT + MARGIN

        detailLabels += DetailLabel(detailX, y, "Why:", MUTED_TEXT_COLOR)
        y += ROW_HEIGHT
        detailLabels += DetailLabel(detailX, y, candidate.reason, MUTED_TEXT_COLOR)
        y += ROW_HEIGHT + MARGIN

        val uuid = candidate.auctionUuid
        if (uuid != null) {
            val button = Button.builder(Component.literal("Copy /viewauction command")) {
                Minecraft.getInstance().keyboardHandler.setClipboard("/viewauction $uuid")
            }.bounds(detailX, y, DETAIL_WIDTH - MARGIN, TOP_BAR_HEIGHT).build()
            addRenderableWidget(button)
            copyButton = button
        }
    }

    private fun format(value: Double): String = "%,.1f".format(value)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX < detailX && scrollY != 0.0) {
            scrollRows += if (scrollY > 0) -1 else 1
            rebuildGrid()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        Panel.draw(extractor, gridAreaX - MARGIN / 2, gridAreaY - MARGIN / 2, gridAreaWidth + MARGIN, gridAreaHeight + MARGIN)
        Panel.draw(extractor, detailX - MARGIN / 2, MARGIN / 2, DETAIL_WIDTH + MARGIN, height - MARGIN)

        super.extractRenderState(extractor, mouseX, mouseY, delta)

        detailLabels.forEach { ColorCodes.drawText(extractor, font, it.text, it.x, it.y, it.color, false) }

        val lastScanned = FlipEngine.lastScanned
        val statusText = if (lastScanned == null) "Scanning..." else "Last scanned: ${lastScanned}"
        extractor.text(font, statusText, gridAreaX + 74, MARGIN + 6, MUTED_TEXT_COLOR, false)

        if (displayedCandidates.isEmpty()) {
            extractor.text(font, "No flips found yet for this filter — enable finders in Flip Finder settings", gridAreaX, gridAreaY, MUTED_TEXT_COLOR, false)
        }

        gridTiles.firstOrNull { it.isHovered }?.let { hovered ->
            tileCandidates[hovered]?.let { drawTooltip(extractor, it, mouseX, mouseY) }
        }
    }

    private fun drawTooltip(extractor: GuiGraphicsExtractor, candidate: FlipCandidate, mouseX: Int, mouseY: Int) {
        val lines = listOf(
            candidate.item.name to RarityColors.of(candidate.item.tier),
            "via ${candidate.sourceFinder}" to MUTED_TEXT_COLOR,
            "Est. profit: ${format(candidate.estimatedProfit)} coins" to if (candidate.estimatedProfit > 0) PROFIT_COLOR else LOSS_COLOR
        )
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
