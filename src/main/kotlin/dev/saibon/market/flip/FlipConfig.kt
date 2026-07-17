package dev.saibon.market.flip

/** Persisted settings for the unified `/saibonflips` engine (`FlipEngine`) — one enable flag per [FlipFinder] strategy plus the shared alert thresholds. Distinct from `MarketConfig`, which still governs the underlying Bazaar/AH/sales-history feeds these finders read from. */
data class FlipConfig(
    /** On by default — needs `MarketConfig.salesHistoryAutoRefresh`/`ahAutoRefresh` on too (also default on), which together are what make this finder produce real candidates immediately instead of after a manual opt-in. */
    var auctionFlipsEnabled: Boolean = true,
    var bazaarMarginFlipsEnabled: Boolean = true,
    var npcFlipsEnabled: Boolean = true,
    var craftFlipsEnabled: Boolean = true,
    /** Needs no sales history at all (just recipes + live Bazaar + the AH lowest-BIN sweep), so it's the one finder that's useful within minutes of a fresh install — on by default for that reason. Still needs `MarketConfig.ahAutoRefresh` on to have any listings to compare against. */
    var craftVsBinFlipsEnabled: Boolean = true,
    var craftVsBinMinMarginPercent: Double = 10.0,
    /** Soulbound items can't be relisted on the AH, so a flip that depends on reselling there is never actually executable — excluded from [AuctionFlipFinder]/[CraftVsBinFinder] by default. Left on for [BazaarMarginFlipFinder]/[NpcFlipFinder]/[CraftFlipFinder], whose resale venue is Bazaar/NPC, not the AH. */
    var excludeSoulbound: Boolean = true,
    var scanIntervalSeconds: Int = 30,
    /** Bucket sales history by item+modifier signature (enchants/hot potato count/stars) when at least this many samples exist for that exact signature; otherwise fall back to the item-id-only bucket. */
    var modifierMatchMinSamples: Int = 3,
    var alertEnabled: Boolean = true,
    var alertMinProfit: Double = 100_000.0,
    var alertMinMarginPercent: Double = 15.0,
    var alertDisplaySeconds: Int = 6,
    /** Chat line (item, asking price, margin, resell price, clickable "Buy now." -> `/ah <seller>`) for newly-found Auction House flips — gated by the same [alertMinProfit]/[alertMinMarginPercent] thresholds as the toast. Off by default since it's more intrusive than the toast. */
    var chatNotifyEnabled: Boolean = false
)
