package dev.saibon.market.value

import dev.saibon.data.DataRepository
import dev.saibon.data.model.Recipe
import dev.saibon.data.model.RecipeType
import dev.saibon.market.AuctionItemDecoder
import dev.saibon.market.AuctionSalesHistoryRepository
import dev.saibon.market.CraftFlipRanking
import dev.saibon.market.flip.IngredientPriceResolver
import dev.saibon.market.model.ItemModifier
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/** Groups an [EstimatedValueLine] for [EstimatedValueHudModule]'s rendering — which categories get a header+subtotal+indented children vs. render as a flat top-level line. */
enum class ValueCategory {
    BASE, BASE_PART, REFORGE, BOOLEAN_UPGRADE, STARS, POTATO,
    ABILITY_SCROLL, GEM_SLOT, GEM, ENCHANTMENT, MISC
}

/** One priced component of an [EstimatedValueResult] — a label and its resolved coin cost. [checkmark] marks a fixed-price boolean upgrade (Recombobulated, Art of War, ...) for a "✓" prefix instead of a per-unit breakdown. */
data class EstimatedValueLine(val label: String, val cost: Double, val category: ValueCategory, val checkmark: Boolean = false)

/**
 * [lines] only ever contains components whose cost was actually resolved —
 * an unresolvable component is dropped and sets [isPartial], never
 * contributes a guessed/zero cost. [total] is the sum of [lines], so a
 * partial result understates the true value rather than overstating it.
 */
data class EstimatedValueResult(
    val itemName: String,
    val lines: List<EstimatedValueLine>,
    val total: Double,
    val isPartial: Boolean
)

/**
 * SkyHanni-style "Estimated Item Value": base item market price plus the
 * cost to acquire/apply every modifier actually present on this specific
 * item instance (reforge, recombobulation, dungeon stars, hot potato books,
 * enchantments, gems, enrichment, ability scrolls, ...). Deliberately
 * separate from [dev.saibon.market.CraftFlipRanking.craftCostOf], which
 * answers a different question — "what would a *plain* copy of this base
 * item cost to craft from raw recipe ingredients," used by the AH
 * flip-finder's sanity cap — this answers "what did upgrading *this exact
 * item* actually cost."
 *
 * The **base item** component itself still goes through
 * [CraftFlipRanking.craftCostOf]'s `min(buy, craft)` rule rather than a
 * flat market lookup: for a multi-stage forge item (Divan's Drill, Hyperion,
 * ...) the AH market price is often a handful of thin/inflated listings far
 * above what the item actually costs to forge from raw Bazaar ingredients
 * bottom-up, so whenever craft cost wins, [baseItemLines] breaks the item's
 * *own* top-level recipe apart into its own lines (e.g. a drill's Engine and
 * Fuel Tank each show up separately) instead of collapsing the whole forge
 * tree into one opaque "Base item" number — deeper sub-ingredients still
 * fold into each part's own resolved cost rather than exploding the tooltip.
 *
 * Every component is priced via a real market lookup
 * ([IngredientPriceResolver.costOf]: Bazaar buy-order -> Bazaar instant-buy
 * average -> AH -> NPC-floor) or a wiki-verified constant
 * ([ModifierCostTables]). Remaining coverage gaps in [ModifierCostTables]
 * (most essence types, gemstone slot unlocks outside a curated item list,
 * Undead/Gold essence, tiered Perfect Armor, Master Stars, Kuudra attributes)
 * surface as [EstimatedValueResult.isPartial], not as a wrong number.
 */
object EstimatedItemValueCalculator {
    private val recipesOf: (String) -> List<Recipe> = DataRepository::recipesFor
    private val marketCostOf: (String) -> Double? = { IngredientPriceResolver.costOf(it) }
    private val marketCostOfQuantity: (String, Double) -> Double? = IngredientPriceResolver::costOfQuantity

    fun compute(stack: ItemStack): EstimatedValueResult? {
        // On a live (component-based) ItemStack, Hypixel's fields (id, modifier, enchantments, ...)
        // sit directly at the top level of the CUSTOM_DATA tag — there's no nested "ExtraAttributes"
        // child key on this data source. That wrapper only exists in the pre-1.20.5 raw NBT format
        // AuctionItemDecoder decodes from AH item_bytes, a separate, differently-shaped data source.
        val extraAttributes = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        val itemId = extraAttributes.getString("id").orElse(null) ?: return null
        val item = DataRepository.item(itemId)

        var isPartial = false
        val lines = mutableListOf<EstimatedValueLine>()

        val (base, basePartial) = baseItemLines(itemId)
        if (base.isEmpty()) isPartial = true else lines += base
        if (basePartial) isPartial = true

        for (modifier in AuctionItemDecoder.itemModifiers(extraAttributes)) {
            if (modifier.kind == "potato") {
                val potato = potatoLines(modifier.key)
                if (potato.isEmpty()) isPartial = true else lines += potato
                continue
            }
            val priced = priceModifier(modifier, itemId, item?.tier)
            if (priced == null) isPartial = true else lines += priced
        }

        val total = lines.sumOf { it.cost }
        return EstimatedValueResult(item?.name ?: itemId, lines, total, isPartial)
    }

    /**
     * `min(buy, craft)` for the item itself, per the class doc comment. When
     * craft wins (or the item has a recipe but no market price at all), the
     * recipe's own top-level ingredients are priced and returned as separate
     * [ValueCategory.BASE_PART] lines instead of one lump sum — each part's
     * price still recurses through *its own* cheapest buy-vs-craft choice via
     * [CraftFlipRanking.craftCostOf], just not exploded further in the UI.
     *
     * Unlike [CraftFlipRanking]'s own all-or-nothing recursion (correct for
     * flip-finding, where one unpriceable ingredient should kill the whole
     * candidate rather than understate its cost), a part that can't be priced
     * at all here just drops its own line and marks the second (`isPartial`)
     * element of the returned pair `true`, instead of collapsing the entire
     * breakdown back to the finished item's flat market price and silently
     * discarding every sibling part that DID resolve. This was previously
     * happening for any multi-tier forge chain (e.g. Divan's Drill's Titanium
     * Drill DR-X655 sub-tier, or Storm's Chestplate's L.A.S.R.'s Eye/Wither
     * Chestplate parts) where one deep leaf ingredient had no market data:
     * [CraftFlipRanking.craftCostOf] returns `null` for the *whole* recipe in
     * that case, even though sibling ingredients priced just fine.
     */
    private fun baseItemLines(itemId: String): Pair<List<EstimatedValueLine>, Boolean> {
        val marketCost = marketCostOf(itemId)
        val recipe = CraftFlipRanking.cheapestRecipe(itemId, recipesOf, marketCostOf)
            ?: return (marketCost?.let { listOf(EstimatedValueLine("Base item", it, ValueCategory.BASE)) } ?: emptyList()) to false

        if (recipe.type == RecipeType.NPC) {
            val npcCost = recipe.npcCost
                ?: return (marketCost?.let { listOf(EstimatedValueLine("Base item", it, ValueCategory.BASE)) } ?: emptyList()) to false
            val cost = if (marketCost != null) minOf(marketCost, npcCost) else npcCost
            return listOf(EstimatedValueLine("Base item", cost, ValueCategory.BASE)) to false
        }

        val craftCost = CraftFlipRanking.craftCostOf(itemId, recipesOf, marketCostOf, marketCostOfQuantity)
        if (marketCost != null && (craftCost == null || marketCost <= craftCost)) {
            return listOf(EstimatedValueLine("Base item", marketCost, ValueCategory.BASE)) to false
        }
        if (craftCost == null && marketCost == null) return emptyList<EstimatedValueLine>() to true

        val resultCount = recipe.resultCount.coerceAtLeast(1)
        val partLines = mutableListOf<EstimatedValueLine>()
        var missingPart = false
        for (ingredient in recipe.ingredients) {
            val unitCost = CraftFlipRanking.craftCostOf(ingredient.itemId, recipesOf, marketCostOf, marketCostOfQuantity)
                ?: marketCostOf(ingredient.itemId)
            if (unitCost == null) {
                missingPart = true
                continue
            }
            val partName = DataRepository.item(ingredient.itemId)?.name ?: displayName(ingredient.itemId)
            val label = if (ingredient.amount > 1) "${ingredient.amount}x $partName" else partName
            partLines += EstimatedValueLine(label, unitCost * ingredient.amount / resultCount, ValueCategory.BASE_PART)
        }
        val forgeFee = if (recipe.type == RecipeType.FORGE) recipe.npcCost ?: 0.0 else 0.0
        if (forgeFee > 0) partLines += EstimatedValueLine("Forge fee", forgeFee / resultCount, ValueCategory.BASE_PART)

        if (partLines.isEmpty()) {
            return (marketCost?.let { listOf(EstimatedValueLine("Base item", it, ValueCategory.BASE)) } ?: emptyList()) to (marketCost == null)
        }
        return partLines to missingPart
    }

    private fun priceModifier(modifier: ItemModifier, itemId: String, rarity: String?): List<EstimatedValueLine>? = when (modifier.kind) {
        "reforge" -> reforgeLines(modifier.key, rarity)
        "recomb" -> marketCostOf("RECOMBOBULATOR_3000")?.let { listOf(EstimatedValueLine("Recombobulated", it, ValueCategory.BOOLEAN_UPGRADE, checkmark = true)) }
        "stars" -> starsLine(itemId, modifier.key)?.let { listOf(it) }
        "ench" -> enchantLine(modifier.key)?.let { listOf(it) }
        "gem" -> gemLine(modifier.key)?.let { listOf(it) }
        "gem_slot" -> gemSlotLine(modifier.key)?.let { listOf(it) }
        "enrich" -> marketCostOf("TALISMAN_ENRICHMENT_${modifier.key}")?.let { listOf(EstimatedValueLine("Enrichment", it, ValueCategory.MISC)) }
        "scroll" -> marketCostOf(modifier.key)?.let { listOf(EstimatedValueLine(displayName(modifier.key), it, ValueCategory.ABILITY_SCROLL)) }
        "book" -> bookLine(modifier.key)?.let { listOf(it) }
        "upgrade" -> upgradeLines(modifier.key)
        "dye" -> dyeLine(modifier.key)?.let { listOf(it) }
        "rune" -> runeLine(modifier.key)?.let { listOf(it) }
        "drill_part" -> drillPartLine(modifier.key)?.let { listOf(it) }
        // "skin": deliberately unhandled — see AuctionItemDecoder.skinModifier's doc comment.
        // Falls through here, contributing to isPartial rather than a fabricated price.
        else -> null
    }

    /** `key` is the dye's own real Bazaar/AH-native itemId directly (e.g. `"DYE_AURORA"`) — see [AuctionItemDecoder]'s `dyeModifier` doc comment. */
    private fun dyeLine(key: String): EstimatedValueLine? {
        val price = marketCostOf(key) ?: return null
        return EstimatedValueLine(DataRepository.item(key)?.name ?: displayName(key.removePrefix("DYE_")) + " Dye", price, ValueCategory.MISC)
    }

    /**
     * `key` is `"<RUNE_NAME>:<LEVEL>"`. Unlike every other modifier here, a rune has
     * no stable standalone itemId to feed [marketCostOf] — see [AuctionItemDecoder]'s
     * `runeModifiers` doc comment — so this instead reads the AH fair-price reference
     * for the synthetic `"RUNE"` item's `"rune:<NAME>:<LEVEL>"` signature bucket
     * directly, the same bucket standalone rune AH listings populate via
     * [AuctionItemDecoder.modifierSignature].
     */
    private fun runeLine(key: String): EstimatedValueLine? {
        val parts = key.split(":")
        if (parts.size != 2) return null
        val (name, level) = parts
        val price = AuctionSalesHistoryRepository.saleReference("RUNE", "rune:$name:$level")?.fairPrice?.takeIf { it > 0 } ?: return null
        return EstimatedValueLine("${displayName(name)} $level", price, ValueCategory.MISC)
    }

    /**
     * Flat rarity apply fee plus the reforge stone's own market price, if
     * this reforge required one, as two separate lines (matching the
     * reference SkyHanni layout's "Stone: ..." / "Apply cost: ..." split).
     * Three cases per [ModifierCostTables]: catalogued stone -> price it;
     * catalogued as free -> stone cost is 0 (no stone line); neither -> this
     * is a stone-based reforge the table hasn't catalogued yet, bail the
     * whole line (`null`) rather than silently treating an unknown reforge
     * as free.
     */
    private fun reforgeLines(reforgeKey: String, rarity: String?): List<EstimatedValueLine>? {
        val fee = ModifierCostTables.reforgeApplyFee(rarity) ?: return null
        val stoneItemId = ModifierCostTables.reforgeStoneItemId(reforgeKey)
        val lines = mutableListOf<EstimatedValueLine>()
        when {
            stoneItemId != null -> {
                val stoneCost = marketCostOf(stoneItemId) ?: return null
                lines += EstimatedValueLine("Stone (${DataRepository.item(stoneItemId)?.name ?: displayName(stoneItemId)})", stoneCost, ValueCategory.REFORGE)
            }
            !ModifierCostTables.isFreeReforge(reforgeKey) -> return null
        }
        lines += EstimatedValueLine("Reforge (${displayName(reforgeKey)}) apply cost", fee, ValueCategory.REFORGE)
        return lines
    }

    /** `hot_potato_count` covers both Hot Potato Books (first 10) and Fuming Potato Books (the next up to 5) in one combined NBT counter — Hypixel doesn't expose which of the count were which type, so this assumes HPBs were applied first, matching how the anvil UI orders them. Split into two lines (HPB's / Fuming) to match SkyHanni's reference tooltip layout instead of one merged line. */
    private fun potatoLines(countText: String): List<EstimatedValueLine> {
        val count = countText.toIntOrNull() ?: return emptyList()
        val hpbCount = count.coerceAtMost(10)
        val fpbCount = (count - 10).coerceAtLeast(0)
        val lines = mutableListOf<EstimatedValueLine>()
        if (hpbCount > 0) {
            val price = marketCostOf("HOT_POTATO_BOOK") ?: return emptyList()
            lines += EstimatedValueLine("HPB's ($hpbCount/10)", hpbCount * price, ValueCategory.POTATO)
        }
        if (fpbCount > 0) {
            val price = marketCostOf("FUMING_POTATO_BOOK") ?: return emptyList()
            lines += EstimatedValueLine("Fuming ($fpbCount/5)", fpbCount * price, ValueCategory.POTATO)
        }
        return lines
    }

    /** Coins plus gemstone material cost to unlock this slot — only priced for the handful of items in [ModifierCostTables.gemstoneSlotUnlockCost]'s curated coverage, `null` (isPartial) everywhere else. */
    private fun gemSlotLine(key: String): EstimatedValueLine? {
        val itemId = key.substringBeforeLast(":")
        val slotName = key.substringAfterLast(":")
        val cost = ModifierCostTables.gemstoneSlotUnlockCost(itemId, slotName) ?: return null
        val gemPrice = marketCostOf("${cost.gemQuality}_${cost.gemType}_GEM") ?: return null
        return EstimatedValueLine(displayName(slotName), cost.coins + gemPrice * cost.gemCount, ValueCategory.GEM_SLOT)
    }

    private fun starsLine(itemId: String, levelText: String): EstimatedValueLine? {
        val level = levelText.toIntOrNull() ?: return null
        val (essenceItemId, essenceCount) = ModifierCostTables.starEssenceCost(itemId, level) ?: return null
        val essencePrice = marketCostOf(essenceItemId) ?: return null
        return EstimatedValueLine("Stars ($level★, $essenceCount essence)", essencePrice * essenceCount, ValueCategory.STARS)
    }

    private val ENCHANT_KEY = Regex("^(.*?)(\\d+)$")

    /**
     * Best-effort only: tries the Bazaar's per-level enchanted-book product id directly
     * (`ENCHANTMENT_<NAME>_<LEVEL>`) since this codebase has no other source for standalone
     * enchant acquisition cost — see [ModifierCostTables]'s doc comment on why this differs
     * from the AH-sale-price-delta model [dev.saibon.market.ModifierValueModel] uses elsewhere.
     *
     * Level 5 rarely trades directly on the Bazaar (most enchants that go past 4 only ever get
     * there by combining 2 books of the previous level in an anvil), so a direct-lookup miss at
     * level 5 falls back to Coflnet's `CorrectEnchantTagAndAddLvl5` (doc §4.2): the highest
     * directly-priced level below 5, times `2^(5 - thatLevel)` copies, instead of dropping the
     * line (and setting [EstimatedValueResult.isPartial]) when it's perfectly computable from a
     * lower level's real price.
     */
    private fun enchantLine(key: String): EstimatedValueLine? {
        val match = ENCHANT_KEY.find(key) ?: return null
        val name = match.groupValues[1]
        val level = match.groupValues[2].toIntOrNull() ?: return null
        marketCostOf("ENCHANTMENT_${name.uppercase()}_$level")?.let {
            return EstimatedValueLine("${displayName(name)} $level", it, ValueCategory.ENCHANTMENT)
        }
        if (level != 5) return null
        for (baseLevel in 4 downTo 1) {
            val basePrice = marketCostOf("ENCHANTMENT_${name.uppercase()}_$baseLevel") ?: continue
            val copies = 1 shl (5 - baseLevel)
            return EstimatedValueLine("${displayName(name)} 5 (from ${copies}x lvl $baseLevel)", basePrice * copies, ValueCategory.ENCHANTMENT)
        }
        return null
    }

    /** Key is `"<slot>:<gemType>:<quality>"`, or `"<slot>:<quality>"` when the gem type wasn't tracked (see [AuctionItemDecoder]'s `gemModifiers` doc) — the latter can't be priced since the itemId needs both. */
    private fun gemLine(key: String): EstimatedValueLine? {
        val parts = key.split(":")
        if (parts.size != 3) return null
        val (slot, gemType, quality) = parts
        val price = marketCostOf("${quality.uppercase()}_${gemType.uppercase()}_GEM") ?: return null
        return EstimatedValueLine("${displayName(slot)}: ${displayName(quality)} ${displayName(gemType)}", price, ValueCategory.GEM)
    }

    private fun bookLine(key: String): EstimatedValueLine? {
        val itemId = when (key) {
            "art_of_war" -> "THE_ART_OF_WAR"
            "art_of_peace" -> "THE_ART_OF_PEACE"
            "book_of_stats" -> "BOOK_OF_STATS"
            else -> return null
        }
        val price = marketCostOf(itemId) ?: return null
        return EstimatedValueLine(displayName(key), price, ValueCategory.BOOLEAN_UPGRADE, checkmark = true)
    }

    private fun upgradeLines(key: String): List<EstimatedValueLine>? {
        if (key == "etherwarp") return etherwarpLine()?.let { listOf(it) }
        if (key == "wood_singularity") {
            val price = marketCostOf("WOOD_SINGULARITY") ?: return null
            return listOf(EstimatedValueLine("Wood Singularity", price, ValueCategory.BOOLEAN_UPGRADE, checkmark = true))
        }
        val itemId = when {
            key.startsWith("farming_for_dummies") -> "FARMING_FOR_DUMMIES"
            key.startsWith("transmission_tuner") -> "TRANSMISSION_TUNER"
            key.startsWith("booster:") -> key.substringAfter("booster:")
            else -> return null
        }
        val count = key.substringAfter(":", "1").toIntOrNull() ?: 1
        val unitPrice = marketCostOf(itemId) ?: return null
        return listOf(EstimatedValueLine(displayName(itemId), unitPrice * count, ValueCategory.MISC))
    }

    /**
     * Etherwarp Conduit's recipe is already in `data/recipes.json`
     * (24 Null Ovoid + 16 Refined Titanium, recursively priceable), so this
     * reuses [CraftFlipRanking.craftCostOf] the same way
     * [dev.saibon.market.flip.CraftVsBinFinder] does rather than duplicating
     * the recipe by hand. The Etherwarp Merger has no recipe (Voidgloom
     * Seraph T4 drop only) — [marketCostOf] still resolves it if it has AH
     * sales history, `null` (bailing this whole line) otherwise, since a
     * fully illiquid item contributing a guessed cost would be worse than
     * omitting it.
     */
    private fun etherwarpLine(): EstimatedValueLine? {
        val conduitCost = CraftFlipRanking.craftCostOf("ETHERWARP_CONDUIT", recipesOf, marketCostOf, marketCostOfQuantity) ?: return null
        val mergerCost = marketCostOf("ETHERWARP_MERGER") ?: return null
        return EstimatedValueLine("Etherwarp", conduitCost + mergerCost, ValueCategory.BOOLEAN_UPGRADE, checkmark = true)
    }

    /**
     * Key is `"<slot>:<itemId>"` — see [AuctionItemDecoder]'s `drillPartModifiers`
     * doc comment. The part's own item name (e.g. "Fuel Canister", "Drill Motor")
     * often doesn't say which socket it fills, so the line is prefixed with the
     * slot label (matching [reforgeLines]'s "Stone (...)" convention) rather than
     * showing the bare item name alone.
     */
    private fun drillPartLine(key: String): EstimatedValueLine? {
        val parts = key.split(":", limit = 2)
        if (parts.size != 2) return null
        val (slot, itemId) = parts
        val price = marketCostOf(itemId) ?: return null
        val slotLabel = displayName(slot)
        val partName = DataRepository.item(itemId)?.name ?: displayName(itemId)
        return EstimatedValueLine("$slotLabel ($partName)", price, ValueCategory.MISC)
    }

    private fun displayName(key: String): String = key.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
