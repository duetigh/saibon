package dev.saibon.hud

import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Unscaled content size of a [HudModule], used by [HudEngine]/`HudEditScreen` to anchor from the right/bottom edges and to draw the edit-mode outline — computed separately from [HudModule.render] since the anchor math needs it *before* drawing. */
data class HudSize(val width: Int, val height: Int)

/**
 * One positionable/scalable in-world HUD overlay, rendered by [HudEngine].
 * `render` is always called already translated so (0,0) is this module's own
 * top-left corner and already scaled by the user's chosen [HudElementState.scale]
 * — implementations just draw their content at unscaled local coordinates.
 */
interface HudModule {
    /** Stable, unique key this module's [HudElementState] is persisted under in `HudConfig.elements` — never rename without a migration. */
    val id: String
    val title: String
    val defaultState: HudElementState get() = HudElementState()

    fun measure(): HudSize
    fun render(extractor: GuiGraphicsExtractor, delta: DeltaTracker)

    /**
     * Size to show for this module's outline in `HudEditScreen` when there's
     * no live data to size against (module not active right now, e.g. not on
     * a mining island) — [measure] legitimately collapses to a near-zero
     * placeholder in that case since there's nothing to render, which made
     * the edit-mode box shrink to a dot instead of showing roughly what the
     * module looks like when it's actually up. Defaults to [measure] for
     * modules whose size never depends on live data.
     */
    fun editorPreviewSize(): HudSize = measure()
}
