package dev.saibon.market

import dev.saibon.data.model.SkyblockItem

/**
 * Compares each item's current lowest active BIN listing against a locally
 * computed fair price ([FairPriceCalculator]), ranking by estimated profit
 * after AH tax — the same shape of comparison Cofl-style flipper mods make,
 * computed here purely from Hypixel's own public API (no third-party flip
 * service). Takes price lookups as parameters rather than calling
 * [AuctionPriceRepository]/[AuctionSalesHistoryRepository] directly,
 * mirroring [MarketItemMatcher]'s parameter-injection pattern so this
 * ranking math has no client dependency.
 */
data class AuctionFlip(
    val item: SkyblockItem,
    val lowestBin: Double,
    val fairPrice: FairPriceResult,
    val estimatedProfit: Double,
    val profitPercent: Double
)

object AuctionFlipRanking {
    fun bestFlips(
        items: Collection<SkyblockItem>,
        lowestBinOf: (SkyblockItem) -> Double?,
        fairPriceOf: (SkyblockItem) -> FairPriceResult?,
        taxRatePercent: Double,
        minSamples: Int
    ): List<AuctionFlip> = items.mapNotNull { item ->
        val lowestBin = lowestBinOf(item) ?: return@mapNotNull null
        val fairPrice = fairPriceOf(item) ?: return@mapNotNull null
        if (fairPrice.sampleCount < minSamples || lowestBin <= 0) return@mapNotNull null

        val netFairPrice = AuctionHouseTax.netOfTax(fairPrice.fairPrice, taxRatePercent)
        val profit = netFairPrice - lowestBin
        AuctionFlip(item, lowestBin, fairPrice, profit, profit / lowestBin * 100.0)
    }.sortedByDescending { it.estimatedProfit }
}
