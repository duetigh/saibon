package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.market.BazaarFlipRanking
import dev.saibon.market.MarketPriceRepository

/** Wraps the pre-existing [BazaarFlipRanking.marginFlips] (buy-order/sell-offer spread) math — spec §4.1.2. */
object BazaarMarginFlipFinder : FlipFinder {
    override val name = "Bazaar Margin"

    private fun sellOfferPrice(item: dev.saibon.data.model.SkyblockItem) = MarketPriceRepository.bazaarPrice(item.id)?.topSellOfferPrice?.takeIf { it > 0 }
    private fun buyOrderPrice(item: dev.saibon.data.model.SkyblockItem) = MarketPriceRepository.bazaarPrice(item.id)?.topBuyOrderPrice?.takeIf { it > 0 }

    override fun scan(): List<FlipCandidate> {
        val config = Saibon.config.data.market
        return BazaarFlipRanking.marginFlips(DataRepository.allItems(), ::sellOfferPrice, ::buyOrderPrice, config.flipMinMarginPercent)
            .map { flip ->
                FlipCandidate(
                    item = flip.item,
                    modifierSignature = "",
                    cost = flip.buyOrderPrice,
                    estimatedValue = flip.sellOfferPrice,
                    estimatedProfit = flip.margin,
                    marginPercent = flip.marginPercent,
                    confidence = 100,
                    sourceFinder = name,
                    reason = "current Bazaar buy-order/sell-offer spread"
                )
            }
    }
}
