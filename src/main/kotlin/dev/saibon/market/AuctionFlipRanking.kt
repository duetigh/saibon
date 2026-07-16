package dev.saibon.market

import dev.saibon.data.model.SkyblockItem

/**
 * Compares each item's current lowest active BIN listing against a locally
 * computed reference resale price, ranking by estimated profit — the same
 * shape of comparison SkyCofl-style flipper mods make, computed here purely
 * from Hypixel's own public API (no third-party flip service). Takes price
 * lookups as parameters rather than calling [AuctionPriceRepository]/
 * [AuctionSalesHistoryRepository] directly, mirroring [MarketItemMatcher]'s
 * parameter-injection pattern so this ranking math has no client dependency.
 */
data class AuctionFlip(
    val item: SkyblockItem,
    val lowestBin: Double,
    val referenceMedian: Double,
    val sampleCount: Int,
    val estimatedProfit: Double,
    val profitPercent: Double
)

object AuctionFlipRanking {
    fun bestFlips(
        items: Collection<SkyblockItem>,
        lowestBinOf: (SkyblockItem) -> Double?,
        referenceMedianOf: (SkyblockItem) -> Double?,
        sampleCountOf: (SkyblockItem) -> Int,
        taxRatePercent: Double,
        minSamples: Int
    ): List<AuctionFlip> = items.mapNotNull { item ->
        val lowestBin = lowestBinOf(item) ?: return@mapNotNull null
        val median = referenceMedianOf(item) ?: return@mapNotNull null
        val samples = sampleCountOf(item)
        if (samples < minSamples || lowestBin <= 0) return@mapNotNull null

        val netMedian = AuctionHouseTax.netOfTax(median, taxRatePercent)
        val profit = netMedian - lowestBin
        AuctionFlip(item, lowestBin, median, samples, profit, profit / lowestBin * 100.0)
    }.sortedByDescending { it.estimatedProfit }
}
