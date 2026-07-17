package dev.saibon.market.model

/**
 * Latest buy/sell price for one Bazaar product, as shown to the player.
 *
 * [buyPrice]/[sellPrice] come from `quick_status` — Hypixel's volume-weighted
 * average across the top ~2% of that side of the book, i.e. what an actual
 * instant-buy/instant-sell transaction of meaningful size would net out to.
 * [topBuyOrderPrice]/[topSellOfferPrice] come from `buy_summary`/`sell_summary`
 * instead: the single best order currently sitting in the book. For thin,
 * high-value items these two can diverge a lot (a couple of large orders
 * beyond the very top can drag the weighted average well away from the top
 * order), so a margin flip — which only cares about undercutting the current
 * best order by a hair, not filling meaningful volume — must use the latter,
 * not `quick_status`.
 */
data class BazaarPrice(
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val topBuyOrderPrice: Double = 0.0,
    val topSellOfferPrice: Double = 0.0
)

/** Wire format of `https://api.hypixel.net/v2/skyblock/bazaar` — a public, keyless Hypixel endpoint. */
data class BazaarResponse(
    val success: Boolean = false,
    val lastUpdated: Long = 0,
    val products: Map<String, BazaarProductEntry> = emptyMap()
)

data class BazaarOrderSummaryEntry(
    val amount: Long = 0,
    val pricePerUnit: Double = 0.0,
    val orders: Int = 0
)

data class BazaarQuickStatus(
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0
)

data class BazaarProductEntry(
    val product_id: String = "",
    val quick_status: BazaarQuickStatus = BazaarQuickStatus(),
    val buy_summary: List<BazaarOrderSummaryEntry> = emptyList(),
    val sell_summary: List<BazaarOrderSummaryEntry> = emptyList()
)
