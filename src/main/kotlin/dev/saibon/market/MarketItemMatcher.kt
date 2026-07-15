package dev.saibon.market

import dev.saibon.data.model.SkyblockItem
import dev.saibon.search.query.SearchQuery
import dev.saibon.search.query.SkyblockItemMatcher

/**
 * Extends [SkyblockItemMatcher]'s name/rarity/category/id matching with
 * `minprice:`/`minmargin:` numeric fields, resolved against whichever price
 * source the caller supplies — AH lowest-BIN for
 * [dev.saibon.market.ui.AuctionSearchScreen], Bazaar buy/sell spread for
 * [dev.saibon.market.ui.BazaarSearchScreen] — so both screens share one
 * field grammar without either repository knowing about the other.
 */
object MarketItemMatcher {
    fun matches(
        item: SkyblockItem,
        query: SearchQuery,
        priceOf: (SkyblockItem) -> Double?,
        marginOf: (SkyblockItem) -> Double?
    ): Boolean = when (query) {
        is SearchQuery.And -> matches(item, query.left, priceOf, marginOf) && matches(item, query.right, priceOf, marginOf)
        is SearchQuery.Or -> matches(item, query.left, priceOf, marginOf) || matches(item, query.right, priceOf, marginOf)
        is SearchQuery.Not -> !matches(item, query.inner, priceOf, marginOf)
        is SearchQuery.Field -> when (query.name) {
            "minprice" -> query.value.toDoubleOrNull()?.let { (priceOf(item) ?: 0.0) >= it } ?: true
            "minmargin" -> query.value.toDoubleOrNull()?.let { (marginOf(item) ?: 0.0) >= it } ?: true
            else -> SkyblockItemMatcher.matches(item, query)
        }
        else -> SkyblockItemMatcher.matches(item, query)
    }
}
