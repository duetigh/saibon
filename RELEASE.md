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
