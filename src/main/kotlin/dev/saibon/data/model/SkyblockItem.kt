package dev.saibon.data.model

/** One entry from the `items` dataset in the Saibon data repo (see [DataManifest]). */
data class SkyblockItem(
    val id: String = "",
    val name: String = "",
    val tier: String = "",
    val category: String = "",
    val npcSellPrice: Double = 0.0,
    val wikiUrl: String? = null
)
