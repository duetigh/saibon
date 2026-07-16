package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.data.model.SkyblockItem
import dev.saibon.market.BazaarFlipRanking
import dev.saibon.market.MarketPriceRepository

/** Wraps the pre-existing [BazaarFlipRanking.instaBuyNpcFlips]/[BazaarFlipRanking.buyOrderNpcFlips] (both NPC-sell directions) — spec §4.1.4. */
object NpcFlipFinder : FlipFinder {
    override val name = "NPC Flip"

    private fun buyPrice(item: SkyblockItem) = MarketPriceRepository.bazaarPrice(item.id)?.buyPrice?.takeIf { it > 0 }
    private fun sellPrice(item: SkyblockItem) = MarketPriceRepository.bazaarPrice(item.id)?.sellPrice?.takeIf { it > 0 }

    override fun scan(): List<FlipCandidate> {
        val config = Saibon.config.data.market
        val items = DataRepository.allItems()

        val instaBuy = BazaarFlipRanking.instaBuyNpcFlips(items, ::buyPrice, config.instaBuyToNpcMinMarginPercent)
            .map { toCandidate(it, "instant-buy on Bazaar, sell to NPC") }
        val buyOrder = BazaarFlipRanking.buyOrderNpcFlips(items, ::sellPrice, config.buyOrderToNpcMinMarginPercent)
            .map { toCandidate(it, "Bazaar buy order, sell to NPC") }

        return (instaBuy + buyOrder).sortedByDescending { it.estimatedProfit }
    }

    private fun toCandidate(flip: dev.saibon.market.NpcSellFlip, reason: String) = FlipCandidate(
        item = flip.item,
        modifierSignature = "",
        cost = flip.cost,
        estimatedValue = flip.npcSellPrice,
        estimatedProfit = flip.profit,
        marginPercent = flip.profitPercent,
        confidence = 100,
        sourceFinder = name,
        reason = reason
    )
}
