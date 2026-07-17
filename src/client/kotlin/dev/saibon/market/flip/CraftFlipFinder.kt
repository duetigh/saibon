package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.market.CraftFlipRanking
import dev.saibon.market.MarketPriceRepository

/**
 * Wraps the pre-existing [CraftFlipRanking] (craft-cost-vs-sale-price) math —
 * spec §4.1.3. Ingredient costs go through [IngredientPriceResolver] so
 * AH-only ingredients (most rare/dungeon/boss-drop items) are priced instead
 * of silently dropping the whole recipe.
 */
object CraftFlipFinder : FlipFinder {
    override val name = "Craft Flip"

    private fun sellPrice(item: dev.saibon.data.model.SkyblockItem) = MarketPriceRepository.bazaarPrice(item.id)?.sellPrice?.takeIf { it > 0 }

    override fun scan(): List<FlipCandidate> {
        val config = Saibon.config.data.market
        return CraftFlipRanking.bestFlips(
            DataRepository.allItems(),
            recipeOf = { DataRepository.recipesFor(it).firstOrNull() },
            marketCostOf = { id -> IngredientPriceResolver.costOf(id) },
            sellPriceOf = { item -> sellPrice(item) },
            minMarginPercent = config.craftFlipMinMarginPercent
        ).map { flip ->
            FlipCandidate(
                item = flip.item,
                modifierSignature = "",
                cost = flip.craftCost,
                estimatedValue = flip.sellPrice,
                estimatedProfit = flip.profit,
                marginPercent = flip.profitPercent,
                confidence = 100,
                sourceFinder = name,
                reason = "cost to craft from current Bazaar ingredient prices"
            )
        }
    }
}
