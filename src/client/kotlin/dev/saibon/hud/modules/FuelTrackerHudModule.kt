package dev.saibon.hud.modules

import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.mining.FuelState
import dev.saibon.mining.FuelTracker
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * NEU parity item #4 "Mining / Dwarven overlay" — fuel bar sub-feature: a
 * small persistent panel showing the currently held drill's fuel level as a
 * colored bar plus a `current/max (NN%)` label, reusing [FuelTracker] for all
 * state (see its doc comment for the held-item NBT/lore source and why this
 * tracker needed a direct client-tick poll rather than reusing an existing
 * scoreboard/tab-list event, unlike [MiningCommissionsHudModule]/
 * [PowderTrackerHudModule]/[ForgeTrackerHudModule]). Renders nothing when no
 * drill is currently held (or its fuel/lore couldn't be read).
 */
object FuelTrackerHudModule : HudModule {
    override val id = "fuel_tracker"
    override val title = "Drill Fuel Bar"
    override val defaultState = HudElementState(anchor = HudAnchor.TOP_LEFT, offsetX = 8, offsetY = 180)

    private const val BAR_WIDTH = 120
    private const val BAR_HEIGHT = 6
    private const val PADDING = 4
    private const val LABEL_HEIGHT = 10

    private const val BAR_BACKGROUND = 0xFF3A3A3A.toInt()
    private const val LABEL_COLOR = 0xFFFFFFFF.toInt()

    // Fixed layout regardless of state - the bar/label dimensions never depend on the live fuel
    // reading, so unlike the other mining trackers this doesn't need a separate editorPreviewSize().
    override fun measure(): HudSize = HudSize(BAR_WIDTH + PADDING * 2, LABEL_HEIGHT + BAR_HEIGHT + PADDING * 2)

    override fun render(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val state = FuelTracker.currentState() ?: return
        val font = Minecraft.getInstance().font
        val width = BAR_WIDTH + PADDING * 2
        val height = LABEL_HEIGHT + BAR_HEIGHT + PADDING * 2

        Panel.draw(extractor, 0, 0, width, height)

        val label = "%,d/%,d (%d%%)".format(state.current, state.max, (state.percent * 100).toInt())
        extractor.text(font, label, PADDING, PADDING, LABEL_COLOR, false)

        val barY = PADDING + LABEL_HEIGHT
        extractor.fill(PADDING, barY, PADDING + BAR_WIDTH, barY + BAR_HEIGHT, BAR_BACKGROUND)
        val filledWidth = (BAR_WIDTH * state.percent).toInt()
        if (filledWidth > 0) {
            extractor.fill(PADDING, barY, PADDING + filledWidth, barY + BAR_HEIGHT, barColor(state.percent))
        }
    }

    /** Green at full, sliding through yellow to red as fuel runs low — same intent as NEU's HSB-interpolated fuel bar, using a simple 3-stop lerp instead of an HSB curve to match this codebase's flat `0xAARRGGBB` color constants elsewhere. */
    private fun barColor(percent: Float): Int {
        val (r, g) = if (percent >= 0.5f) {
            (((1f - percent) * 2f) * 255).toInt() to 255
        } else {
            255 to ((percent * 2f) * 255).toInt()
        }
        return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8)
    }
}
