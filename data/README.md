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
still holds a two-item placeholder seed and needs real, sourced recipe data.

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
