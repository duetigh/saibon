package dev.saibon.slayer.voidgloom

import net.minecraft.core.BlockPos

/** An axis-aligned sub-area of a Voidgloom Seraph arena, e.g. one Bruiser side or one Void floor. */
data class VoidgloomZone(
    val label: String,
    val minX: Int, val maxX: Int,
    val minY: Int, val maxY: Int,
    val minZ: Int, val maxZ: Int
) {
    fun contains(pos: BlockPos): Boolean =
        pos.x in minX..maxX && pos.y in minY..maxY && pos.z in minZ..maxZ
}

/**
 * Bounding boxes for the 5 Voidgloom Seraph miniboss sub-areas (Bruiser Side 1/2, Void
 * Bottom/Mid/Top). The End is pregenerated, so these coordinates are fixed forever once
 * measured — but nobody has measured them yet from this sandbox (no reachable Hypixel
 * server). All 5 are unset placeholders (`0, 0`) until real numbers are gathered in-game via
 * `/saibonvoidgloomscan <label>` (stand at each zone's corners, run the command at each
 * corner, then replace the placeholder ranges below with the min/max of what was sampled).
 * Until then [zoneFor] returns null for every position and callers fall back to "Unknown
 * area" rather than crashing — each placeholder below uses an inverted (empty) range
 * (`min > max`), which never contains any position, rather than a real-looking `0, 0` box
 * that could silently match a position near the world origin.
 */
object VoidgloomZones {
    // TODO: replace with real coordinates from /saibonvoidgloomscan
    val BRUISER_SIDE_1 = VoidgloomZone("Bruiser Side 1", 1, 0, 1, 0, 1, 0)
    val BRUISER_SIDE_2 = VoidgloomZone("Bruiser Side 2", 1, 0, 1, 0, 1, 0)
    val VOID_BOTTOM = VoidgloomZone("Void Bottom", 1, 0, 1, 0, 1, 0)
    val VOID_MID = VoidgloomZone("Void Mid", 1, 0, 1, 0, 1, 0)
    val VOID_TOP = VoidgloomZone("Void Top", 1, 0, 1, 0, 1, 0)

    private val ALL = listOf(BRUISER_SIDE_1, BRUISER_SIDE_2, VOID_BOTTOM, VOID_MID, VOID_TOP)

    fun zoneFor(pos: BlockPos): VoidgloomZone? = ALL.firstOrNull { it.contains(pos) }
}
