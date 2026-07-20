package dev.saibon.market.value

import dev.saibon.data.DataRepository
import dev.saibon.market.AuctionItemDecoder
import dev.saibon.market.CraftFlipRanking
import dev.saibon.market.flip.IngredientPriceResolver
import dev.saibon.market.model.ItemModifier
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/** One priced component of an [EstimatedValueResult] — a label and its resolved coin cost. */
data class EstimatedValueLine(val label: String, val cost: Double)

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
 * Every component is priced via a real market lookup
 * ([IngredientPriceResolver.costOf]: Bazaar buy-order -> Bazaar instant-buy
 * average -> AH -> NPC-floor) or a wiki-verified constant
 * ([ModifierCostTables]). Remaining coverage gaps in [ModifierCostTables]
 * (most essence types, gemstone slot unlocks outside a curated item list,
 * Undead/Gold essence, tiered Perfect Armor, Master Stars, Kuudra attributes)
 * surface as [EstimatedValueResult.isPartial], not as a wrong number.
 */
object EstimatedItemValueCalculator {
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

        val baseCost = IngredientPriceResolver.costOf(itemId)
        if (baseCost != null) lines += EstimatedValueLine("Base item", baseCost) else isPartial = true

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

    private fun priceModifier(modifier: ItemModifier, itemId: String, rarity: String?): EstimatedValueLine? = when (modifier.kind) {
        "reforge" -> reforgeLine(modifier.key, rarity)
        "recomb" -> IngredientPriceResolver.costOf("RECOMBOBULATOR_3000")?.let { EstimatedValueLine("Recombobulated", it) }
        "stars" -> starsLine(itemId, modifier.key)
        "ench" -> enchantLine(modifier.key)
        "gem" -> gemLine(modifier.key)
        "gem_slot" -> gemSlotLine(modifier.key)
        "enrich" -> IngredientPriceResolver.costOf("TALISMAN_ENRICHMENT_${modifier.key}")?.let { EstimatedValueLine("Enrichment", it) }
        "scroll" -> IngredientPriceResolver.costOf(modifier.key)?.let { EstimatedValueLine(displayName(modifier.key), it) }
        "book" -> bookLine(modifier.key)
        "upgrade" -> upgradeLine(modifier.key)
        else -> null
    }

    /**
     * Flat rarity apply fee plus the reforge stone's own market price, if
     * this reforge required one. Three cases per [ModifierCostTables]:
     * catalogued stone -> price it; catalogued as free -> stone cost is 0;
     * neither -> this is a stone-based reforge the table hasn't catalogued
     * yet, bail the whole line (`null`) rather than silently treating an
     * unknown reforge as free.
     */
    private fun reforgeLine(reforgeKey: String, rarity: String?): EstimatedValueLine? {
        val fee = ModifierCostTables.reforgeApplyFee(rarity) ?: return null
        val stoneItemId = ModifierCostTables.reforgeStoneItemId(reforgeKey)
        val stoneCost = when {
            stoneItemId != null -> IngredientPriceResolver.costOf(stoneItemId) ?: return null
            ModifierCostTables.isFreeReforge(reforgeKey) -> 0.0
            else -> return null
        }
        return EstimatedValueLine("Reforge (${displayName(reforgeKey)})", fee + stoneCost)
    }

    /** `hot_potato_count` covers both Hot Potato Books (first 10) and Fuming Potato Books (the next up to 5) in one combined NBT counter — Hypixel doesn't expose which of the count were which type, so this assumes HPBs were applied first, matching how the anvil UI orders them. Split into two lines (HPB's / Fuming) to match SkyHanni's reference tooltip layout instead of one merged line. */
    private fun potatoLines(countText: String): List<EstimatedValueLine> {
        val count = countText.toIntOrNull() ?: return emptyList()
        val hpbCount = count.coerceAtMost(10)
        val fpbCount = (count - 10).coerceAtLeast(0)
        val lines = mutableListOf<EstimatedValueLine>()
        if (hpbCount > 0) {
            val price = IngredientPriceResolver.costOf("HOT_POTATO_BOOK") ?: return emptyList()
            lines += EstimatedValueLine("HPB's ($hpbCount/10)", hpbCount * price)
        }
        if (fpbCount > 0) {
            val price = IngredientPriceResolver.costOf("FUMING_POTATO_BOOK") ?: return emptyList()
            lines += EstimatedValueLine("Fuming ($fpbCount/5)", fpbCount * price)
        }
        return lines
    }

    /** Coins plus gemstone material cost to unlock this slot — only priced for the handful of items in [ModifierCostTables.gemstoneSlotUnlockCost]'s curated coverage, `null` (isPartial) everywhere else. */
    private fun gemSlotLine(key: String): EstimatedValueLine? {
        val itemId = key.substringBeforeLast(":")
        val slotName = key.substringAfterLast(":")
        val cost = ModifierCostTables.gemstoneSlotUnlockCost(itemId, slotName) ?: return null
        val gemPrice = IngredientPriceResolver.costOf("${cost.gemQuality}_${cost.gemType}_GEM") ?: return null
        return EstimatedValueLine("Gem slot unlock (${displayName(slotName)})", cost.coins + gemPrice * cost.gemCount)
    }

    private fun starsLine(itemId: String, levelText: String): EstimatedValueLine? {
        val level = levelText.toIntOrNull() ?: return null
        val (essenceItemId, essenceCount) = ModifierCostTables.starEssenceCost(itemId, level) ?: return null
        val essencePrice = IngredientPriceResolver.costOf(essenceItemId) ?: return null
        return EstimatedValueLine("Stars ($level★, $essenceCount essence)", essencePrice * essenceCount)
    }

    private val ENCHANT_KEY = Regex("^(.*?)(\\d+)$")

    /** Best-effort only: tries the Bazaar's per-level enchanted-book product id directly (`ENCHANTMENT_<NAME>_<LEVEL>`) since this codebase has no other source for standalone enchant acquisition cost — see [ModifierCostTables]'s doc comment on why this differs from the AH-sale-price-delta model [dev.saibon.market.ModifierValueModel] uses elsewhere. */
    private fun enchantLine(key: String): EstimatedValueLine? {
        val match = ENCHANT_KEY.find(key) ?: return null
        val name = match.groupValues[1]
        val level = match.groupValues[2]
        val price = IngredientPriceResolver.costOf("ENCHANTMENT_${name.uppercase()}_$level") ?: return null
        return EstimatedValueLine("${displayName(name)} $level", price)
    }

    /** Key is `"<slot>:<gemType>:<quality>"`, or `"<slot>:<quality>"` when the gem type wasn't tracked (see [AuctionItemDecoder]'s `gemModifiers` doc) — the latter can't be priced since the itemId needs both. */
    private fun gemLine(key: String): EstimatedValueLine? {
        val parts = key.split(":")
        if (parts.size != 3) return null
        val (slot, gemType, quality) = parts
        val price = IngredientPriceResolver.costOf("${quality.uppercase()}_${gemType.uppercase()}_GEM") ?: return null
        return EstimatedValueLine("Gem (${displayName(slot)}: ${displayName(quality)} ${displayName(gemType)})", price)
    }

    private fun bookLine(key: String): EstimatedValueLine? {
        val itemId = when (key) {
            "art_of_war" -> "THE_ART_OF_WAR"
            "art_of_peace" -> "THE_ART_OF_PEACE"
            "book_of_stats" -> "BOOK_OF_STATS"
            else -> return null
        }
        val price = IngredientPriceResolver.costOf(itemId) ?: return null
        return EstimatedValueLine(displayName(key), price)
    }

    private fun upgradeLine(key: String): EstimatedValueLine? {
        if (key == "etherwarp") return etherwarpLine()
        val itemId = when {
            key == "wood_singularity" -> "WOOD_SINGULARITY"
            key.startsWith("farming_for_dummies") -> "FARMING_FOR_DUMMIES"
            key.startsWith("transmission_tuner") -> "TRANSMISSION_TUNER"
            key.startsWith("booster:") -> key.substringAfter("booster:")
            else -> return null
        }
        val count = key.substringAfter(":", "1").toIntOrNull() ?: 1
        val unitPrice = IngredientPriceResolver.costOf(itemId) ?: return null
        return EstimatedValueLine(displayName(itemId), unitPrice * count)
    }

    /**
     * Etherwarp Conduit's recipe is already in `data/recipes.json`
     * (24 Null Ovoid + 16 Refined Titanium, recursively priceable), so this
     * reuses [CraftFlipRanking.craftCostOf] the same way
     * [dev.saibon.market.flip.CraftVsBinFinder] does rather than duplicating
     * the recipe by hand. The Etherwarp Merger has no recipe (Voidgloom
     * Seraph T4 drop only) — [IngredientPriceResolver.costOf] still resolves
     * it if it has AH sales history, `null` (bailing this whole line)
     * otherwise, since a fully illiquid item contributing a guessed cost
     * would be worse than omitting it.
     */
    private fun etherwarpLine(): EstimatedValueLine? {
        val conduitCost = CraftFlipRanking.craftCostOf(
            "ETHERWARP_CONDUIT",
            recipeOf = { DataRepository.recipesFor(it).firstOrNull() },
            marketCostOf = { id -> IngredientPriceResolver.costOf(id) }
        ) ?: return null
        val mergerCost = IngredientPriceResolver.costOf("ETHERWARP_MERGER") ?: return null
        return EstimatedValueLine("Etherwarp", conduitCost + mergerCost)
    }

    private fun displayName(key: String): String = key.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
