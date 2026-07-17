package dev.saibon.market

import dev.saibon.data.model.Recipe
import dev.saibon.data.model.RecipeType
import dev.saibon.data.model.SkyblockItem

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
    val profitPercent: Double
)

object CraftFlipRanking {
    /** Bounds recursion through multi-tier forge/craft chains against cyclical or malformed recipe data. */
    private const val MAX_DEPTH = 6

    fun bestFlips(
        items: Collection<SkyblockItem>,
        recipeOf: (itemId: String) -> Recipe?,
        marketCostOf: (itemId: String) -> Double?,
        sellPriceOf: (item: SkyblockItem) -> Double?,
        minMarginPercent: Double
    ): List<CraftFlip> = items.mapNotNull { item ->
        recipeOf(item.id) ?: return@mapNotNull null // craft flips only apply to items with an actual recipe, not any item with a market price
        val cost = craftCostOf(item.id, recipeOf, marketCostOf) ?: return@mapNotNull null
        val sell = sellPriceOf(item) ?: return@mapNotNull null
        if (cost <= 0) return@mapNotNull null
        val profit = sell - cost
        val percent = profit / cost * 100.0
        if (percent < minMarginPercent) return@mapNotNull null
        CraftFlip(item, cost, sell, profit, percent)
    }.sortedByDescending { it.profit }

    /**
     * Per-unit cost to *acquire* [itemId] — the cheaper of buying it directly
     * via [marketCostOf] or crafting it from its recipe's ingredients (each
     * priced the same way, recursively). Spec §5.2's `min(buy, craft)` rule:
     * crafting isn't always assumed optimal, an oversupplied ingredient can
     * make buying the finished item outright cheaper. Public/reusable single-
     * item entry point — used by [bestFlips] above and by
     * [dev.saibon.market.flip.CraftVsBinFinder], which needs a cost without
     * `bestFlips`'s "must sell for more than it costs" framing.
     */
    fun craftCostOf(
        itemId: String,
        recipeOf: (itemId: String) -> Recipe?,
        marketCostOf: (itemId: String) -> Double?
    ): Double? = resolve(itemId.uppercase(), recipeOf, marketCostOf, mutableSetOf(), 0)

    private fun resolve(
        id: String,
        recipeOf: (String) -> Recipe?,
        marketCostOf: (String) -> Double?,
        visiting: MutableSet<String>,
        depth: Int
    ): Double? {
        val marketCost = marketCostOf(id)
        // Depth cap or a cycle back to an ingredient already being resolved: stop recursing
        // and fall back to whatever direct market price is available for this id, if any.
        if (depth > MAX_DEPTH || !visiting.add(id)) return marketCost

        val craftCost = recipeOf(id)?.let { craftCostFromRecipe(it, recipeOf, marketCostOf, visiting, depth) }
        visiting.remove(id)

        return if (marketCost != null && craftCost != null) minOf(marketCost, craftCost) else marketCost ?: craftCost
    }

    /** Sums [recipe]'s ingredient costs (each resolved via [resolve], so a sub-ingredient's own buy-vs-craft choice is made independently) plus any NPC coin component, per unit produced. */
    private fun craftCostFromRecipe(
        recipe: Recipe,
        recipeOf: (String) -> Recipe?,
        marketCostOf: (String) -> Double?,
        visiting: MutableSet<String>,
        depth: Int
    ): Double? {
        if (recipe.type == RecipeType.NPC) return recipe.npcCost

        // For FORGE recipes, npcCost (if present) is an extra coin cost alongside
        // the ingredients (e.g. Travel Scrolls), not an alternative to them.
        var total = if (recipe.type == RecipeType.FORGE) recipe.npcCost ?: 0.0 else 0.0
        for (ingredient in recipe.ingredients) {
            val unitCost = resolve(ingredient.itemId.uppercase(), recipeOf, marketCostOf, visiting, depth + 1) ?: return null
            total += unitCost * ingredient.amount
        }
        return total / recipe.resultCount.coerceAtLeast(1)
    }
}
