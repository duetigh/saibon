package dev.saibon.market.value

/**
 * Fixed Hypixel game-mechanic constants [EstimatedItemValueCalculator] needs
 * that aren't market data (a reforge's flat NPC apply fee, which stone item
 * grants a given reforge, how much essence a dungeon star costs) — sourced
 * from the Hypixel Skyblock Fandom wiki's API (`Reforging/Prices`,
 * `Reforge_Stones/Armor`, and `Necron's_Armor`'s own embedded
 * `{{Essence Crafting}}` data block) during planning, not guessed. Kept as
 * plain Kotlin constants rather than a `data/`-repo dataset: these only
 * change when Hypixel patches the game (handled by a normal mod-code
 * update), not on a market cadence, so routing them through
 * `DataRepository`/`data/index.json`'s version+checksum machinery would add
 * ceremony for no benefit — and sidesteps the "forgot to bump the checksum"
 * regression class entirely (see project history).
 *
 * Coverage is intentionally partial: only what was possible to verify against
 * a real source during planning. Anything not listed here (Crimson Isle
 * "Fuming" upgrades, non-armor reforge stones, essence types other than
 * Wither) is a genuine gap, not an oversight — callers must treat a `null`
 * result as "no data," never as "free."
 */
object ModifierCostTables {
    /** Flat NPC coin fee to apply *any* reforge at the Reforge Anvil, by the target item's rarity — independent of which stone (if any) grants the reforge. Source: `Reforging/Prices`. */
    private val REFORGE_APPLY_FEE_BY_RARITY: Map<String, Double> = mapOf(
        "COMMON" to 250.0,
        "UNCOMMON" to 500.0,
        "RARE" to 1000.0,
        "EPIC" to 2500.0,
        "LEGENDARY" to 5000.0,
        "MYTHIC" to 10000.0,
        "DIVINE" to 15000.0,
        "SPECIAL" to 25000.0,
        "VERY_SPECIAL" to 50000.0
    )

    /**
     * Reforge internal key (as stored in `ExtraAttributes.modifier`, lowercase)
     * to the itemId of the Reforge Stone that grants it. Only reforges that
     * actually require a stone are listed — the many "free" reforges
     * (Sharp/Heavy/Light/etc., chosen directly at the Reforge Anvil with no
     * material cost) correctly have no entry here, since their entire cost
     * *is* [REFORGE_APPLY_FEE_BY_RARITY]. Seeded from `Reforge_Stones/Armor`
     * only — sword/bow/mining/fishing/equipment/vacuum reforge stones aren't
     * covered yet.
     */
    private val REFORGE_STONE_ITEM_ID: Map<String, String> = mapOf(
        "candied" to "CANDY_CORN",
        "submerged" to "DEEP_SEA_ORB",
        "reinforced" to "RARE_DIAMOND",
        "cubic" to "MOLTEN_CUBE",
        "hyper" to "ENDSTONE_GEODE",
        "undead" to "PREMIUM_FLESH",
        "ridiculous" to "RED_NOSE",
        "necrotic" to "NECROMANCER_BROOCH",
        "spiked" to "DRAGON_SCALE",
        "jaded" to "JADERALD",
        "loving" to "RED_SCARF",
        "perfect" to "DIAMOND_ATOM",
        "renowned" to "DRAGON_HORN",
        "giant" to "GIANT_TOOTH",
        "empowered" to "SADAN_BROOCH",
        "ancient" to "PRECURSOR_GEAR",
        "bustling" to "SKYMART_BROCHURE",
        "mossy" to "OVERGROWN_GRASS",
        "groovy" to "MANGROVE_GEM"
    )

    /** Essence itemId (Bazaar-tradeable, resolved live via [dev.saibon.market.flip.IngredientPriceResolver]) plus the essence count required for each of stars 1 through 5, in order — cost to reach a given star is the running sum up to that index. */
    private data class StarCostTable(val essenceItemId: String, val perStar: List<Int>)

    /**
     * Dungeon-star essence cost by itemId. Only the Wither-essence "Wither
     * Armor lineage" (base Wither Armor, Necron's Armor — whose real itemIds
     * are `POWER_WITHER_*`, not `NECRON_*` — and Shadow Assassin Armor, which
     * share identical per-star costs per the wiki's `Essence_Cost` table) is
     * covered. Every other essence-costed item in the game (Spider/Undead/
     * Dragon/Gold/Diamond/Ice essence lines, and the Crimson Isle "Fuming"
     * mechanic, which isn't essence-based at all and has no sourced data yet)
     * is a known gap — [starEssenceCost] returns `null` for them, which
     * callers must render as "no data," not omit silently as zero.
     */
    private val STAR_COSTS: Map<String, StarCostTable> = mapOf(
        "POWER_WITHER_HELMET" to StarCostTable("ESSENCE_WITHER", listOf(50, 100, 200, 350, 500)),
        "POWER_WITHER_CHESTPLATE" to StarCostTable("ESSENCE_WITHER", listOf(100, 200, 350, 600, 1000)),
        "POWER_WITHER_LEGGINGS" to StarCostTable("ESSENCE_WITHER", listOf(75, 150, 250, 400, 700)),
        "POWER_WITHER_BOOTS" to StarCostTable("ESSENCE_WITHER", listOf(50, 100, 200, 350, 500)),
        "WITHER_HELMET" to StarCostTable("ESSENCE_WITHER", listOf(50, 100, 200, 350, 500)),
        "WITHER_CHESTPLATE" to StarCostTable("ESSENCE_WITHER", listOf(100, 200, 350, 600, 1000)),
        "WITHER_LEGGINGS" to StarCostTable("ESSENCE_WITHER", listOf(75, 150, 250, 400, 700)),
        "WITHER_BOOTS" to StarCostTable("ESSENCE_WITHER", listOf(50, 100, 200, 350, 500)),
        "SHADOW_ASSASSIN_HELMET" to StarCostTable("ESSENCE_WITHER", listOf(20, 50, 100, 200, 300)),
        "SHADOW_ASSASSIN_CHESTPLATE" to StarCostTable("ESSENCE_WITHER", listOf(35, 75, 150, 300, 500)),
        "SHADOW_ASSASSIN_LEGGINGS" to StarCostTable("ESSENCE_WITHER", listOf(30, 60, 120, 250, 400)),
        "SHADOW_ASSASSIN_BOOTS" to StarCostTable("ESSENCE_WITHER", listOf(20, 50, 100, 200, 300))
    )

    fun reforgeApplyFee(rarity: String?): Double? = rarity?.let { REFORGE_APPLY_FEE_BY_RARITY[it.uppercase()] }

    fun reforgeStoneItemId(reforgeKey: String): String? = REFORGE_STONE_ITEM_ID[reforgeKey.lowercase()]

    /** Essence itemId and total essence count to reach [level] stars (1-5; higher master-star tiers aren't covered), or `null` if [itemId] isn't in [STAR_COSTS]. */
    fun starEssenceCost(itemId: String, level: Int): Pair<String, Int>? {
        val table = STAR_COSTS[itemId.uppercase()] ?: return null
        val stars = level.coerceIn(0, table.perStar.size)
        if (stars == 0) return null
        return table.essenceItemId to table.perStar.take(stars).sum()
    }
}
