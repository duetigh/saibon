package dev.saibon.market

import dev.saibon.data.model.Recipe
import dev.saibon.data.model.RecipeType
import dev.saibon.data.model.SkyblockItem

/**
 * "Craft it cheaper than it sells for" flip ranking. Pure/parameter-injected
 * like [BazaarFlipRanking]/[AuctionFlipRanking] — the data repo's `recipes`
 * dataset lives behind [dev.saibon.data.DataRepository], a client-only
 * object, so recipe/cost lookups are passed in rather than called directly.
 * Note: this checkout's `data/recipes.json`/`items.json` are placeholder
 * seeds (one recipe, two items) — this ranking is architecturally complete
 * but will show little/nothing until the live data repo has real recipe
 * content; that's a data-completeness gap, not a bug here.
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
        val recipe = recipeOf(item.id) ?: return@mapNotNull null
        val cost = craftCost(recipe, recipeOf, marketCostOf, mutableSetOf(item.id.uppercase()), 0) ?: return@mapNotNull null
        val sell = sellPriceOf(item) ?: return@mapNotNull null
        if (cost <= 0) return@mapNotNull null
        val profit = sell - cost
        val percent = profit / cost * 100.0
        if (percent < minMarginPercent) return@mapNotNull null
        CraftFlip(item, cost, sell, profit, percent)
    }.sortedByDescending { it.profit }

    /** Per-unit cost to produce whatever [recipe] crafts, preferring a direct market price per ingredient and only recursing into a sub-recipe when no market price is available. */
    private fun craftCost(
        recipe: Recipe,
        recipeOf: (String) -> Recipe?,
        marketCostOf: (String) -> Double?,
        visiting: MutableSet<String>,
        depth: Int
    ): Double? {
        if (recipe.type == RecipeType.NPC) return recipe.npcCost
        if (depth > MAX_DEPTH) return null

        var total = 0.0
        for (ingredient in recipe.ingredients) {
            val id = ingredient.itemId.uppercase()
            val unitCost = marketCostOf(id) ?: resolveViaSubRecipe(id, recipeOf, marketCostOf, visiting, depth) ?: return null
            total += unitCost * ingredient.amount
        }
        return total / recipe.resultCount.coerceAtLeast(1)
    }

    private fun resolveViaSubRecipe(
        id: String,
        recipeOf: (String) -> Recipe?,
        marketCostOf: (String) -> Double?,
        visiting: MutableSet<String>,
        depth: Int
    ): Double? {
        if (!visiting.add(id)) return null
        val subRecipe = recipeOf(id)
        val resolved = subRecipe?.let { r ->
            craftCost(r, recipeOf, marketCostOf, visiting, depth + 1)?.let { it / r.resultCount.coerceAtLeast(1) }
        }
        visiting.remove(id)
        return resolved
    }
}
