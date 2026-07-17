package dev.saibon.market.value

import dev.saibon.data.DataRepository
import dev.saibon.market.AuctionItemDecoder
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
 * ([IngredientPriceResolver.costOf]: Bazaar -> AH -> NPC-floor) or a
 * wiki-verified constant ([ModifierCostTables]). Coverage gaps in
 * [ModifierCostTables] (Crimson Isle "Fuming," most reforge stones, most
 * essence types) surface as [EstimatedValueResult.isPartial], not as a wrong
 * number.
 */
object EstimatedItemValueCalculator {
    fun compute(stack: ItemStack): EstimatedValueResult? {
        val root = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        val extraAttributes = root.getCompoundOrEmpty("ExtraAttributes")
        val itemId = extraAttributes.getString("id").orElse(null) ?: return null
        val item = DataRepository.item(itemId)

        var isPartial = false
        val lines = mutableListOf<EstimatedValueLine>()

        val baseCost = IngredientPriceResolver.costOf(itemId)
        if (baseCost != null) lines += EstimatedValueLine("Base item", baseCost) else isPartial = true

        for (modifier in AuctionItemDecoder.itemModifiers(extraAttributes)) {
            val priced = priceModifier(modifier, itemId, item?.tier)
            if (priced == null) isPartial = true else lines += priced
        }

        val total = lines.sumOf { it.cost }
        return EstimatedValueResult(item?.name ?: itemId, lines, total, isPartial)
    }

    private fun priceModifier(modifier: ItemModifier, itemId: String, rarity: String?): EstimatedValueLine? = when (modifier.kind) {
        "reforge" -> reforgeLine(modifier.key, rarity)
        "potato" -> potatoLine(modifier.key)
        "recomb" -> IngredientPriceResolver.costOf("RECOMBOBULATOR_3000")?.let { EstimatedValueLine("Recombobulated", it) }
        "stars" -> starsLine(itemId, modifier.key)
        "ench" -> enchantLine(modifier.key)
        "gem" -> gemLine(modifier.key)
        "enrich" -> IngredientPriceResolver.costOf("TALISMAN_ENRICHMENT_${modifier.key}")?.let { EstimatedValueLine("Enrichment", it) }
        "scroll" -> IngredientPriceResolver.costOf(modifier.key)?.let { EstimatedValueLine(displayName(modifier.key), it) }
        "book" -> bookLine(modifier.key)
        "upgrade" -> upgradeLine(modifier.key)
        else -> null
    }

    /** Flat rarity apply fee plus the reforge stone's own market price, if this reforge required one — a stone that's known-required but unpriced (no market data yet) bails the whole line rather than treating it as free. */
    private fun reforgeLine(reforgeKey: String, rarity: String?): EstimatedValueLine? {
        val fee = ModifierCostTables.reforgeApplyFee(rarity) ?: return null
        val stoneItemId = ModifierCostTables.reforgeStoneItemId(reforgeKey)
        val stoneCost = if (stoneItemId == null) 0.0 else IngredientPriceResolver.costOf(stoneItemId) ?: return null
        return EstimatedValueLine("Reforge (${displayName(reforgeKey)})", fee + stoneCost)
    }

    /** `hot_potato_count` covers both Hot Potato Books (first 10) and Fuming Potato Books (the next up to 5) in one combined NBT counter — Hypixel doesn't expose which of the count were which type, so this assumes HPBs were applied first, matching how the anvil UI orders them. */
    private fun potatoLine(countText: String): EstimatedValueLine? {
        val count = countText.toIntOrNull() ?: return null
        val hpbCount = count.coerceAtMost(10)
        val fpbCount = (count - 10).coerceAtLeast(0)
        val hpbPrice = if (hpbCount > 0) IngredientPriceResolver.costOf("HOT_POTATO_BOOK") ?: return null else 0.0
        val fpbPrice = if (fpbCount > 0) IngredientPriceResolver.costOf("FUMING_POTATO_BOOK") ?: return null else 0.0
        return EstimatedValueLine("Hot Potato Books ($count)", hpbCount * hpbPrice + fpbCount * fpbPrice)
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
            else -> return null
        }
        val price = IngredientPriceResolver.costOf(itemId) ?: return null
        return EstimatedValueLine(displayName(key), price)
    }

    private fun upgradeLine(key: String): EstimatedValueLine? {
        val itemId = when {
            key == "wood_singularity" -> "WOOD_SINGULARITY"
            key.startsWith("farming_for_dummies") -> "FARMING_FOR_DUMMIES"
            else -> return null
        }
        val count = key.substringAfter(":", "1").toIntOrNull() ?: 1
        val unitPrice = IngredientPriceResolver.costOf(itemId) ?: return null
        return EstimatedValueLine(displayName(itemId), unitPrice * count)
    }

    private fun displayName(key: String): String = key.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
