package dev.saibon.market

/**
 * Liquidity-scaled discount off a raw fair/median price — Coflnet
 * `SkyBackendForFrontend`'s `InstaSellPrice` (doc §3.8), adapted to this
 * mod's own [dev.saibon.market.FairPriceCalculator] output. A raw
 * statistical fair price is what past sales *averaged out to*, not
 * necessarily what a listing needs to be priced at to actually sell
 * promptly — a deeper discount is applied for cheap and/or thin-volume
 * items (which take longer to move at full price), a shallower one for
 * expensive, liquid ones. [dev.saibon.market.flip.AuctionFlipFinder] uses
 * this (rather than the raw fair price) as the assumed resale target when
 * estimating a flip's profit, so a thin/illiquid item's estimated profit
 * isn't overstated by assuming a full-price instant resale.
 */
object InstasellPricing {
    fun instasellTarget(fairPrice: Double, volumePerWeek: Int): Double {
        if (fairPrice <= 0.0) return fairPrice

        var deduct = 0.12
        if (fairPrice < 15_000_000.0) deduct = 0.18
        if (fairPrice > 150_000_000.0) deduct = 0.10

        val volumePerDay = volumePerWeek / 7.0
        if (volumePerDay < 1.0) deduct += 0.05
        if (volumePerDay < 0.15) deduct += 0.05

        return fairPrice * (1.0 - deduct)
    }
}
