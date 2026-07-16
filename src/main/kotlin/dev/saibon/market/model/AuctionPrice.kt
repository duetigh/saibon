package dev.saibon.market.model

/** Lowest active BIN listing price for one Skyblock item id, aggregated from a full auction sweep. [lowestBinUuid] is that specific listing's auction id, e.g. for a `/viewauction` copy-command button — empty if no listing has been seen yet. */
data class AuctionPrice(
    val lowestBin: Long = 0,
    val activeBinCount: Int = 0,
    val lowestBinUuid: String = ""
)

/** Wire format of one page of `https://api.hypixel.net/v2/skyblock/auctions` — public, keyless. */
data class AuctionsPageResponse(
    val success: Boolean = false,
    val page: Int = 0,
    val totalPages: Int = 0,
    val totalAuctions: Int = 0,
    val lastUpdated: Long = 0,
    val auctions: List<AuctionEntry> = emptyList()
)

data class AuctionEntry(
    val uuid: String = "",
    val item_name: String = "",
    val tier: String = "",
    val starting_bid: Long = 0,
    val claimed: Boolean = false,
    val bin: Boolean = false,
    /** Base64 gzip'd NBT itemstack blob — decode with [dev.saibon.market.AuctionItemDecoder]. */
    val item_bytes: String = ""
)
