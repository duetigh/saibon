package dev.saibon.data.model

/** One entry from the `items` dataset in the Saibon data repo (see [DataManifest]). */
data class SkyblockItem(
    val id: String = "",
    val name: String = "",
    val tier: String = "",
    val category: String = "",
    val npcSellPrice: Double = 0.0,
    val wikiUrl: String? = null,
    /** `namespace:path` vanilla item id used to render this item's icon, e.g. `"minecraft:wheat"`. */
    val material: String = "minecraft:paper",
    /** True for Coop Soulbound/Soulbound items — can't be traded, sold on the AH, or moved between profiles. */
    val soulbound: Boolean = false,
    /** RGB dye color (e.g. `0xA00000`) applied to [material] when it's a leather-armor piece, e.g. dungeon armor sets — null renders the vanilla undyed leather brown. */
    val color: Int? = null
)
