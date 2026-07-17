package dev.saibon.market

/** Persisted Bazaar/Auction House price-feed preferences. */
data class MarketConfig(
    var autoRefresh: Boolean = true,
    var refreshIntervalSeconds: Int = 60,
    /** On by default — [dev.saibon.market.flip.CraftVsBinFinder] needs this sweep to find anything at all, and it's the one flip signal that's useful within minutes of a fresh install (no sales-history warm-up). Costs a full lowest-BIN sweep of Hypixel's entire active-auction list (tens of MB) on [ahRefreshIntervalSeconds]. */
    var ahAutoRefresh: Boolean = true,
    var ahRefreshIntervalSeconds: Int = 120,
    /** Multiplier over the known lowest-BIN price above which a real AH listing is flagged as overpriced. */
    var overpayWarningThreshold: Double = 1.5,
    /** Minimum buy-order/sell-offer margin (ask minus bid), as a percent of the bid, to surface as a flip suggestion. */
    var flipMinMarginPercent: Double = 10.0,
    /** Render-only search/highlight overlay on the real Hypixel AH menu — see MarketMenuOverlay. Off by default, same as [ahOverlayPanelEnabled]/[ahRelayoutEnabled] — the AH menu gets no Saibon overlay at all unless explicitly opted into. */
    var menuOverlayEnabled: Boolean = false,
    /** The browse-all-items/category/flip panel drawn beside the real AH menu — see AuctionHouseListingPanel. Independent of [menuOverlayEnabled]/[ahAutoRefresh], which govern the underlying feed and the overpay-badge highlight, not this panel. Off by default — the real AH menu stays a plain highlight/dim overlay unless explicitly opted into. */
    var ahOverlayPanelEnabled: Boolean = false,
    /** Powers `/saibonflips`' Auction House finder's reference-sale-price engine — see AuctionSalesHistoryRepository. On by default so that finder actually produces candidates out of the box; local samples are also seeded from the bundled `fair_prices` server snapshot ([dev.saibon.data.DataRepository.fairPriceSnapshot]) so it isn't starting from zero. */
    var salesHistoryAutoRefresh: Boolean = true,
    var salesHistoryRefreshIntervalSeconds: Int = 60,
    /** Bounded per-item rolling sample ring buffer size — sized to support a real sales/week volume figure ([dev.saibon.market.FairPriceCalculator]), not just a short price window. */
    var salesHistoryMaxSamplesPerItem: Int = 300,
    /** An item needs at least this many recent sales observed before it's ranked as a flip — avoids ranking on a single troll/outlier sale. */
    var salesHistoryMinSamples: Int = 3,
    /** 0 (default) uses AuctionHouseTax's real listing-fee + claim-tax bracket model; set above 0 to override with one flat percentage instead. */
    var ahTaxRatePercent: Double = 0.0,
    /** The category-tabbed browse/flip panel drawn beside the real Bazaar menu — see BazaarMenuOverlay. */
    var bazaarOverlayEnabled: Boolean = true,
    /** Minimum profit percent (vs. cost) for the "instant-buy on the Bazaar, sell to an NPC" flip ranking. */
    var instaBuyToNpcMinMarginPercent: Double = 5.0,
    /** Minimum profit percent (vs. cost) for the "place a buy order, sell to an NPC" flip ranking. */
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
