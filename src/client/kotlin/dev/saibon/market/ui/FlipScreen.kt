package dev.saibon.market.ui

import dev.saibon.core.Saibon
import dev.saibon.itemlist.ItemTileWidget
import dev.saibon.itemlist.RarityColors
import dev.saibon.market.flip.FlipCandidate
import dev.saibon.market.flip.FlipConfig
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
 * replacing the single-strategy `/saibonah` and `/saibonbz` screens (both
 * retired) as the one flip-browsing screen. Reads [FlipEngine.latestCandidates]
 * (already scanned in the background on its own schedule) rather than
 * computing anything itself, and re-pulls it on a timer ([tick]) so a
 * listing that sold/expired between manual rescans naturally drops out of
 * the grid instead of lingering until the player clicks "Rescan". Auction
 * House candidates are additionally held to the same [FlipConfig.alertMinProfit]/
 * [FlipConfig.alertMinMarginPercent] bar as the toast/chat alerts — this list
 * is meant to be "flips worth taking", not every AH listing that happened to
 * price above zero. A "View auction" button (`/viewauction &lt;uuid&gt;`) appears
 * only for candidates backed by one real listing ([FlipCandidate.auctionUuid] !=
 * null) — Bazaar/craft/NPC flips have no single listing to point at.
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
        /** How often (in client ticks, 20/sec) the grid re-pulls [FlipEngine.latestCandidates] while this screen is open, so a sold/expired auction disappears on its own instead of only on a manual "Rescan" click. */
        private const val AUTO_REFRESH_INTERVAL_TICKS = 100
    }

    private enum class SourceFilter(val label: String) {
        ALL("All finders"),
        AUCTION_HOUSE("Auction House"),
        BAZAAR_MARGIN("Bazaar Margin"),
        NPC_FLIP("NPC Flip"),
        CRAFT_FLIP("Craft Flip")
    }

    private enum class SortMode(val label: String) {
        PROFIT("Sort: Max profit"),
        MARGIN("Sort: Margin %"),
        CHEAPEST("Sort: Cheapest"),
        PROFIT_PER_HOUR("Sort: Profit/hour")
    }

    private data class DetailLabel(val x: Int, val y: Int, val text: String, val color: Int)

    private val gridTiles = mutableListOf<ItemTileWidget>()
    private val tileCandidates = mutableMapOf<ItemTileWidget, FlipCandidate>()
    private val detailLabels = mutableListOf<DetailLabel>()

    private var sourceFilter: SourceFilter = SourceFilter.ALL
    private var sortMode: SortMode = SortMode.PROFIT
    private var scrollRows: Int = 0
    private var displayedCandidates: List<FlipCandidate> = emptyList()
    private var selected: FlipCandidate? = null
    private var actionButton: Button? = null
    private var ticksSinceRefresh: Int = 0

    private val gridAreaX get() = MARGIN
    private val gridAreaWidth get() = width - DETAIL_WIDTH - MARGIN * 3
    private val gridAreaY get() = MARGIN * 2 + TOP_BAR_HEIGHT
    private val gridAreaHeight get() = height - gridAreaY - MARGIN
    private val detailX get() = gridAreaX + gridAreaWidth + MARGIN

    private fun gridColumns(): Int = max(1, (gridAreaWidth + TILE_GAP) / (TILE_SIZE + TILE_GAP))
    private fun gridVisibleRows(): Int = max(1, gridAreaHeight / (TILE_SIZE + TILE_GAP))

    override fun init() {
        val dropdownWidth = 160
        val sortDropdownWidth = 140
        addRenderableWidget(
            DropdownWidget.create(
                this, gridAreaX + gridAreaWidth - dropdownWidth, MARGIN, dropdownWidth, TOP_BAR_HEIGHT,
                Component.literal("Finder"), SourceFilter.entries.toList(), sourceFilter, { Component.literal(it.label) }
            ) { sourceFilter = it; rebuildGrid() }
        )
        addRenderableWidget(
            DropdownWidget.create(
                this, gridAreaX + gridAreaWidth - dropdownWidth - sortDropdownWidth - MARGIN, MARGIN, sortDropdownWidth, TOP_BAR_HEIGHT,
                Component.literal("Sort"), SortMode.entries.toList(), sortMode, { Component.literal(it.label) }
            ) { sortMode = it; rebuildGrid() }
        )
        addRenderableWidget(
            Button.builder(Component.literal("Rescan")) { FlipEngine.scanNow(); rebuildGrid() }
                .bounds(gridAreaX, MARGIN, 70, TOP_BAR_HEIGHT).build()
        )

        rebuildGrid()
    }

    override fun tick() {
        super.tick()
        ticksSinceRefresh++
        if (ticksSinceRefresh >= AUTO_REFRESH_INTERVAL_TICKS) {
            ticksSinceRefresh = 0
            rebuildGrid()
        }
    }

    /** Stable identity across scans — [FlipEngine] produces brand-new [FlipCandidate] instances every cycle, so reference equality (used for tile highlighting/selection carry-over) can't survive a refresh on its own. */
    private fun candidateKey(candidate: FlipCandidate) =
        candidate.auctionUuid ?: "${candidate.sourceFinder}|${candidate.item.id}|${candidate.modifierSignature}"

    /**
     * An Auction House candidate must clear the same profit/margin bar as the
     * toast/chat alert to appear here — a listing priced above zero but below
     * what the player told Saibon counts as "worth alerting on" is still just
     * noise in the flip list, not a flip. Other finders (Bazaar/craft/NPC)
     * already gate their own inclusion at the finder level via their own
     * margin settings, so this only needs to apply to [SourceFilter.AUCTION_HOUSE].
     */
    private fun passesAlertBar(candidate: FlipCandidate, flipConfig: FlipConfig): Boolean {
        if (candidate.sourceFinder != SourceFilter.AUCTION_HOUSE.label) return true
        return candidate.estimatedProfit > 0 &&
            candidate.estimatedProfit >= flipConfig.alertMinProfit &&
            candidate.marginPercent >= flipConfig.alertMinMarginPercent
    }

    private fun rebuildGrid() {
        gridTiles.forEach { removeWidget(it) }
        gridTiles.clear()
        tileCandidates.clear()

        val flipConfig = Saibon.config.data.flip
        val all = FlipEngine.latestCandidates()
        val filtered = when (sourceFilter) {
            SourceFilter.ALL -> all
            else -> all.filter { it.sourceFinder == sourceFilter.label }
        }.filter { passesAlertBar(it, flipConfig) }
        displayedCandidates = when (sortMode) {
            SortMode.PROFIT -> filtered.sortedByDescending { it.estimatedProfit }
            SortMode.MARGIN -> filtered.sortedByDescending { it.marginPercent }
            SortMode.CHEAPEST -> filtered.sortedBy { it.cost }
            // Candidates with no profit/hour figure (everything but forge craft flips) sink to
            // the bottom under this sort rather than being excluded or erroring.
            SortMode.PROFIT_PER_HOUR -> filtered.sortedByDescending { it.profitPerHour ?: Double.NEGATIVE_INFINITY }
        }

        // Carry the selection forward by stable key (not reference — see candidateKey), or drop
        // it if that flip no longer clears the bar / is no longer on the AH at all.
        selected = selected?.let { prev -> displayedCandidates.firstOrNull { candidateKey(it) == candidateKey(prev) } }

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
                candidateKey(candidate) == selected?.let { candidateKey(it) }
            ) { selectCandidate(candidate) }
            gridTiles += tile
            tileCandidates[tile] = candidate
            addRenderableWidget(tile)
        }

        rebuildDetail()
    }

    private fun selectCandidate(candidate: FlipCandidate) {
        selected = candidate
        rebuildDetail()
    }

    private fun rebuildDetail() {
        detailLabels.clear()
        actionButton?.let { removeWidget(it) }
        actionButton = null

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
        y += ROW_HEIGHT
        candidate.profitPerHour?.let { perHour ->
            detailLabels += DetailLabel(detailX, y, "Profit/hour: ${format(perHour)} coins", profitColor)
            y += ROW_HEIGHT
        }
        y += MARGIN

        detailLabels += DetailLabel(detailX, y, "Confidence: ${candidate.confidence}/100", MUTED_TEXT_COLOR)
        y += ROW_HEIGHT
        candidate.volumePerWeek?.let { volume ->
            detailLabels += DetailLabel(detailX, y, "Volume: ~$volume sales/week", MUTED_TEXT_COLOR)
            y += ROW_HEIGHT
        }
        y += MARGIN

        detailLabels += DetailLabel(detailX, y, "Why:", MUTED_TEXT_COLOR)
        y += ROW_HEIGHT
        wrapText(candidate.reason, DETAIL_WIDTH - MARGIN).forEach { line ->
            detailLabels += DetailLabel(detailX, y, line, MUTED_TEXT_COLOR)
            y += ROW_HEIGHT
        }
        y += MARGIN

        val auctionUuid = candidate.auctionUuid
        if (auctionUuid != null) {
            addViewAuctionButton(y, auctionUuid)
        }
    }

    /** Runs `/viewauction <uuid>` to jump straight to this specific listing — not the seller's whole AH page, which could hold dozens of other auctions the player would have to hunt through. Direct-click command, same pattern as [dev.saibon.update.UpdatePrompt]'s command links; no name resolution needed since the auction UUID alone is enough for Hypixel to route to it. */
    private fun addViewAuctionButton(y: Int, auctionUuid: String) {
        val button = Button.builder(Component.literal("View auction")) {
            Minecraft.getInstance().connection?.sendCommand("viewauction $auctionUuid")
        }.bounds(detailX, y, DETAIL_WIDTH - MARGIN, TOP_BAR_HEIGHT).build()
        addRenderableWidget(button)
        actionButton = button
    }

    private fun format(value: Double): String = "%,.1f".format(value)

    /** Greedy word-wrap for the detail panel's "Why" text — vanilla `extractor.text` never wraps, so a long [FlipCandidate.reason] would otherwise run off the right edge of the panel. */
    private fun wrapText(text: String, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && ColorCodes.width(font, candidate) > maxWidth) {
                lines += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

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
