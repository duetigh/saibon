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
    val color: Int? = null,
    /** Base64 Mojang skin-profile texture blob applied to [material] when it's `minecraft:player_head` — renders the item's real custom head texture, same as any player head in vanilla. Null renders a default Steve head. */
    val skullTexture: String? = null,
    /** `namespace:path` model id (e.g. `"hypixel_skyblock:item/uncategorized/amber_polished_drill_engine"`) set via `minecraft:item_model` so items with a custom SkyBlock look render correctly through the player's own active Hypixel resource pack, instead of falling back to [material]'s plain vanilla texture. Null renders [material] as-is. */
    val itemModel: String? = null
)
