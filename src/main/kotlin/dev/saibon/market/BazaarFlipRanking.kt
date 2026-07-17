package dev.saibon.market

import dev.saibon.data.model.SkyblockItem

/**
 * Pure Bazaar flip math, parameter-injected (no client dependency) same as
 * [MarketItemMatcher]/[AuctionFlipRanking]. `buyPrice`/`sellPrice` (the
 * `quick_status` volume-weighted averages) approximate what an instant buy
 * or instant sell of meaningful size actually nets out to, and feed
 * [instaBuyNpcFlips]/[buyOrderNpcFlips] below, since those really do execute
 * an instant transaction against the book.
 *
 * [marginFlips] is different: placing your own buy order / sell offer only
 * needs to undercut the current *best* order by a hair, not fill real
 * volume, so it must be called with the order-book top-of-book prices
 * (`BazaarPrice.topSellOfferPrice`/`topBuyOrderPrice`) rather than
 * `quick_status` — for thin, high-value items the weighted average can
 * diverge sharply from the top order, which previously made the margin
 * number meaningless. See [dev.saibon.market.model.BazaarPrice] kdoc.
 *
 * Three distinct flip strategies, matching the three ways to move an item
 * through the Bazaar:
 * - [instaBuyNpcFlips]: instant-buy on the Bazaar (pay ask), instant-sell to
 *   an NPC vendor.
 * - [buyOrderNpcFlips]: place a buy order (pay ~bid), instant-sell to an NPC
 *   vendor — usually cheaper than instant-buying, at the cost of waiting for
 *   the order to fill.
 * - [marginFlips]: place a buy order, then a sell offer — the classic
 *   Bazaar-only flip, profiting off the spread itself (top sell offer minus
 *   top buy order).
 *
 * All three return `null`/exclude an item entirely when a required price
 * (Bazaar buy, Bazaar sell, or NPC sell) is missing or non-positive — callers
 * that need to show those items anyway (e.g. a browse list, sorted N/A-last)
 * should treat that missing input as "N/A" themselves rather than defaulting
 * it to zero, which would misrepresent it as a real, computed value.
 */
data class BazaarMarginFlip(
    val item: SkyblockItem,
    val buyOrderPrice: Double,
    val sellOfferPrice: Double,
    val margin: Double,
    val marginPercent: Double
)

data class NpcSellFlip(
    val item: SkyblockItem,
    val cost: Double,
    val npcSellPrice: Double,
    val profit: Double,
    val profitPercent: Double
)

object BazaarFlipRanking {
    /** Buy-order -> sell-offer margin (ask minus bid). Null if either side of the book is missing/non-positive. */
    fun margin(buyPrice: Double?, sellPrice: Double?): Double? {
        val buy = buyPrice?.takeIf { it > 0 } ?: return null
        val sell = sellPrice?.takeIf { it > 0 } ?: return null
        return buy - sell
    }

    fun marginFlips(
        items: Collection<SkyblockItem>,
        buyPriceOf: (SkyblockItem) -> Double?,
        sellPriceOf: (SkyblockItem) -> Double?,
        minMarginPercent: Double
    ): List<BazaarMarginFlip> = items.mapNotNull { item ->
        val buy = buyPriceOf(item)?.takeIf { it > 0 } ?: return@mapNotNull null
        val sell = sellPriceOf(item)?.takeIf { it > 0 } ?: return@mapNotNull null
        val margin = buy - sell
        val percent = margin / sell * 100.0
        if (percent < minMarginPercent) return@mapNotNull null
        BazaarMarginFlip(item, sell, buy, margin, percent)
    }.sortedByDescending { it.margin }

    /** Instant-buy on the Bazaar (pay ask), instant-sell to an NPC. */
    fun instaBuyNpcFlips(
        items: Collection<SkyblockItem>,
        buyPriceOf: (SkyblockItem) -> Double?,
        minMarginPercent: Double
    ): List<NpcSellFlip> = npcFlips(items, buyPriceOf, minMarginPercent)

    /** Place a buy order (fills near the current bid), instant-sell to an NPC. */
    fun buyOrderNpcFlips(
        items: Collection<SkyblockItem>,
        sellPriceOf: (SkyblockItem) -> Double?,
        minMarginPercent: Double
    ): List<NpcSellFlip> = npcFlips(items, sellPriceOf, minMarginPercent)

    private fun npcFlips(
        items: Collection<SkyblockItem>,
        costOf: (SkyblockItem) -> Double?,
        minMarginPercent: Double
    ): List<NpcSellFlip> = items.mapNotNull { item ->
        val cost = costOf(item)?.takeIf { it > 0 } ?: return@mapNotNull null
        val npc = item.npcSellPrice
        if (npc <= 0) return@mapNotNull null
        val profit = npc - cost
        val percent = profit / cost * 100.0
        if (percent < minMarginPercent) return@mapNotNull null
        NpcSellFlip(item, cost, npc, profit, percent)
    }.sortedByDescending { it.profit }
}
