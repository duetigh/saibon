package dev.saibon.search.query

import dev.saibon.search.extract.SkyblockItemInfo

/** Recursively evaluates a parsed [SearchQuery] against an extracted item. */
object SearchMatcher {
    fun matches(info: SkyblockItemInfo, query: SearchQuery): Boolean = when (query) {
        is SearchQuery.And -> matches(info, query.left) && matches(info, query.right)
        is SearchQuery.Or -> matches(info, query.left) || matches(info, query.right)
        is SearchQuery.Not -> !matches(info, query.inner)
        is SearchQuery.Bare -> query.value.isBlank() || info.searchableText.contains(query.value, ignoreCase = true)
        is SearchQuery.Field -> matchesField(info, query.name, query.value)
    }

    private fun matchesField(info: SkyblockItemInfo, name: String, value: String): Boolean = when (name) {
        "enchant", "ench" -> info.enchants.any { it.name.contains(value, ignoreCase = true) }
        "rarity" -> info.rarity?.contains(value, ignoreCase = true) == true
        "reforge" -> info.reforge?.contains(value, ignoreCase = true) == true
        "type" -> info.type?.contains(value, ignoreCase = true) == true
        "stars", "star" -> value.toIntOrNull()?.let { info.stars >= it } ?: false
        "name" -> info.name.contains(value, ignoreCase = true)
        else -> info.searchableText.contains(value, ignoreCase = true)
    }
}
