package dev.saibon.itemlist

/**
 * Vanilla `§`-formatting-code colors Hypixel reuses 1:1 for SkyBlock item
 * rarity tiers. The tier name strings themselves are the same ones
 * [dev.saibon.search.extract.SkyblockItemExtractor]'s `RARITY_LINE` regex
 * recognizes, so this map covers exactly the tiers a [dev.saibon.data.model.SkyblockItem]
 * can carry.
 */
object RarityColors {
    private val COLORS: Map<String, Int> = mapOf(
        "COMMON" to 0xFFFFFFFF.toInt(),
        "UNCOMMON" to 0xFF55FF55.toInt(),
        "RARE" to 0xFF5555FF.toInt(),
        "EPIC" to 0xFFAA00AA.toInt(),
        "LEGENDARY" to 0xFFFFAA00.toInt(),
        "MYTHIC" to 0xFFFF55FF.toInt(),
        "DIVINE" to 0xFF55FFFF.toInt(),
        "SPECIAL" to 0xFFFF5555.toInt(),
        "VERY SPECIAL" to 0xFFFF5555.toInt(),
        "SUPREME" to 0xFFFF5555.toInt(),
        "UNOBTAINABLE" to 0xFF555555.toInt(),
        "ADMIN" to 0xFFAA0000.toInt()
    )

    /** Data-repo tiers (from the Hypixel API) use underscores (`VERY_SPECIAL`); lore-text tiers use spaces — normalize both to the space form used as this map's keys. */
    fun of(tier: String): Int = COLORS[tier.uppercase().replace('_', ' ')] ?: 0xFFE0E0E0.toInt()
}
