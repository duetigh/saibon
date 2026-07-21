package dev.saibon.hud.modules

import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.mining.ForgeTracker
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * NEU parity item #4 "Mining / Dwarven overlay" — forge slot timers
 * sub-feature: a small persistent panel mirroring Hypixel's own "Forges:"
 * tab-list section verbatim, reusing [ForgeTracker] for all state (see its
 * doc comment for why the per-line text is shown raw rather than parsed
 * into a structured time-remaining model — no source confirms that line
 * shape, only that the header itself is real), the same read-only-render
 * shape as [MiningCommissionsHudModule]/[PowderTrackerHudModule]. Renders
 * nothing when the "Forges:" section isn't present (i.e. not on a mining
 * island, or no forge queue active). Fuel bar, pickaxe cooldown, HotM perk
 * preview, Crystal Hollows/Glacite waypoints, and the chest highlighter are
 * still left for follow-up passes.
 */
object ForgeTrackerHudModule : HudModule {
    override val id = "forge_tracker"
    override val title = "Forge Tracker"
    override val defaultState = HudElementState(anchor = HudAnchor.TOP_LEFT, offsetX = 8, offsetY = 140)

    private const val LINE_HEIGHT = 10
    private const val PADDING = 4

    private const val HEADER_COLOR = 0xFFFFAA00.toInt()
    private const val LINE_COLOR = 0xFFE0E0E0.toInt()

    /** Sample content sized like a real forge queue, for [editorPreviewSize] only. */
    private val PREVIEW_LINES = listOf("Forges", "Slot 1: Mithril Cube - 2h 30m", "Slot 2: Empty")

    override fun measure(): HudSize {
        val lines = displayLines()
        if (lines.isEmpty()) return HudSize(1, 1)
        return sizeFor(lines)
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
        val width = lines.maxOf { font.width(it) } + PADDING * 2
        val height = lines.size * LINE_HEIGHT + PADDING * 2

        Panel.draw(extractor, 0, 0, width, height)
        lines.forEachIndexed { index, text ->
            val color = if (index == 0) HEADER_COLOR else LINE_COLOR
            extractor.text(font, text, PADDING, PADDING + index * LINE_HEIGHT, color, false)
        }
    }

    private fun displayLines(): List<String> = buildList {
        val lines = ForgeTracker.currentLines()
        if (lines.isEmpty()) return@buildList
        add("Forges")
        for (line in lines) add(line.text)
    }
}
