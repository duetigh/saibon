package dev.saibon.hud.modules

import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.mining.CommissionTracker
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * NEU parity item #4 "Mining / Dwarven overlay" — commission-list sub-feature:
 * a small persistent panel listing each active Dwarven Mines / Crystal
 * Hollows / Glacite Tunnel commission (read off the tab list — see
 * [CommissionTracker]'s doc comment) and its completion percentage, reusing
 * [CommissionTracker] for all state, the same read-only-render shape as
 * [SlayerHudModule]. Renders nothing when no commissions are active. The
 * remaining Mining/Dwarven sub-bullets (powder tracker, forge slot timers,
 * fuel bar, pickaxe cooldown, HotM perk preview, Crystal Hollows/Glacite
 * waypoints, chest highlighter) are left for follow-up passes.
 */
object MiningCommissionsHudModule : HudModule {
    override val id = "mining_commissions_tracker"
    override val title = "Mining Commissions"
    override val defaultState = HudElementState(anchor = HudAnchor.TOP_LEFT, offsetX = 8, offsetY = 60)

    private const val LINE_HEIGHT = 10
    private const val PADDING = 4

    private const val HEADER_COLOR = 0xFFFFAA00.toInt()
    private const val PROGRESS_COLOR = 0xFFE0E0E0.toInt()
    private const val COMPLETE_COLOR = 0xFF55FF55.toInt()

    /** Sample content sized like a real commission list, for [editorPreviewSize] only. */
    private val PREVIEW_LINES = listOf("Commissions", "Mine 200 Mithril Ore 45%", "Kill 5 Automatons 100%")

    override fun measure(): HudSize {
        val lines = displayLines()
        if (lines.isEmpty()) return HudSize(1, 1)
        return sizeFor(lines.map { it.first })
    }

    override fun editorPreviewSize(): HudSize = sizeFor(PREVIEW_LINES)

    private fun sizeFor(lines: List<String>): HudSize {
        val font = Minecraft.getInstance().font
        val width = lines.maxOf { font.width(it) } + PADDING * 2
        return HudSize(width, lines.size * LINE_HEIGHT + PADDING * 2)
    }

    override fun render(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val lines = displayLines()
        if (lines.isEmpty()) return
        val font = Minecraft.getInstance().font
        val width = lines.maxOf { font.width(it.first) } + PADDING * 2
        val height = lines.size * LINE_HEIGHT + PADDING * 2

        Panel.draw(extractor, 0, 0, width, height)
        lines.forEachIndexed { index, (text, color) ->
            extractor.text(font, text, PADDING, PADDING + index * LINE_HEIGHT, color, false)
        }
    }

    private fun displayLines(): List<Pair<String, Int>> = buildList {
        val commissions = CommissionTracker.currentCommissions()
        if (commissions.isEmpty()) return@buildList
        add("Commissions" to HEADER_COLOR)
        for (commission in commissions) {
            val color = if (commission.completed) COMPLETE_COLOR else PROGRESS_COLOR
            val percentText = if (commission.percent == commission.percent.toInt().toDouble()) {
                "${commission.percent.toInt()}%"
            } else {
                "%.1f%%".format(commission.percent)
            }
            add("${commission.description} $percentText" to color)
        }
    }
}
