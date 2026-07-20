package dev.saibon.market

/**
 * Models the two real deductions between a BIN's listing price and what the
 * seller actually banks:
 *
 * 1. **Listing fee** — paid upfront when creating the BIN, lost even if it
 *    never sells: 1% under 10M, 2% 10M-100M, 2.5% above 100M.
 * 2. **Claim tax** — deducted when collecting a completed sale's proceeds:
 *    flat 1% on sales above 1M, capped so the payout never drops below 1M.
 *
 * Sourced from Coflnet's own AH tax-avoidance guide
 * (sky.coflnet.com/guides/avoid-taxes-and-losses) cross-referenced against
 * Hypixel Forums threads discussing the same numbers — the Hypixel Wiki
 * itself blocks direct fetches from this project (same Cloudflare block
 * noted for `NEU-REPO` elsewhere), so this wasn't verified against the wiki
 * page directly. Hypixel has changed these numbers before, so
 * [MarketConfig.ahTaxRatePercent] remains a user-overridable escape hatch —
 * set it above 0 to replace this whole calculation with one flat percentage.
 */
object AuctionHouseTax {
    private const val CLAIM_TAX_RATE = 0.01
    private const val CLAIM_TAX_FLOOR = 1_000_000.0

    private fun listingFeeRate(price: Double): Double = when {
        price > 100_000_000.0 -> 0.025
        price > 10_000_000.0 -> 0.02
        else -> 0.01
    }

    /** Quadruples the effective claim-tax rate when Derpy is the active mayor — see [dev.saibon.market.MayorRepository]. */
    private const val DERPY_CLAIM_TAX_MULTIPLIER = 4.0

    private fun claimTax(price: Double, rate: Double = CLAIM_TAX_RATE): Double {
        if (price <= CLAIM_TAX_FLOOR) return 0.0
        return (price * rate).coerceAtMost(price - CLAIM_TAX_FLOOR)
    }

    /**
     * [overridePercent] > 0 replaces the bracket table with one flat percentage of
     * [grossPrice]; 0 (the default) uses the real listing-fee + claim-tax model above.
     * [derpyActive] quadruples the claim-tax rate (this file's own doc comment already
     * cites Hypixel's real Derpy-mayor tax quadrupling; see [dev.saibon.market.MayorRepository]
     * for the live detection this is sourced from) — ignored when [overridePercent] is set,
     * since that's a deliberate user override of the whole model. Defaults to `false` so every
     * existing caller that doesn't pass it keeps behaving exactly as before.
     */
    fun netOfTax(grossPrice: Double, overridePercent: Double = 0.0, derpyActive: Boolean = false): Double {
        if (overridePercent > 0.0) {
            return grossPrice * (1.0 - (overridePercent / 100.0).coerceIn(0.0, 1.0))
        }
        val listingFee = grossPrice * listingFeeRate(grossPrice)
        val claimTaxRate = if (derpyActive) CLAIM_TAX_RATE * DERPY_CLAIM_TAX_MULTIPLIER else CLAIM_TAX_RATE
        val claim = claimTax(grossPrice, claimTaxRate)
        return (grossPrice - listingFee - claim).coerceAtLeast(0.0)
    }
}
