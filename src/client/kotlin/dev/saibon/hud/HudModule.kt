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
}
