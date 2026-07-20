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
     * material cost, see [FREE_REFORGE_KEYS]) correctly have no entry here,
     * since their entire cost *is* [REFORGE_APPLY_FEE_BY_RARITY]. Seeded from
     * `Reforge_Stones/Armor`, `/Sword`, `/Bow`, `/Mining_Tool`,
     * `/Fishing_Rod`, `/Equipment`, `/Vacuum`, and `/Axe_Hoe`. A handful of
     * lower-confidence names from that research (apostrophe-containing keys
     * like "Jerry's", and "Dirty"/`DIRT_BOTTLE` whose wikitext only had an
     * unconfirmed lowercase `id=` field) were deliberately left out rather
     * than guessed — see [reforgeLine]'s doc comment for what happens to a
     * reforge key that's genuinely missing from both this map and
     * [FREE_REFORGE_KEYS].
     */
    private val REFORGE_STONE_ITEM_ID: Map<String, String> = mapOf(
        // Armor
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
        "groovy" to "MANGROVE_GEM",
        // Sword / fishing rod
        "fabled" to "DRAGON_CLAW",
        "suspicious" to "SUSPICIOUS_VIAL",
        "warped" to "AOTE_STONE",
        "withered" to "WITHER_BLOOD",
        "bulky" to "BULKY_STONE",
        "coldfused" to "ENTROPY_SUPPRESSOR",
        "fanged" to "FULL_JAW_FANGING_KIT",
        "gilded" to "MIDAS_JEWEL",
        // Bow
        "headstrong" to "SALMON_OPAL",
        "precise" to "OPTICAL_LENS",
        "spiritual" to "SPIRIT_DECOY",
        // Mining tool (pickaxe/drill)
        "fruitful" to "ONYX",
        "magnetic" to "LAPIS_CRYSTAL",
        "fleet" to "DIAMONITE",
        "mithraic" to "PURE_MITHRIL",
        "auspicious" to "ROCK_GEMSTONE",
        "refined" to "REFINED_AMBER",
        "stellar" to "PETRIFIED_STARFALL",
        "heated" to "HOT_STUFF",
        "ambered" to "AMBER_MATERIAL",
        "lustrous" to "GLEAMING_CRYSTAL",
        "glacial" to "FRIGID_HUSK",
        // Fishing rod
        "salty" to "SALT_CUBE",
        "treacherous" to "RUSTY_ANCHOR",
        "stiff" to "HARDENED_WOOD",
        "pitchin" to "PITCHIN_KOI",
        "lucky" to "LUCKY_DICE",
        "chomp" to "KUUDRA_MANDIBLE",
        // Equipment (belt/cloak/necklace/gloves/bracelet)
        "waxed" to "BLAZE_WAX",
        "fortified" to "METEOR_SHARD",
        "strengthened" to "SEARING_STONE",
        "glistening" to "SHINY_PRISM",
        "blooming" to "FLOWERING_BOUQUET",
        "rooted" to "BURROWING_SPORES",
        "royal" to "DWARVEN_TREASURE",
        "blazing" to "BLAZEN_SPHERE",
        "bloodsoaked" to "PRESUMED_GALLON_OF_RED_PAINT",
        // Vacuum (Garden pest-catcher) / Axe & Hoe (foraging/farming tool)
        "beady" to "BEADY_EYES",
        "buzzing" to "CLIPPED_WINGS",
        "moil" to "MOIL_LOG",
        "blessed" to "BLESSED_FRUIT",
        "toil" to "TOIL_LOG",
        "bountiful" to "GOLDEN_BALL",
        "earthy" to "LARGE_WALNUT",
        "moonglade" to "MOONGLADE_JEWEL"
    )

    /**
     * Reforge internal keys that are chosen directly at the Reforge
     * Anvil/Blacksmith for free (no stone, just [REFORGE_APPLY_FEE_BY_RARITY]) —
     * covers Armor, Sword & Fishing Rod, Bow, Tool (Pickaxe/Drill/Axe/Hoe),
     * and Equipment's free-reforge lists per the wiki's "Reforging" family of
     * category subpages. Used
     * by [reforgeLine] to tell "genuinely free" apart from "a stone-based
     * reforge this table hasn't catalogued yet" — see its doc comment.
     */
    private val FREE_REFORGE_KEYS: Set<String> = setOf(
        // Armor
        "clean", "fierce", "heavy", "light", "mythic", "pure", "smart", "titanic", "wise",
        // Sword & fishing rod
        "gentle", "odd", "fast", "fair", "epic", "sharp", "heroic", "spicy", "legendary",
        // Bow
        "deadly", "fine", "grand", "hasty", "neat", "rapid", "unreal", "awkward", "rich",
        // Mining tool (pickaxe/drill)
        "unyielding", "prospectors", "excellent", "sturdy", "fortunate",
        // Axe (foraging)
        "doublebit", "lumberjacks", "great", "rugged", "lush",
        // Hoe (farming)
        "greenthumb", "peasants", "robust", "zooming",
        // Equipment
        "stained", "menacing", "hefty", "soft", "honored", "blended", "astute", "colossal", "brilliant"
    )

    /** Essence itemId (Bazaar-tradeable, resolved live via [dev.saibon.market.flip.IngredientPriceResolver]) plus the essence count required for each of stars 1 through 5, in order — cost to reach a given star is the running sum up to that index. */
    private data class StarCostTable(val essenceItemId: String, val perStar: List<Int>)

    /**
     * Dungeon-star essence cost by itemId. Only the Wither-essence "Wither
     * Armor lineage" (base Wither Armor, Necron's Armor — whose real itemIds
     * are `POWER_WITHER_*`, not `NECRON_*` — and Shadow Assassin Armor, which
     * share identical per-star costs per the wiki's `Essence_Cost` table) is
     * covered here, plus Spider (Tarantula Armor), Dragon (the 6 identical-
     * cost elemental tiers plus Superior — Holy and the Superior Chestplate
     * are excluded, see below), and Diamond (Hardened Diamond Armor) and Ice
     * (Frozen Blaze Armor, minus Boots) essence, per the wiki's
     * `Essence_Cost` page. Two specific rows were deliberately left out
     * rather than shipped as likely-wrong data: `SUPERIOR_DRAGON_CHESTPLATE`
     * and `FROZEN_BLAZE_BOOTS` both have a non-monotonic star-cost array in
     * the wiki source (star 1 costing *more* than star 2), reproduced
     * identically across two independent fetches — almost certainly a wiki
     * transcription error, but not something to guess a fix for. Gold
     * essence has no armor set at all (weapons/cosmetics only, out of scope
     * for this table); Undead essence has no single flagship set (spread
     * thinly and unverified across many mob-drop lines); Perfect Armor
     * (Diamond essence) uses a tiered itemId (`PERFECT_HELMET_<n>`) that
     * doesn't fit this table's flat-key shape. All of these, plus the
     * Crimson Isle "Fuming" mechanic (which doesn't actually exist as a
     * distinct mechanic — what looks like it in a SkyHanni tooltip is
     * Fuming Potato Books, already priced by
     * [dev.saibon.market.value.EstimatedItemValueCalculator.potatoLine]),
     * are known gaps — [starEssenceCost] returns `null` for them, which
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
        "SHADOW_ASSASSIN_BOOTS" to StarCostTable("ESSENCE_WITHER", listOf(20, 50, 100, 200, 300)),
        // Spider essence — Tarantula Armor
        "TARANTULA_HELMET" to StarCostTable("ESSENCE_SPIDER", listOf(15, 25, 35, 45, 65)),
        "TARANTULA_CHESTPLATE" to StarCostTable("ESSENCE_SPIDER", listOf(15, 25, 35, 45, 65)),
        "TARANTULA_LEGGINGS" to StarCostTable("ESSENCE_SPIDER", listOf(10, 15, 20, 25, 30)),
        "TARANTULA_BOOTS" to StarCostTable("ESSENCE_SPIDER", listOf(10, 15, 20, 25, 30)),
        // Dragon essence — the 6 elemental Dragon Armor tiers share identical per-slot costs
        "YOUNG_DRAGON_HELMET" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 50, 100, 150)),
        "YOUNG_DRAGON_CHESTPLATE" to StarCostTable("ESSENCE_DRAGON", listOf(30, 50, 80, 120, 180)),
        "YOUNG_DRAGON_LEGGINGS" to StarCostTable("ESSENCE_DRAGON", listOf(25, 40, 65, 110, 160)),
        "YOUNG_DRAGON_BOOTS" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 40, 90, 140)),
        "OLD_DRAGON_HELMET" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 50, 100, 150)),
        "OLD_DRAGON_CHESTPLATE" to StarCostTable("ESSENCE_DRAGON", listOf(30, 50, 80, 120, 180)),
        "OLD_DRAGON_LEGGINGS" to StarCostTable("ESSENCE_DRAGON", listOf(25, 40, 65, 110, 160)),
        "OLD_DRAGON_BOOTS" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 40, 90, 140)),
        "STRONG_DRAGON_HELMET" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 50, 100, 150)),
        "STRONG_DRAGON_CHESTPLATE" to StarCostTable("ESSENCE_DRAGON", listOf(30, 50, 80, 120, 180)),
        "STRONG_DRAGON_LEGGINGS" to StarCostTable("ESSENCE_DRAGON", listOf(25, 40, 65, 110, 160)),
        "STRONG_DRAGON_BOOTS" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 40, 90, 140)),
        "PROTECTOR_DRAGON_HELMET" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 50, 100, 150)),
        "PROTECTOR_DRAGON_CHESTPLATE" to StarCostTable("ESSENCE_DRAGON", listOf(30, 50, 80, 120, 180)),
        "PROTECTOR_DRAGON_LEGGINGS" to StarCostTable("ESSENCE_DRAGON", listOf(25, 40, 65, 110, 160)),
        "PROTECTOR_DRAGON_BOOTS" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 40, 90, 140)),
        "WISE_DRAGON_HELMET" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 50, 100, 150)),
        "WISE_DRAGON_CHESTPLATE" to StarCostTable("ESSENCE_DRAGON", listOf(30, 50, 80, 120, 180)),
        "WISE_DRAGON_LEGGINGS" to StarCostTable("ESSENCE_DRAGON", listOf(25, 40, 65, 110, 160)),
        "WISE_DRAGON_BOOTS" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 40, 90, 140)),
        "UNSTABLE_DRAGON_HELMET" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 50, 100, 150)),
        "UNSTABLE_DRAGON_CHESTPLATE" to StarCostTable("ESSENCE_DRAGON", listOf(30, 50, 80, 120, 180)),
        "UNSTABLE_DRAGON_LEGGINGS" to StarCostTable("ESSENCE_DRAGON", listOf(25, 40, 65, 110, 160)),
        "UNSTABLE_DRAGON_BOOTS" to StarCostTable("ESSENCE_DRAGON", listOf(20, 30, 40, 90, 140)),
        // Superior Dragon Armor — Chestplate omitted, see class doc comment
        "SUPERIOR_DRAGON_HELMET" to StarCostTable("ESSENCE_DRAGON", listOf(40, 60, 100, 200, 300)),
        "SUPERIOR_DRAGON_LEGGINGS" to StarCostTable("ESSENCE_DRAGON", listOf(50, 80, 130, 220, 320)),
        "SUPERIOR_DRAGON_BOOTS" to StarCostTable("ESSENCE_DRAGON", listOf(40, 60, 80, 180, 280)),
        // Diamond essence — Hardened Diamond Armor
        "HARDENED_DIAMOND_HELMET" to StarCostTable("ESSENCE_DIAMOND", listOf(5, 10, 15, 20, 25)),
        "HARDENED_DIAMOND_CHESTPLATE" to StarCostTable("ESSENCE_DIAMOND", listOf(5, 10, 15, 20, 25)),
        "HARDENED_DIAMOND_LEGGINGS" to StarCostTable("ESSENCE_DIAMOND", listOf(5, 10, 15, 20, 25)),
        "HARDENED_DIAMOND_BOOTS" to StarCostTable("ESSENCE_DIAMOND", listOf(5, 10, 15, 20, 25)),
        // Ice essence — Frozen Blaze Armor — Boots omitted, see class doc comment
        "FROZEN_BLAZE_HELMET" to StarCostTable("ESSENCE_ICE", listOf(10, 15, 25, 35, 60)),
        "FROZEN_BLAZE_CHESTPLATE" to StarCostTable("ESSENCE_ICE", listOf(20, 25, 35, 45, 75)),
        "FROZEN_BLAZE_LEGGINGS" to StarCostTable("ESSENCE_ICE", listOf(15, 20, 30, 40, 65))
    )

    /**
     * Per-item-per-slot gemstone slot unlock cost (coins plus a gemstone
     * material cost), keyed by `"<itemId>:<slotName>"` matching
     * [dev.saibon.market.AuctionItemDecoder]'s `gem_slot` modifier key.
     * Costs are **not** a formula of rarity or slot index — Hyperion and
     * Livid Dagger are both Legendary but cost roughly 5-10x apart — so this
     * only covers the handful of high-value items actually verified against
     * their own wiki infobox (`gemstone_slots` field) during planning.
     * Everything else is a genuine gap: [gemstoneSlotUnlockCost] returns
     * `null`, which must render as "no data," not zero.
     */
    data class GemstoneSlotCost(val coins: Double, val gemQuality: String, val gemType: String, val gemCount: Int)

    private val GEMSTONE_SLOT_UNLOCK_COST: Map<String, GemstoneSlotCost> = mapOf(
        "HYPERION:SAPPHIRE_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "SAPPHIRE", 4),
        "HYPERION:COMBAT_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 1),
        // Necron's Armor's real itemIds are POWER_WITHER_* (see STAR_COSTS' doc comment), not NECRON_*
        "POWER_WITHER_HELMET:JASPER_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 2),
        "POWER_WITHER_HELMET:COMBAT_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 1),
        "POWER_WITHER_CHESTPLATE:JASPER_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 2),
        "POWER_WITHER_CHESTPLATE:COMBAT_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 1),
        "POWER_WITHER_LEGGINGS:JASPER_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 2),
        "POWER_WITHER_LEGGINGS:COMBAT_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 1),
        "POWER_WITHER_BOOTS:JASPER_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 2),
        "POWER_WITHER_BOOTS:COMBAT_0" to GemstoneSlotCost(250_000.0, "FLAWLESS", "JASPER", 1),
        "MIDAS_SWORD:JASPER_0" to GemstoneSlotCost(50_000.0, "FINE", "JASPER", 20),
        "SHADOW_FURY:JASPER_0" to GemstoneSlotCost(100_000.0, "FINE", "JASPER", 40),
        "LIVID_DAGGER:JASPER_0" to GemstoneSlotCost(50_000.0, "FINE", "JASPER", 20)
    )

    fun reforgeApplyFee(rarity: String?): Double? = rarity?.let { REFORGE_APPLY_FEE_BY_RARITY[it.uppercase()] }

    fun reforgeStoneItemId(reforgeKey: String): String? = REFORGE_STONE_ITEM_ID[reforgeKey.lowercase()]

    fun isFreeReforge(reforgeKey: String): Boolean = reforgeKey.lowercase() in FREE_REFORGE_KEYS

    /** Essence itemId and total essence count to reach [level] stars (1-5; higher master-star tiers aren't covered), or `null` if [itemId] isn't in [STAR_COSTS]. */
    fun starEssenceCost(itemId: String, level: Int): Pair<String, Int>? {
        val table = STAR_COSTS[itemId.uppercase()] ?: return null
        val stars = level.coerceIn(0, table.perStar.size)
        if (stars == 0) return null
        return table.essenceItemId to table.perStar.take(stars).sum()
    }

    /** Coins-plus-gemstone-material cost to unlock `"<itemId>:<slotName>"`, or `null` if this specific item/slot isn't in [GEMSTONE_SLOT_UNLOCK_COST]. */
    fun gemstoneSlotUnlockCost(itemId: String, slotName: String): GemstoneSlotCost? =
        GEMSTONE_SLOT_UNLOCK_COST["${itemId.uppercase()}:${slotName.uppercase()}"]
}
