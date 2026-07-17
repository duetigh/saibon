package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.CraftFlipRanking

/**
 * "This live AH listing is cheaper than it would cost to craft" — needs
 * zero sales history, only the recipe graph (already synced), live Bazaar
 * prices, and [AuctionPriceRepository]'s lowest-BIN sweep — all
 * already-fetched data. Usable within minutes of enabling the AH sweep,
 * unlike [AuctionFlipFinder] which needs a real sales-history sample to
 * build confidence, so this is the flip signal that gives a fresh install
 * something useful before local sales history (or the bundled server
 * snapshot, see [dev.saibon.data.DataRepository.fairPriceSnapshot]) has
 * anything to say.
 */
object CraftVsBinFinder : FlipFinder {
    override val name = "Craft vs BIN"

    override fun scan(): List<FlipCandidate> {
        val flipConfig = Saibon.config.data.flip
        val itemsById = DataRepository.allItems()
            .filter { !flipConfig.excludeSoulbound || !it.soulbound }
            .associateBy { it.id.uppercase() }

        return AuctionPriceRepository.allLowestBins().mapNotNull { (itemId, lowestBin) ->
            val item = itemsById[itemId] ?: return@mapNotNull null
            DataRepository.recipesFor(itemId).firstOrNull() ?: return@mapNotNull null // only real craftable items — a bare market price isn't a "craft" signal
            val cost = lowestBin.lowestBin.toDouble()
            if (cost <= 0) return@mapNotNull null

            val craftCost = CraftFlipRanking.craftCostOf(
                itemId,
                recipeOf = { DataRepository.recipesFor(it).firstOrNull() },
                marketCostOf = { id -> IngredientPriceResolver.costOf(id) }
            ) ?: return@mapNotNull null
            if (craftCost <= 0) return@mapNotNull null

            val profit = craftCost - cost
            val marginPercent = profit / cost * 100.0
            if (marginPercent < flipConfig.craftVsBinMinMarginPercent) return@mapNotNull null

            FlipCandidate(
                item = item,
                modifierSignature = "",
                cost = cost,
                estimatedValue = craftCost,
                estimatedProfit = profit,
                marginPercent = marginPercent,
                confidence = 100,
                sourceFinder = name,
                reason = "lowest BIN is below this item's recursive craft cost",
                auctionUuid = lowestBin.lowestBinUuid.takeIf { it.isNotEmpty() },
                sellerUuid = lowestBin.lowestBinSeller.takeIf { it.isNotEmpty() }
            )
        }.sortedByDescending { it.estimatedProfit }
    }
}
