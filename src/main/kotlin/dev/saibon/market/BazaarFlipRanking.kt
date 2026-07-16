package dev.saibon.market

import dev.saibon.data.model.SkyblockItem

/**
 * Pure Bazaar flip math, parameter-injected (no client dependency) same as
 * [MarketItemMatcher]/[AuctionFlipRanking]. Two rankings:
 * [marginFlips] migrates the buy/sell-spread sort that used to live inline
 * in `BazaarSearchScreen`'s `MARGIN_DESC` sort order, so both that screen and
 * the new [dev.saibon.market.ui.BazaarMenuOverlay] share one implementation.
 * [npcSellFlips] is the "buy on the Bazaar, instantly sell to an NPC vendor"
 * flip: profitable whenever an item's Bazaar buy price sits below its known
 * NPC sell price.
 */
data class BazaarMarginFlip(
    val item: SkyblockItem,
    val buyPrice: Double,
    val sellPrice: Double,
    val margin: Double,
    val marginPercent: Double
)

data class NpcSellFlip(
    val item: SkyblockItem,
    val buyPrice: Double,
    val npcSellPrice: Double,
    val profit: Double,
    val profitPercent: Double
)

object BazaarFlipRanking {
    fun marginFlips(
        items: Collection<SkyblockItem>,
        buyPriceOf: (SkyblockItem) -> Double?,
        sellPriceOf: (SkyblockItem) -> Double?,
        minMarginPercent: Double
    ): List<BazaarMarginFlip> = items.mapNotNull { item ->
        val buy = buyPriceOf(item) ?: return@mapNotNull null
        val sell = sellPriceOf(item) ?: return@mapNotNull null
        if (buy <= 0) return@mapNotNull null
        val margin = sell - buy
        val percent = margin / buy * 100.0
        if (percent < minMarginPercent) return@mapNotNull null
        BazaarMarginFlip(item, buy, sell, margin, percent)
    }.sortedByDescending { it.margin }

    fun npcSellFlips(
        items: Collection<SkyblockItem>,
        buyPriceOf: (SkyblockItem) -> Double?,
        minMarginPercent: Double
    ): List<NpcSellFlip> = items.mapNotNull { item ->
        val buy = buyPriceOf(item) ?: return@mapNotNull null
        val npc = item.npcSellPrice
        if (buy <= 0 || npc <= 0) return@mapNotNull null
        val profit = npc - buy
        val percent = profit / buy * 100.0
        if (percent < minMarginPercent) return@mapNotNull null
        NpcSellFlip(item, buy, npc, profit, percent)
    }.sortedByDescending { it.profit }
}
