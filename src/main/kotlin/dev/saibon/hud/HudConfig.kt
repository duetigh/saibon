package dev.saibon.hud

/** Anchor point a HUD module's position is measured from, matching the 9-point layout `HudEditScreen` exposes. */
enum class HudAnchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

/** Persisted position/scale/visibility for one HUD module. [offsetX]/[offsetY] are plain pixel offsets from [anchor]'s corner/edge — independent of [scale], so dragging in `HudEditScreen` is a simple delta-add regardless of anchor or zoom. */
data class HudElementState(
    var anchor: HudAnchor = HudAnchor.TOP_LEFT,
    var offsetX: Int = 4,
    var offsetY: Int = 4,
    var scale: Float = 1.0f,
    var enabled: Boolean = true
)

/** One [HudElementState] per registered HUD module, keyed by its id. A module with no entry yet falls back to its own declared default (see `HudModule.defaultState`) — additive, no migration needed when a new module ships. */
data class HudConfig(
    var elements: MutableMap<String, HudElementState> = mutableMapOf()
)
