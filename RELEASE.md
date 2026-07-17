# Release Notes

Style guide for entries in this file (read this before adding a new one):

- One `##` heading per version: `## vX.Y.Z - YYYY-MM-DD`, newest at the top.
- Group changes under `### Added`, `### Changed`, `### Fixed`, `### Policy`
  (only include the subheadings that apply to that release).
- Each bullet is one line, states the user-visible or contributor-visible
  effect first, then the reason if it's not obvious.
- To cut a release: add a new entry here for the version being released, bump
  `mod_version` in `gradle.properties` to match, commit, tag `vX.Y.Z`, and
  push the tag. The `Release` GitHub Actions workflow reads this file's
  section for that tag and publishes it as the GitHub release body, plus
  builds and attaches the jar automatically.

---

## v0.9.1 - 2026-07-16

### Changed
- `ItemIcons.resolvedProfile` now builds its `HashMultimap` with explicit
  `<String, Property>` type arguments instead of relying on an unchecked raw
  type inferred from `PropertyMap`'s constructor; no behavior change.

## v0.9.0 - 2026-07-16

### Added
- `data/items.json` now holds the full ~5,500-item SkyBlock catalog matched
  id-for-id against Hypixel's public `/v2/resources/skyblock/items` API,
  replacing the two-item placeholder seed; new
  `scripts/sync_hypixel_items.py` re-syncs `material`/`color`/`skullTexture`
  against Hypixel's current data. `SkyblockItem` gained a `skullTexture`
  field, and `ItemIcons` now renders `minecraft:player_head` items with
  their real custom Mojang skin texture (via a resolved `GameProfile`, the
  same mechanism vanilla uses for any player head) instead of a default
  Steve head.
- `FlipScreen`'s AH flip detail panel now resolves the winning listing's
  seller UUID to a name (`PlayerNameResolver`, a Mojang session-server
  lookup and deliberate second exception to `PLAN.md`'s outbound-call
  whitelist) and shows an "Open `<seller>`'s AH" button that runs
  `/ah <seller>`, replacing the old "Copy /viewauction command" button.
  `AuctionPrice`/`AuctionEntry`/`FlipCandidate` now carry the seller UUID
  end to end to support this.
- Auction House flip chat notifications (`AhFlipChatNotifier`, off by
  default): posts an item/price/margin line plus a clickable "Buy now."
  link (`/ah <seller>`) for newly-found high-confidence AH flips, gated by
  the same profit/margin thresholds as the existing toast. New "Post
  Auction House flips to chat" toggle in Flip Finder settings.
- `FlipScreen` gained a Sort dropdown (Max profit / Margin % / Cheapest).
- `DropdownWidget` popups now virtualize option lists longer than 10 rows
  behind a scrollbar (mouse-wheel or drag-to-jump) instead of rendering
  every row, and intercept clicks/scroll/drag on the popup
  (`ScreenMouseEvents.allowMouseClick`/`Scroll`/`Drag`) so an open popup
  can no longer leak input through to a widget underneath it at the same
  screen position.

### Changed
- Bazaar price displays (`BazaarMenuOverlay`, `BazaarSearchScreen`)
  collapsed the mislabeled four-line Insta-buy/Buy order/Insta-sell/Sell
  offer breakdown (the middle two always duplicated the outer two) down to
  a plain Buy/Sell pair.
- Panel backgrounds (`Panel.BACKGROUND`/`HOVER_BACKGROUND`/
  `SELECTED_BACKGROUND`/`TOGGLE_ON`/`TOGGLE_OFF`) and
  `SearchToggleWidget`'s fill are now fully opaque instead of translucent,
  so nothing renders through a Saibon panel or dropdown popup.

## v0.8.0 - 2026-07-16

### Added
- HUD engine (`HudEngine`/`HudModule`/`HudEditScreen`, `PLAN.md` Stage 2's "GUI
  Locations" screen): a shared, positionable/scalable in-world overlay
  system — every module registers once and gets a 9-point anchor, drag-to-
  reposition, scale, and enable/disable from one edit screen, instead of
  hand-rolling its own Fabric HUD-layer hook and position math. New "HUD
  Locations" settings section (per-module toggles, "Edit HUD positions"
  button).
- Flip Alert Toast (`FlipAlertHudModule`), the first HUD module: a read-only
  toast shown for a few seconds when a new high-confidence flip crosses
  configurable profit/margin thresholds — never buys, bids, or lists
  anything itself.
- Unified Flip Finder (`/saibonflips`, `FlipEngine`/`FlipScreen`): a single
  background-scanned, multi-strategy flip table combining Auction House,
  Bazaar margin, NPC, and Craft flips (each independently toggleable) behind
  one shared scan interval; `/saibonah` remains as the narrower AH-only
  browser. New "Flip Finder" settings section (per-strategy toggles, scan
  interval, alert thresholds).
- Modifier-aware Auction House flip matching: `AuctionItemDecoder` now
  extracts a stable signature (reforge, hot-potato count, recombobulated,
  dungeon stars, enchants) from each listing's NBT, and
  `AuctionPriceRepository`/`AuctionSalesHistoryRepository` track a parallel
  per-signature price/sales bucket alongside their existing item-id-only
  one — so, once enough matching-modifier sales exist, a Sharpness 7 sword's
  flip price is no longer blended with a bare one (falls back to the
  item-id bucket otherwise).
- Flip candidates backed by a real AH listing now carry that listing's
  auction UUID; `FlipScreen`'s detail panel shows a "Copy /viewauction
  command" button for those instead of requiring the player to find the
  listing manually.
- Chat/scoreboard/tab-list read hooks (`ChatEvents`/`ChatPatternRegistry`,
  `ScoreboardReader`, `TabListReader`): shared, read-only feeds that future
  chat- or HUD-driven features (farming counters, dungeon parsers, key/door
  counters, calendar announcements) can register against instead of each
  hooking its own listener. No consumers yet beyond the flip-alert toast;
  the scoreboard/tab-list line-text parsing is unverified against a live
  Hypixel session.

### Changed
- Config schema bumped to v6, adding nested `hud` and `flip` sub-configs
  (old config files load fine via per-field defaults, no migration needed).

## v0.7.3 - 2026-07-16

### Fixed
- `data/index.json`'s `items` dataset manifest version/sha256 was never
  bumped when v0.7.0 fixed `data/items.json`, so clients with an
  already-cached copy never re-fetched the fix (`DataRepository` only
  re-downloads when the manifest version is higher than what's cached);
  bumped to v4 with the current file's checksum.

### Changed
- `menuOverlayEnabled` (the AH/Bazaar menu search/highlight overlay) is now
  off by default, matching `ahOverlayPanelEnabled`/`ahRelayoutEnabled` — the
  real AH menu gets no Saibon overlay at all out of the box now.

## v0.7.1 - 2026-07-16

### Fixed
- `UpdateInstaller`'s `HttpClient` now follows redirects (`Redirect.NORMAL`);
  GitHub release asset downloads redirect through S3, and the update jar
  previously failed to download since the client wasn't configured to follow
  that redirect.

## v0.7.0 - 2026-07-15

### Added
- Real dropdown widget (`DropdownWidget`) replacing the vanilla `CycleButton`
  wrapper: clicking it opens a popup list of every option (below the button,
  or above it if there isn't room) instead of cycling one value at a time;
  used by every Category/Rarity/View/Sort dropdown across the Item List,
  Auction, and Bazaar screens/overlays.
- Item names carrying Hypixel's `%%color%%` placeholder tokens (seen on some
  data-repo items, e.g. `"%%green%%Ballista Fuel Cell"`) now render as
  actually-colored text instead of the literal token text, via a new
  `ColorCodes` helper.
- `SkyblockItem` gained `soulbound` and `color` fields: items with no
  Bazaar/AH price because they're Soulbound now show "Soulbound (not
  tradeable)" instead of a blank price block, and dungeon leather armor
  (e.g. Necron's set) renders dyed instead of plain brown leather.
- New "Flips: Insta-buy -> NPC" Bazaar ranking (`instaBuyNpcFlips`) alongside
  the existing buy-order variant, exposed as a new view in
  `BazaarMenuOverlay`; new `instaBuyToNpcMinMarginPercent` setting.
- Settings screen (`/saibon`) content area now scrolls with the mouse wheel
  instead of overflowing off-screen when a category has more entries than
  fit in the window.

### Changed
- Bazaar price displays across the Item List, `BazaarSearchScreen`, and
  `BazaarMenuOverlay` now distinguish all four Bazaar actions (insta-buy, buy
  order, insta-sell, sell offer) instead of a mislabeled buy/sell pair, and
  show "N/A" for a missing price instead of silently treating it as zero.
- Grid layouts (Item List, Auction Flip Finder, AH/Bazaar overlay panels) now
  fit one more column where it belongs — the column-count formula was
  discounting a trailing gap that isn't actually needed after the last
  column.
- Inventory search overlay's typed query no longer filters/highlights slots
  by default (`filterEnabled` starts off); double-click still toggles it on.
- Auction House browse/flip panel (`ahOverlayPanelEnabled`) is now off by
  default, matching the AH relayout panel's opt-in default — the real AH menu
  keeps just the plain highlight/dim overlay unless explicitly enabled.

### Fixed
- `BazaarFlipRanking`'s buy-order/sell-offer margin was computed backwards
  (bid minus ask), which is never profitable since the ask always sits above
  the bid; it's now ask minus bid, matching the actual spread.
- Settings screen title/placeholder text colors were missing their alpha
  channel, rendering them nearly transparent; both now draw fully opaque.
- `data/items.json`: Necron's Boots/Chestplate/Leggings/Helmet now carry a
  `color` so they render dyed instead of plain leather; Necron's Helmet's
  `material` was `minecraft:paper` (the wrong item entirely) and is now
  `minecraft:leather_helmet`; Toxic Arrow Poison's `material` corrected from
  `minecraft:ink_sac` to `minecraft:green_dye`.

## v0.6.0 - 2026-07-15

### Added
- Auction House relayout (`NEU_FEATURE_PARITY.md` #2, opt-in, **off by
  default**): `AuctionRelayoutPanel` redraws the current AH page's listings
  as a sorted (price asc/desc, name), searchable NEU-style tile grid drawn
  over the real menu. Every tile stays backed by its real, currently-open
  `Slot` — `Slot.x`/`Slot.y` are immutable in this MC build so listings
  can't actually move, the grid just draws over them at new coordinates and
  maps clicks back. Clicking a tile doesn't fire immediately: it goes
  through a Confirm/Cancel gate (on by default) before issuing the same
  `invokeSlotClicked` call path a genuine click would take, so Hypixel's own
  server-side purchase confirmation still runs as the backstop. New toggles
  in Auction House Overlay settings (enable relayout, require confirm).
- Inventory search overlay: double-clicking the expanded search box toggles
  whether the current query filters/highlights slots, independent of typing
  — lets a query (e.g. scratch math) be typed without dimming the inventory,
  while a single click still just focuses the box.

### Changed
- Data repository, self-update manifest, and mod contact link now point at
  `duetigh/saibon` instead of `JamesWLyon/saibon` (maintainer's GitHub
  account was renamed); `data/index.json`'s `items` dataset bumped to
  version 3.
- Panel theme switched from the gold/amber NEU-style accent to a neutral
  gray/white scheme (`Panel.BACKGROUND`/`ACCENT`/etc.).
- `RarityColors.of` now normalizes underscore-separated tiers (`VERY_SPECIAL`,
  as returned by the Hypixel API via the data repo) to the space-separated
  form its color map already used for lore-parsed tiers; added `SUPREME` and
  `UNOBTAINABLE` entries.
- Update checker no longer re-announces "Mod loaded"/re-checks on every
  Hypixel server switch (hub ↔ SkyBlock island ↔ dungeon instance ↔ `/warp`),
  which each resend a join packet without closing the connection — it now
  only fires once per actual connection, gated on a new `DISCONNECT` listener.
- `MarketMenuOverlay`, `BazaarMenuOverlay`, `AuctionHouseListingPanel`, and
  `ItemListSidebarPanel` now draw their background panel in a
  `beforeExtract` pass instead of inline at the top of `render`, fixing the
  panel background painting over content drawn earlier in the same frame by
  sibling overlays sharing a screen.

### Fixed
- Inventory search overlay's search box now takes focus via
  `Screen.setFocused` instead of `EditBox.setFocused(true)`, fixing the
  vanilla screen not recognizing the box as focused for keyboard routing.

### Policy
- The AH relayout tile-click path follows the same rule as the existing
  Bazaar action-navigation feature (`PLAN.md`): it synthesizes a real click
  only from a direct, in-the-moment player action, and stops at the same
  server-round-trip a genuine click would trigger rather than chaining
  further clicks itself. Unverified against a live server, so it ships off
  by default with confirm-required also on by default.

## v0.5.0 - 2026-07-15

### Added
- Auction Flip Finder (`NEU_FEATURE_PARITY.md` #2): `/saibonah` now opens a
  flip-ranked grid instead of the plain price browser, backed by a new sold-
  auctions feed (`AuctionSalesHistoryRepository`, off by default) that tracks
  a rolling median sale price per item and ranks items by
  `median sale − AH tax − lowest active BIN`. Bazaar gained two more flip
  rankings: buy/sell margin (moved from the old `BazaarSearchScreen` sort)
  and "buy on Bazaar, instant-sell to an NPC vendor"; plus a "craft cheaper
  than it sells" ranking that recurses into sub-recipes when an ingredient
  has no direct market price. New "Auction Flip Finder" settings section
  (sample-size/margin thresholds, AH tax-rate estimate).
- `BazaarMenuOverlay`: a category-tabbed browse/flip panel drawn beside the
  real Bazaar menu (split out of the old combined `MarketMenuOverlay`), with
  its own "Bazaar Overlay" settings section. `AuctionHouseListingPanel` adds
  the equivalent read-only browse panel beside the real AH menu.
- Opt-in, **dry-run-by-default** semi-automated Bazaar navigation: clicking
  Buy/Sell/Order/Offer on the overlay (after a confirm prompt) drives
  synthesized clicks through the Bazaar's category → product → action-menu
  screens, verifying the screen title/slot text at every step and aborting on
  any mismatch or timeout. It deliberately stops before the final
  money-committing click — quantity/price selection is always a real manual
  click by the player. Ships with dry-run (logs the intended clicks to chat
  instead of sending them) and a second explicit confirmation both on by
  default; new "Auction House Overlay" and "Bazaar Overlay" settings expose
  the toggles.
- Item List sidebar (`NEU_FEATURE_PARITY.md` #1): a narrower grid/search/
  filter companion panel now docks onto the player's own inventory screen on
  Hypixel, so items can be looked up without leaving the inventory; picking a
  tile or its expand button opens the full Item List screen, which gained a
  "Minimize" button to return to the inventory view. New sidebar
  toggle/width slider in Item List settings (on by default).
- Calculator (`NEU_FEATURE_PARITY.md` #9, first slice): typing a pure
  arithmetic expression (`+ - * / % ^ ()`) into the inventory search overlay
  shows the result as suggestion text instead of filtering the inventory.
- "About" settings tab: current version, a "Check for updates now" button,
  and a "View changelog" button, replacing the previously empty tab.
- `SearchEditBox`, a text field that swallows all keypresses while focused,
  fixing letter/number keys (e.g. e/q/1-9) leaking through to vanilla
  container hotkeys while typing in any Saibon search box.

### Changed
- `AuctionSearchScreen` removed; its category/price browsing moved into
  `AuctionHouseListingPanel` and its flip-ranked view into `AuctionFlipScreen`.
- `MarketMenuOverlay` is now AH-only; Bazaar's search/highlight/overpay
  overlay logic moved to the new `BazaarMenuOverlay`.

### Policy
- The Bazaar action-navigation feature synthesizes multiple clicks from a
  single confirmed player action and stops short of the purchase-committing
  click, in line with `PLAN.md`'s "no feature acts without a direct,
  in-the-moment user action" rule; it is unverified against a live server, so
  it ships behind dry-run + confirm-required defaults until confirmed safe.

## v0.4.0 - 2026-07-15

### Added
- Item List GUI (`NEU_FEATURE_PARITY.md` #1): `/saibonitems` (`/sbi`) and a new
  "Items" button injected above the SkyBlock hotbar-menu chest GUI open a
  searchable, filterable grid of every data-repo item. Search reuses the
  existing query parser (bare terms plus `rarity:`/`category:`/`name:`/`id:`
  fields); selecting an item shows NPC sell price, live Bazaar buy/sell +
  flip margin, AH lowest-BIN, a wiki link, a recipe panel (cycling through
  multiple recipes per item, e.g. forge stages), an ingredient grid, a
  reverse "used in" lookup, and back/forward navigation history.
- Market Prices (`NEU_FEATURE_PARITY.md` #2): a Bazaar price poller and an
  opt-in (off by default) Auction House sweeper track Bazaar buy/sell and AH
  lowest-BIN per item. `/saibonah` and `/saibonbz` open read-only
  price-browser grids (same tile/detail layout as the Item List) with sort
  orders and `minprice:`/`minmargin:` query fields. A render-only overlay on
  the real Hypixel AH/Bazaar screens dims non-matching slots, outlines
  matches/the cheapest match, and flags AH listings priced above a
  configurable multiple of the known lowest BIN.
- New "Item List" and "Market Prices" / "Auction House Prices" settings
  sections (refresh toggles/intervals, flip-margin slider, overpay-warning
  threshold slider, overlay toggle, SkyBlock-menu button toggle).
- `SkyblockItem` gained a `material` field (vanilla `namespace:path` icon id)
  so item tiles render a real `ItemStack` icon tinted/outlined by rarity;
  `data/items.json` seed entries and the `items` dataset manifest
  (`data/index.json`) were updated accordingly.
- `DataRepository.recipeFor(id): Recipe?` replaced with
  `recipesFor(id): List<Recipe>` (grouped instead of 1:1) plus a new
  `allRecipes()`, so items with multiple recipes (e.g. forge stages) can be
  cycled through instead of only ever showing one.

### Changed
- Chat messages now go through a shared `SaibonChat` helper for the
  `[Saibon] ` prefix (with a per-letter red gradient on "Saibon") instead of
  each caller building its own `Component.literal("[Saibon] ...")`.
- Config schema bumped to v5, adding nested `itemList` and `market`
  sub-configs (old config files load fine via per-field defaults, no
  migration needed).

## v0.3.0 - 2026-07-15

### Added
- Data repository (`DataRepository`, PLAN.md `saibon-data`, Stage 4): fetches
  a versioned JSON manifest (`data/index.json`) from the Saibon GitHub repo
  and downloads/caches per-dataset files (currently `items`, `recipes`),
  verifying each against its manifest sha256 before applying it. Cached data
  loads from disk on startup and refreshes on a timer, decoupled from the
  jar-update cycle so game data can update without a mod restart.
- New "Data Repository" settings section: toggle auto-refresh, pick the
  refresh interval, and a "Refresh data now" button to force an immediate
  fetch.
- Generic settings-widget framework gained a `button()` entry type, backing
  the new "Refresh data now" control.

### Changed
- Inventory search overlay's collapsed tab now expands into the query box on
  a single click instead of requiring a double-click.
- Config schema bumped to v3, adding a nested `dataRepo` sub-config (old
  config files load fine via per-field defaults, no migration needed).

## v0.2.1 - 2026-07-15

### Added
- Shared beveled-panel style (`Panel`) giving custom screens and widgets a
  consistent NEU/inventory-GUI look, replacing the flat single-color
  fills/outlines each one drew separately.
- Settings sidebar now shows item-icon tiles per category
  (`CategoryTileWidget`: compass/painting/redstone torch/chest/potion/book)
  instead of plain text buttons.
- `/saibon` and `/sb` subcommands now catch and log exceptions instead of
  letting them leak a raw crash-report fragment into chat; the player sees a
  plain "[Saibon] Couldn't ... — see log for details" message instead.

### Changed
- Toggle settings render as a custom green/red ON/OFF widget instead of
  vanilla's on/off `CycleButton`.
- Update checker now runs off world/server join
  (`ClientPlayConnectionEvents.JOIN`) instead of the client-started lifecycle
  event, since the latter fired before a player existed to receive the
  "Mod loaded" / update chat messages.
- Update-available chat prompt is now two plain lines ("New version found!
  (x → y)" plus a single "Click to download." link) instead of one line with
  separate View Changelog / Update Now links.
- Opening the settings screen or changelog from `/saibon` is now deferred to
  the next client tick, since `ChatScreen`'s Enter-key handler was closing
  the screen the same tick it opened, causing a visible flash.

### Fixed
- Removed a redundant background draw in `ChangelogScreen` and
  `ColorPickerScreen` that was rendering the screen background twice.

## v0.2.0 - 2026-07-15

### Added
- Inventory search overlay: a small "Search" tab docked below your inventory,
  storage, Auction House, and Bazaar screens on Hypixel — double-click to
  expand into a query box that live-highlights matching item slots (green
  outline) and dims non-matches as you type.
- Search query syntax: bare substring terms, `field:value` filters
  (`enchant`/`ench`, `rarity`, `reforge`, `type`, `stars`/`star`, `name`),
  boolean `&` (implicit between adjacent terms), `|`, `!`, parentheses, and
  quoted `"multi word"` values; invalid input gracefully falls back to a
  plain substring search.
- New "Search & Highlight" settings section (toggle overlay on/off, pick
  match-highlight and non-match-dim colors).
- Self-update system: on game start (if auto-check is enabled), Saibon
  fetches the latest release's version manifest from GitHub and, if a newer
  version is found, posts a one-time chat message with clickable "View
  Changelog" and "Update Now" links (silenced per-version once
  dismissed/updated).
- "View Changelog" opens an in-game changelog screen showing the release
  notes; "Update Now" downloads the release jar, verifies its SHA-256, and
  stages it — a detached helper process swaps it into place after Minecraft
  fully closes, so nothing installs without the user clicking Update.
- New "Updates" settings section (toggle auto-check for updates).
- Generic settings-widget framework backing both new sections: toggle,
  dropdown, slider, text field, keybind capture, and a color-picker swatch
  that opens a dedicated RGBA slider + hex-field popup screen.
- The mod settings menu now renders registered settings sections per
  category instead of a placeholder, and its search box filters both
  sidebar categories and individual setting labels.
- Release workflow now also builds and publishes a `version.json` manifest
  (version, min game version, download URL, SHA-256, changelog) alongside
  the jar, consumed by the new self-update system.

### Changed
- Config schema bumped to v2, adding nested `update` and `search`
  sub-configs (old config files load fine via per-field defaults, no
  migration needed); removed the placeholder `exampleValue` field.

## v0.1.2 - 2026-07-14

### Fixed
- Fixed a crash on launch (`IllegalAccessException` on Saibon's private
  constructor). Saibon's entrypoints are Kotlin `object` singletons, which
  compile to a private constructor; `fabric.mod.json` now declares them with
  `"adapter": "kotlin"` so Fabric Loader uses the Kotlin language adapter
  instead of defaulting to the Java one.

## v0.1.1 - 2026-07-14

### Changed
- Widened the supported Minecraft version range from a fixed `~26.2` to
  `>=26.1 <26.3`, so Saibon now loads on 26.1.x clients (e.g. 26.1.2) in
  addition to 26.2. 26.1+ all share the same unobfuscated API surface, so no
  code changes were needed beyond the `fabric.mod.json` dependency range.

### Policy
- Replaced the "single version at a time, ported forward" policy with a
  documented supported-range policy: floor stays at 26.1 (start of the
  unobfuscated era), ceiling is capped to versions actually verified and
  bumped forward only after a new drop is confirmed working. See `PLAN.md`.

## v0.1.0 - 2026-07-14

### Added
- Initial Stage 1 core skeleton: mod bootstrap/entrypoints, internal event
  bus, JSON config persistence engine under `config/saibon/`, and the
  `/saibon` (`/sb`) command opening an empty categorized settings GUI shell.
- Gradle/Fabric Loom project scaffold targeting Minecraft 26.2, Fabric Loader
  0.19.x, Fabric API, Fabric Language Kotlin, Java 25 toolchain.
- CI build workflow and tag-triggered release workflow.
