package dev.saibon.market.ui

import dev.saibon.search.query.SearchQuery

/**
 * Matches a live AH/Bazaar GUI listing's display name + lore-parsed price
 * against a query — see [MarketMenuOverlay]. Separate from
 * [dev.saibon.market.MarketItemMatcher], which matches against the data-repo
 * [dev.saibon.data.model.SkyblockItem] catalog rather than live GUI text.
 */
object LiveListingMatcher {
    fun matches(name: String, price: Double?, query: SearchQuery): Boolean = when (query) {
        is SearchQuery.And -> matches(name, price, query.left) && matches(name, price, query.right)
        is SearchQuery.Or -> matches(name, price, query.left) || matches(name, price, query.right)
        is SearchQuery.Not -> !matches(name, price, query.inner)
        is SearchQuery.Bare -> query.value.isBlank() || name.contains(query.value, ignoreCase = true)
        is SearchQuery.Field -> when (query.name) {
            "minprice" -> query.value.toDoubleOrNull()?.let { (price ?: 0.0) >= it } ?: true
            "maxprice" -> query.value.toDoubleOrNull()?.let { (price ?: Double.MAX_VALUE) <= it } ?: true
            else -> name.contains(query.value, ignoreCase = true)
        }
    }
}
