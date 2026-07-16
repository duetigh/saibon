package dev.saibon.market

/**
 * Hypixel deducts a tax/fee on every completed Auction House sale before the
 * seller is paid out. The exact current tier schedule can't be verified live
 * from this project (no reachable Hypixel session, and it has changed
 * historically), so rather than hardcode a possibly-stale table, the rate is
 * a single user-overridable estimate ([dev.saibon.market.MarketConfig.ahTaxRatePercent],
 * exposed in Flip Finder settings) — approximate, not authoritative.
 */
object AuctionHouseTax {
    fun netOfTax(grossPrice: Double, taxRatePercent: Double): Double =
        grossPrice * (1.0 - (taxRatePercent / 100.0).coerceIn(0.0, 1.0))
}
