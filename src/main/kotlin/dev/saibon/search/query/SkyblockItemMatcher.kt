package dev.saibon.search.query

import dev.saibon.data.model.SkyblockItem

/**
 * Evaluates a parsed [SearchQuery] against a data-repo [SkyblockItem] rather
 * than a live [dev.saibon.search.extract.SkyblockItemInfo] extracted from an
 * inventory slot — used by the Item List GUI's search bar to filter the full
 * item database instead of the items in a currently-open container.
 */
object SkyblockItemMatcher {
    fun matches(item: SkyblockItem, query: SearchQuery): Boolean = when (query) {
        is SearchQuery.And -> matches(item, query.left) && matches(item, query.right)
        is SearchQuery.Or -> matches(item, query.left) || matches(item, query.right)
        is SearchQuery.Not -> !matches(item, query.inner)
        is SearchQuery.Bare -> query.value.isBlank() || bareMatches(item, query.value)
        is SearchQuery.Field -> matchesField(item, query.name, query.value)
    }

    private fun bareMatches(item: SkyblockItem, value: String): Boolean =
        item.name.contains(value, ignoreCase = true) || item.id.contains(value, ignoreCase = true)

    private fun matchesField(item: SkyblockItem, name: String, value: String): Boolean = when (name) {
        "rarity", "tier" -> item.tier.contains(value, ignoreCase = true)
        "category", "type" -> item.category.contains(value, ignoreCase = true)
        "name" -> item.name.contains(value, ignoreCase = true)
        "id" -> item.id.contains(value, ignoreCase = true)
        else -> bareMatches(item, value)
    }
}
