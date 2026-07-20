package dev.saibon.market

import dev.saibon.data.model.Recipe
import dev.saibon.data.model.RecipeType
import dev.saibon.data.model.SkyblockItem
import kotlin.math.ceil

/**
 * "Craft it cheaper than it sells for" flip ranking. Pure/parameter-injected
 * like [BazaarFlipRanking]/[AuctionFlipRanking] — the data repo's `recipes`
 * dataset lives behind [dev.saibon.data.DataRepository], a client-only
 * object, so recipe/cost lookups are passed in rather than called directly.
 */
data class CraftFlip(
    val item: SkyblockItem,
    val craftCost: Double,
    val sellPrice: Double,
    val profit: Double,
    val profitPercent: Double,
    /** Coflnet `ForgeCraftService`'s `(profit / max(duration + 100, 300) * 3600)`, clamped at 0 — only set for `RecipeType.FORGE` items with a known `durationSeconds`, `null` otherwise (including every non-forge item). Lets a marginal-but-fast forge stand apart from an equally-marginal-but-12-hour one, which raw [profit]/[profitPercent] alone can't. */
    val profitPerHour: Double? = null
)

object CraftFlipRanking {
    /** Bounds recursion through multi-tier forge/craft chains against cyclical or malformed recipe data. */
    private const val MAX_DEPTH = 6

    /**
     * Per-craft-step "effort" cost, applied once per [resolve] call — Coflnet
     * `RealisticCraft`'s `CraftStepMarkup`/`CraftStepFlatCoins` (doc §1.3):
     * a multiplicative +1% plus a flat +1 coin, so a craft chain that's only
     * marginally/noisily cheaper than buying doesn't get treated as free, and
     * cost compounds naturally with recursion depth without a separate
     * per-depth multiplier.
     */
    private const val CRAFT_STEP_MARKUP = 1.01
    private const val CRAFT_STEP_FLAT_COINS = 1.0

    /** Craft must beat buy by at least this factor to be preferred — Coflnet's `CraftPreferenceMargin` (doc §1.3), so crafting only wins when meaningfully cheaper, not by a fraction of a coin. */
    private const val CRAFT_PREFERENCE_MARGIN = 1.02

    /** Default quantity-naive fallback for [marketCostOfQuantity] parameters: flat unit price times quantity, i.e. today's pre-tranche behavior, unchanged for every caller that only has a per-unit lookup (display/tooltip contexts). */
    private fun flatQuantityCost(marketCostOf: (String) -> Double?): (String, Double) -> Double? =
        { id, qty -> marketCostOf(id)?.let { it * qty } }

    fun bestFlips(
        items: Collection<SkyblockItem>,
        recipesOf: (itemId: String) -> List<Recipe>,
        marketCostOf: (itemId: String) -> Double?,
        sellPriceOf: (item: SkyblockItem) -> Double?,
        minMarginPercent: Double,
        marketCostOfQuantity: (itemId: String, quantity: Double) -> Double? = flatQuantityCost(marketCostOf)
    ): List<CraftFlip> = items.mapNotNull { item ->
        if (recipesOf(item.id).isEmpty()) return@mapNotNull null // craft flips only apply to items with an actual recipe, not any item with a market price
        val cost = craftCostOf(item.id, recipesOf, marketCostOf, marketCostOfQuantity) ?: return@mapNotNull null
        val sell = sellPriceOf(item) ?: return@mapNotNull null
        if (cost <= 0) return@mapNotNull null
        val profit = sell - cost
        val percent = profit / cost * 100.0
        if (percent < minMarginPercent) return@mapNotNull null
        val profitPerHour = cheapestRecipe(item.id, recipesOf, marketCostOf)
            ?.takeIf { it.type == RecipeType.FORGE }
            ?.durationSeconds
            ?.let { duration -> (profit / maxOf(duration + 100, 300) * 3600.0).coerceAtLeast(0.0) }
        CraftFlip(item, cost, sell, profit, percent, profitPerHour)
    }.sortedByDescending { it.profit }

    /**
     * Per-unit cost to *acquire* [itemId] — the cheaper of buying it directly
     * or crafting it from its recipe's ingredients (each priced the same
     * way, recursively), subject to [CRAFT_PREFERENCE_MARGIN] (doc §1.3's
     * `min(buy, craft)` rule, refined: crafting isn't always assumed
     * optimal, an oversupplied ingredient can make buying the finished item
     * outright cheaper, and a craft that's only trivially cheaper shouldn't
     * win over a simple buy). When [itemId] has more than one candidate
     * recipe (doc §1.2 — e.g. `ENCHANTED_GOLD` from ingots or from a block),
     * every candidate is priced and the cheapest per-unit result is used.
     *
     * [marketCostOfQuantity], when supplied (doc §1.1's "smart buyer"), is
     * asked for the *total* cost of however many units a recipe step
     * actually needs — e.g. [dev.saibon.market.flip.IngredientPriceResolver.costOfQuantity],
     * which walks real Bazaar order-book depth and caps NPC-shop tranches at
     * their real ~640-unit stock — instead of assuming a flat per-unit price
     * however large the quantity gets. Defaults to exactly that flat
     * assumption (today's pre-tranche behavior) when omitted, so every
     * existing display-only caller is unaffected.
     *
     * Public/reusable single-item entry point — used by [bestFlips] above
     * and by [dev.saibon.market.flip.CraftVsBinFinder], which needs a cost
     * without `bestFlips`'s "must sell for more than it costs" framing.
     */
    fun craftCostOf(
        itemId: String,
        recipesOf: (itemId: String) -> List<Recipe>,
        marketCostOf: (itemId: String) -> Double?,
        marketCostOfQuantity: (itemId: String, quantity: Double) -> Double? = flatQuantityCost(marketCostOf)
    ): Double? = resolve(itemId.uppercase(), 1.0, recipesOf, marketCostOfQuantity, mutableSetOf(), 0)

    /**
     * The cheapest candidate recipe for [itemId] by resolved per-unit craft
     * cost (doc §1.2), for callers that need the ingredient breakdown itself
     * rather than just the number (e.g.
     * [dev.saibon.market.value.EstimatedItemValueCalculator]'s base-item
     * part lines). Uses a fresh recursion-guard set seeded with [itemId]
     * itself, matching [resolve]'s own state at the point it would call this.
     */
    fun cheapestRecipe(
        itemId: String,
        recipesOf: (itemId: String) -> List<Recipe>,
        marketCostOf: (itemId: String) -> Double?
    ): Recipe? {
        val id = itemId.uppercase()
        val candidates = recipesOf(id)
        if (candidates.size <= 1) return candidates.firstOrNull()
        val marketCostOfQuantity = flatQuantityCost(marketCostOf)
        return candidates.minByOrNull { candidate ->
            craftCostFromRecipe(id, candidate, 1.0, recipesOf, marketCostOfQuantity, mutableSetOf(id), 0) ?: Double.MAX_VALUE
        }
    }

    /** Returns the total cost to obtain [quantity] units of [id] — `resolve(id, 1.0, ...)` is therefore exactly the old per-unit [craftCostOf]. */
    private fun resolve(
        id: String,
        quantity: Double,
        recipesOf: (String) -> List<Recipe>,
        marketCostOfQuantity: (String, Double) -> Double?,
        visiting: MutableSet<String>,
        depth: Int
    ): Double? {
        val buyCost = marketCostOfQuantity(id, quantity)
        // Depth cap or a cycle back to an ingredient already being resolved: stop recursing
        // and fall back to whatever direct market price is available for this id, if any.
        if (depth > MAX_DEPTH || !visiting.add(id)) return buyCost

        val rawCraftCost = recipesOf(id)
            .mapNotNull { craftCostFromRecipe(id, it, quantity, recipesOf, marketCostOfQuantity, visiting, depth) }
            .minOrNull()
        visiting.remove(id)
        if (rawCraftCost == null) return buyCost

        val effectiveCraftCost = rawCraftCost * CRAFT_STEP_MARKUP + CRAFT_STEP_FLAT_COINS
        if (buyCost == null) return effectiveCraftCost
        return if (effectiveCraftCost * CRAFT_PREFERENCE_MARGIN < buyCost) effectiveCraftCost else buyCost
    }

    /**
     * Total cost of however many whole batches of [recipe] are needed to
     * cover [quantity] units (doc §1.4: `batches = ceil(quantity / yield)`,
     * so a large ask correctly compounds each ingredient's own real
     * quantity need rather than pricing everything per-unit as if only ever
     * crafting one at a time), scaled back down to [quantity]'s own share of
     * that batch total so the common `quantity == resultCount == 1` case is
     * numerically identical to the old per-unit-only implementation.
     */
    private fun craftCostFromRecipe(
        id: String,
        recipe: Recipe,
        quantity: Double,
        recipesOf: (String) -> List<Recipe>,
        marketCostOfQuantity: (String, Double) -> Double?,
        visiting: MutableSet<String>,
        depth: Int
    ): Double? {
        // An NPC-shop "recipe" is just a direct purchase — the same acquisition question
        // resolve() already asks marketCostOfQuantity for this same id, which independently
        // discovers this same NPC listing and caps it at its real stock (see
        // IngredientPriceResolver.costOfQuantity) rather than assuming unlimited stock at a
        // flat price. Delegating avoids double-counting that NPC stock cap between the two paths.
        if (recipe.type == RecipeType.NPC) return marketCostOfQuantity(id, quantity)

        val resultCount = recipe.resultCount.coerceAtLeast(1)
        val batches = ceil(quantity / resultCount)
        if (batches <= 0) return null

        // For FORGE recipes, npcCost (if present) is an extra coin cost alongside
        // the ingredients (e.g. Travel Scrolls), not an alternative to them.
        var total = if (recipe.type == RecipeType.FORGE) (recipe.npcCost ?: 0.0) * batches else 0.0
        for (ingredient in recipe.ingredients) {
            val neededQty = ingredient.amount * batches
            total += resolve(ingredient.itemId.uppercase(), neededQty, recipesOf, marketCostOfQuantity, visiting, depth + 1) ?: return null
        }
        val unitsProduced = batches * resultCount
        return total * (quantity / unitsProduced)
    }
}
