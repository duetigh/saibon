package dev.saibon.hud.modules

import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.mining.PowderState
import dev.saibon.mining.PowderTracker
import dev.saibon.mining.PowderType
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * NEU parity item #4 "Mining / Dwarven overlay" — powder tracker sub-feature:
 * a small persistent panel showing current Mithril/Gemstone/Glacite Powder
 * totals plus session gain and an hourly rate, reusing [PowderTracker] for
 * all state (see its doc comment for the scoreboard-line source and the
 * "session = since client launch" simplification), the same read-only-render
 * shape as [MiningCommissionsHudModule]/[SlayerHudModule]. Renders nothing
 * when no powder line has been seen yet (i.e. not on a mining island).
 */
object PowderTrackerHudModule : HudModule {
    override val id = "powder_tracker"
    override val title = "Powder Tracker"
    override val defaultState = HudElementState(anchor = HudAnchor.BOTTOM_LEFT, offsetX = 8, offsetY = 8)

    private const val LINE_HEIGHT = 10
    private const val PADDING = 4

    private const val HEADER_COLOR = 0xFFFFAA00.toInt()
    private const val MITHRIL_COLOR = 0xFF00AA00.toInt()
    private const val GEMSTONE_COLOR = 0xFFFF55FF.toInt()
    private const val GLACITE_COLOR = 0xFF55FFFF.toInt()

    /** Sample content sized like a real powder readout, for [editorPreviewSize] only. */
    private val PREVIEW_LINES = listOf(
        "Powder",
        "Mithril: 1,234,567 (+1,234) (12,345/h)",
        "Gemstone: 234,567 (+234) (2,345/h)",
        "Glacite: 34,567 (+34) (345/h)"
    )

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

    private fun colorFor(type: PowderType): Int = when (type) {
        PowderType.MITHRIL -> MITHRIL_COLOR
        PowderType.GEMSTONE -> GEMSTONE_COLOR
        PowderType.GLACITE -> GLACITE_COLOR
    }

    private fun formatState(state: PowderState): String {
        val sign = if (state.gainedThisSession >= 0) "+" else ""
        val rate = if (state.perHour == 0.0) {
            ""
        } else {
            " (%,.0f/h)".format(state.perHour)
        }
        return "${state.type.label}: %,d ($sign%,d%s)".format(state.current, state.gainedThisSession, rate)
    }

    private fun displayLines(): List<Pair<String, Int>> = buildList {
        val states = PowderTracker.currentStates()
        if (states.isEmpty()) return@buildList
        add("Powder" to HEADER_COLOR)
        for (state in states) {
            add(formatState(state) to colorFor(state.type))
        }
    }
}
