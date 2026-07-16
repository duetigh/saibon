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
    var menuOverlayEnabled: Boolean = true,
    /** The browse-all-items/category/flip panel drawn beside the real AH menu — see AuctionHouseListingPanel. Independent of [menuOverlayEnabled]/[ahAutoRefresh], which govern the underlying feed and the overpay-badge highlight, not this panel. */
    var ahOverlayPanelEnabled: Boolean = true,
    /** Powers the `/saibonah` flip finder's reference-sale-price engine — see AuctionSalesHistoryRepository. Off by default, same rationale as [ahAutoRefresh]: not everyone wants the extra polling. */
    var salesHistoryAutoRefresh: Boolean = false,
    var salesHistoryRefreshIntervalSeconds: Int = 300,
    /** Bounded per-item rolling-median ring buffer size. */
    var salesHistoryMaxSamplesPerItem: Int = 50,
    /** An item needs at least this many recent sales observed before it's ranked as a flip — avoids ranking on a single troll/outlier sale. */
    var salesHistoryMinSamples: Int = 3,
    /** User-overridable estimate of Hypixel's AH sale tax — see AuctionHouseTax. */
    var ahTaxRatePercent: Double = 1.5,
    /** The category-tabbed browse/flip panel drawn beside the real Bazaar menu — see BazaarMenuOverlay. */
    var bazaarOverlayEnabled: Boolean = true,
    /** Minimum profit percent (vs. Bazaar buy price) for the "buy on Bazaar, sell to an NPC" flip ranking. */
    var buyOrderToNpcMinMarginPercent: Double = 5.0,
    /** Minimum profit percent (vs. craft cost) for the craft-flip ranking. */
    var craftFlipMinMarginPercent: Double = 10.0,
    /** Logs what the Bazaar action navigator would click instead of actually clicking — see BazaarActionNavigator. Default on; must stay on until verified live against a real Bazaar session. */
    var bazaarActionDryRun: Boolean = true,
    /** A second explicit confirmation before the first synthesized click of any Bazaar buy/sell/order/offer sequence. */
    var bazaarActionConfirmRequired: Boolean = true,
    /** Per-step timeout for the Bazaar action navigator before it aborts a stalled click sequence. */
    var bazaarActionTimeoutMs: Int = 5000,
    /** Relays the real AH page's listings into a sorted/paginated NEU-style grid over the real slots — see AuctionRelayoutPanel. Off by default: unlike the read-only overlays above, clicking a tile issues a real click on the backing real slot. */
    var ahRelayoutEnabled: Boolean = false,
    /** A Confirm/Cancel gate before a relayout tile's click is forwarded to its real slot — see AuctionRelayoutPanel. */
    var ahRelayoutConfirmRequired: Boolean = true
)
