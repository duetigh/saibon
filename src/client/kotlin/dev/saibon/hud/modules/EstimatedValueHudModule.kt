package dev.saibon.hud.modules

import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.market.value.EstimatedValueResult
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * SkyHanni-style full cost breakdown for whichever item is currently under
 * the mouse — driven entirely by
 * [dev.saibon.market.value.EstimatedValueTooltipAppender]'s
 * `ItemTooltipCallback` registration calling [onHover] every frame a
 * tooltip is shown; there's no separate hover-detection hook. [render]
 * hides itself once [onHover] hasn't fired for [STALE_AFTER_MILLIS] (i.e.
 * the mouse moved off any item) — the same "no active state = no-op
 * render" shape [FlipAlertHudModule] uses, just driven by a rolling
 * staleness window instead of a fixed post-event expiry, since there's no
 * single "stopped hovering" event to key off of.
 */
object EstimatedValueHudModule : HudModule {
    override val id = "estimated_value_breakdown"
    override val title = "Estimated Value Breakdown"
    override val defaultState = HudElementState(anchor = HudAnchor.MIDDLE_RIGHT, offsetX = 8, offsetY = 0)

    private const val STALE_AFTER_MILLIS = 150L
    private const val LINE_HEIGHT = 10
    private const val PADDING = 4

    private const val NAME_COLOR = 0xFFFFAA00.toInt()
    private const val LINE_COLOR = 0xFFE0E0E0.toInt()
    private const val TOTAL_COLOR = 0xFFFF5555.toInt()

    private var active: EstimatedValueResult? = null
    private var lastSeenMillis: Long = 0

    /** Called by [dev.saibon.market.value.EstimatedValueTooltipAppender] every time a tooltip is rendered — doubles as this module's hover signal. */
    fun onHover(result: EstimatedValueResult) {
        active = result
        lastSeenMillis = System.currentTimeMillis()
    }

    private fun currentlyVisible(): EstimatedValueResult? {
        val result = active ?: return null
        if (System.currentTimeMillis() - lastSeenMillis > STALE_AFTER_MILLIS) {
            active = null
            return null
        }
        return result
    }

    override fun measure(): HudSize {
        val result = currentlyVisible() ?: return HudSize(1, 1)
        val font = Minecraft.getInstance().font
        val lines = displayLines(result)
        return HudSize(lines.maxOf { font.width(it.first) } + PADDING * 2, lines.size * LINE_HEIGHT + PADDING * 2)
    }

    override fun render(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val result = currentlyVisible() ?: return
        val font = Minecraft.getInstance().font
        val lines = displayLines(result)
        val width = lines.maxOf { font.width(it.first) } + PADDING * 2
        val height = lines.size * LINE_HEIGHT + PADDING * 2

        Panel.draw(extractor, 0, 0, width, height)
        lines.forEachIndexed { index, (text, color) ->
            extractor.text(font, text, PADDING, PADDING + index * LINE_HEIGHT, color, false)
        }
    }

    private fun displayLines(result: EstimatedValueResult): List<Pair<String, Int>> = buildList {
        add(result.itemName to NAME_COLOR)
        result.lines.forEach { line -> add("${line.label}: ${format(line.cost)}" to LINE_COLOR) }
        add("Total: ${format(result.total)} coins${if (result.isPartial) " (partial)" else ""}" to TOTAL_COLOR)
    }

    private fun format(value: Double): String = "%,.0f".format(value)
}
