package dev.saibon.market.model

/**
 * Wire format of `https://api.hypixel.net/v2/skyblock/auctions_ended` —
 * public, keyless, a rolling ~1hr window of recently sold auctions. Field
 * names/shape confirmed live against the real endpoint (`curl`, not guessed):
 * `{success, lastUpdated, auctions: [{auction_id, seller, seller_profile,
 * buyer, buyer_profile, timestamp, price, bin, item_bytes}]}` — same
 * `item_bytes` blob shape [dev.saibon.market.AuctionItemDecoder] already
 * decodes for `/v2/skyblock/auctions`.
 */
data class AuctionsEndedResponse(
    val success: Boolean = false,
    val lastUpdated: Long = 0,
    val auctions: List<EndedAuctionEntry> = emptyList()
)

data class EndedAuctionEntry(
    val auction_id: String = "",
    val seller: String = "",
    val timestamp: Long = 0,
    val price: Long = 0,
    val bin: Boolean = false,
    /** Base64 gzip'd NBT itemstack blob — decode with [dev.saibon.market.AuctionItemDecoder]. */
    val item_bytes: String = ""
)
