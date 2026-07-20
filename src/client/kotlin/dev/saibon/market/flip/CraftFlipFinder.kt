package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.market.CraftFlipRanking
import dev.saibon.market.InstasellPricing
import dev.saibon.market.MarketPriceRepository

/**
 * Wraps the pre-existing [CraftFlipRanking] (craft-cost-vs-sale-price) math —
 * spec §4.1.3. Ingredient costs go through [IngredientPriceResolver] so
 * AH-only ingredients (most rare/dungeon/boss-drop items) are priced instead
 * of silently dropping the whole recipe.
 */
object CraftFlipFinder : FlipFinder {
    override val name = "Craft Flip"

    /**
     * Bazaar's `quick_status.sellPrice` sweeps `buy_summary` (other players' buy
     * orders), so [dev.saibon.market.model.BazaarPrice.buyMovingWeek] is the real
     * volume figure behind how fast a freshly-crafted item's resale actually clears
     * — run through [InstasellPricing] rather than assumed to fill instantly at the
     * full quick_status average.
     */
    private fun sellPrice(item: dev.saibon.data.model.SkyblockItem): Double? {
        val bazaar = MarketPriceRepository.bazaarPrice(item.id) ?: return null
        if (bazaar.sellPrice <= 0) return null
        val volumePerWeek = (bazaar.buyMovingWeek / 7.0).toInt()
        return InstasellPricing.instasellTarget(bazaar.sellPrice, volumePerWeek)
    }

    override fun scan(): List<FlipCandidate> {
        val config = Saibon.config.data.market
        return CraftFlipRanking.bestFlips(
            DataRepository.allItems(),
            recipesOf = DataRepository::recipesFor,
            marketCostOf = { id -> IngredientPriceResolver.costOf(id) },
            sellPriceOf = { item -> sellPrice(item) },
            minMarginPercent = config.craftFlipMinMarginPercent,
            marketCostOfQuantity = IngredientPriceResolver::costOfQuantity
        ).map { flip ->
            FlipCandidate(
                item = flip.item,
                modifierSignature = "",
                cost = flip.craftCost,
                estimatedValue = flip.sellPrice,
                estimatedProfit = flip.profit,
                marginPercent = flip.profitPercent,
                confidence = 100,
                profitPerHour = flip.profitPerHour,
                sourceFinder = name,
                reason = "cost to craft from current Bazaar ingredient prices"
            )
        }
    }
}
