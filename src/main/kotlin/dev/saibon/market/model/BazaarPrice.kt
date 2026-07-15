package dev.saibon.market.model

/** Latest buy/sell price for one Bazaar product, as shown to the player. */
data class BazaarPrice(
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0
)

/** Wire format of `https://api.hypixel.net/v2/skyblock/bazaar` — a public, keyless Hypixel endpoint. */
data class BazaarResponse(
    val success: Boolean = false,
    val lastUpdated: Long = 0,
    val products: Map<String, BazaarProductEntry> = emptyMap()
)

data class BazaarProductEntry(
    val product_id: String = "",
    val quick_status: BazaarPrice = BazaarPrice()
)
