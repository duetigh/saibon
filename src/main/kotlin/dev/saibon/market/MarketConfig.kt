package dev.saibon.market

/** Persisted Bazaar/Auction House price-feed preferences. */
data class MarketConfig(
    var autoRefresh: Boolean = true,
    var refreshIntervalSeconds: Int = 60,
    /** Off by default — a full lowest-BIN sweep pages through Hypixel's entire active-auction list (tens of MB). */
    var ahAutoRefresh: Boolean = false,
    var ahRefreshIntervalSeconds: Int = 600,
    /** Multiplier over the known lowest-BIN price above which a real AH listing is flagged as overpriced. */
    var overpayWarningThreshold: Double = 1.5,
    /** Minimum Bazaar sell-buy spread, as a percent of buy price, to surface as a flip suggestion. */
    var flipMinMarginPercent: Double = 10.0,
    /** Render-only search/highlight overlay on the real Hypixel AH/Bazaar menus — see MarketMenuOverlay. */
    var menuOverlayEnabled: Boolean = true
)
