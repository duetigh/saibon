package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.market.AuctionFlipRanking
import dev.saibon.market.AuctionHouseTax
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.AuctionSalesHistoryRepository

/**
 * Wraps the pre-existing item-id-level [AuctionFlipRanking] (math unchanged)
 * for the common case, and adds one candidate per observed modifier-signature
 * bucket that has both a live cheap listing and enough sales-history samples
 * — the spec's "matching modifiers" ask (§4.1.1), reusing the signature
 * buckets `AuctionSalesHistoryRepository`/`AuctionPriceRepository` now track
 * alongside their pre-existing item-only buckets.
 */
object AuctionFlipFinder : FlipFinder {
    override val name = "Auction House"

    override fun scan(): List<FlipCandidate> {
        val marketConfig = Saibon.config.data.market
        val flipConfig = Saibon.config.data.flip

        val itemLevel = AuctionFlipRanking.bestFlips(
            DataRepository.allItems(),
            lowestBinOf = { AuctionPriceRepository.lowestBin(it.id)?.lowestBin?.toDouble() },
            referenceMedianOf = { AuctionSalesHistoryRepository.saleReference(it.id)?.median },
            sampleCountOf = { AuctionSalesHistoryRepository.saleReference(it.id)?.sampleCount ?: 0 },
            taxRatePercent = marketConfig.ahTaxRatePercent,
            minSamples = marketConfig.salesHistoryMinSamples
        ).map { flip ->
            FlipCandidate(
                item = flip.item,
                modifierSignature = "",
                cost = flip.lowestBin,
                estimatedValue = flip.referenceMedian,
                estimatedProfit = flip.estimatedProfit,
                marginPercent = flip.profitPercent,
                confidence = flip.sampleCount,
                sourceFinder = name,
                reason = "median of ${flip.sampleCount} recent sales of this item",
                auctionUuid = AuctionPriceRepository.lowestBin(flip.item.id)?.lowestBinUuid?.takeIf { it.isNotEmpty() }
            )
        }

        val itemsById = DataRepository.allItems().associateBy { it.id.uppercase() }
        val signatureLevel = AuctionSalesHistoryRepository.allSignatureReferences().mapNotNull { (key, reference) ->
            if (reference.sampleCount < flipConfig.modifierMatchMinSamples) return@mapNotNull null
            val lowestBin = AuctionPriceRepository.allSignatureLowestBins()[key] ?: return@mapNotNull null
            val cost = lowestBin.lowestBin.toDouble()
            if (cost <= 0) return@mapNotNull null

            val itemId = key.substringBefore('|')
            val signature = key.substringAfter('|')
            val item = itemsById[itemId] ?: return@mapNotNull null

            val netValue = AuctionHouseTax.netOfTax(reference.median, marketConfig.ahTaxRatePercent)
            val profit = netValue - cost
            FlipCandidate(
                item = item,
                modifierSignature = signature,
                cost = cost,
                estimatedValue = reference.median,
                estimatedProfit = profit,
                marginPercent = profit / cost * 100.0,
                confidence = reference.sampleCount,
                sourceFinder = name,
                reason = "median of ${reference.sampleCount} recent sales matching modifiers ($signature)",
                auctionUuid = lowestBin.lowestBinUuid.takeIf { it.isNotEmpty() }
            )
        }

        return (itemLevel + signatureLevel).sortedByDescending { it.estimatedProfit }
    }
}
