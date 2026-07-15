package dev.saibon.search.query

/**
 * Parsed inventory search query. `&`/`|`/`!` combine [Field] (`enchant:protection`)
 * and [Bare] (plain substring) terms — see [SearchParser] for the grammar and
 * [SearchMatcher] for evaluation against a [dev.saibon.search.extract.SkyblockItemInfo].
 */
sealed class SearchQuery {
    data class And(val left: SearchQuery, val right: SearchQuery) : SearchQuery()
    data class Or(val left: SearchQuery, val right: SearchQuery) : SearchQuery()
    data class Not(val inner: SearchQuery) : SearchQuery()
    data class Field(val name: String, val value: String) : SearchQuery()
    data class Bare(val value: String) : SearchQuery()
}
