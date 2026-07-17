# Saibon data repo

Fetched by [`DataRepository`](../src/client/kotlin/dev/saibon/data/DataRepository.kt)
via `raw.githubusercontent.com` off the `master` branch — decoupled from the
jar-release cycle so data can refresh without a mod update, per
[`PLAN.md`](../docs/planning/PLAN.md)'s `saibon-data` module.

`index.json` is the manifest: it lists each dataset's current `version` and a
`sha256` of its file, both checked by `DataRepository` before a downloaded
file is trusted or applied.

`items.json` holds the full ~5,500-item SkyBlock catalog, id-for-id matched
against Hypixel's public `/v2/resources/skyblock/items` API. `recipes.json`
holds ~1,900 crafting-table recipes sourced from
[NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)
(the community-standard source — Hypixel's own API doesn't expose recipes at
all) via [`scripts/sync_neu_recipes.py`](../scripts/sync_neu_recipes.py),
plus ~100 Forge recipes scraped from the
[SkyBlock Fandom wiki's Forge table](https://hypixel-skyblock.fandom.com/wiki/The_Forge)
via [`scripts/sync_forge_recipes.py`](../scripts/sync_forge_recipes.py) — the
only place Forge recipes are tracked in any structured form; NEU-REPO only
has a freeform hint like `"Requires: HotM 2"` for those. Both scripts write
intermediate `recipes.craft.json`/`recipes.forge.json` files that
[`scripts/merge_recipes.py`](../scripts/merge_recipes.py) combines into
`recipes.json` — see each script's docstring for regeneration instructions.
Pets and a handful of forge-only accessories aren't in `items.json` yet, so
their recipes are dropped rather than referencing an id nothing can resolve;
each sync script logs what it skipped to stderr.

Each `items.json` entry's `material` field is a `namespace:path` vanilla
item id (e.g. `"minecraft:wheat"`) used to render that item's grid icon.
`color` (leather-armor dye RGB) and `skullTexture` (base64 Mojang skin blob
for `minecraft:player_head` items) are populated from the same Hypixel API
and let leather armor and custom heads render their real SkyBlock look
without any Hypixel resource-pack assets — see
[`scripts/sync_hypixel_items.py`](../scripts/sync_hypixel_items.py) to
re-sync `material`/`color`/`skullTexture` against Hypixel's current data.
`recipes.json` entries are grouped by `itemId`, so an item with more than
one crafting/forge/NPC recipe just needs more than one entry sharing that
`itemId`.

To publish a change to a dataset: edit the file, bump its `version` in
`index.json`, and recompute its `sha256`:

```sh
sha256sum data/items.json
```
