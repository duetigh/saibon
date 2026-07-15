# Saibon data repo

Fetched by [`DataRepository`](../src/client/kotlin/dev/saibon/data/DataRepository.kt)
via `raw.githubusercontent.com` off the `master` branch — decoupled from the
jar-release cycle so data can refresh without a mod update, per
[`PLAN.md`](../docs/planning/PLAN.md)'s `saibon-data` module.

`index.json` is the manifest: it lists each dataset's current `version` and a
`sha256` of its file, both checked by `DataRepository` before a downloaded
file is trusted or applied.

**`items.json`/`recipes.json` right now hold a two-item placeholder seed**,
not a sourced SkyBlock price/recipe feed — they exist to exercise the fetch/
cache/checksum/version pipeline end-to-end. The Item List GUI
([`NEU_FEATURE_PARITY.md`](../docs/planning/NEU_FEATURE_PARITY.md) #1) is
built against this schema and works correctly with this placeholder data,
but needs real, sourced items/recipes to be useful in practice — that
population is still outstanding.

Each `items.json` entry's `material` field is a `namespace:path` vanilla
item id (e.g. `"minecraft:wheat"`) used to render that item's grid icon;
`recipes.json` entries are grouped by `itemId`, so an item with more than
one crafting/forge/NPC recipe just needs more than one entry sharing that
`itemId`.

To publish a change to a dataset: edit the file, bump its `version` in
`index.json`, and recompute its `sha256`:

```sh
sha256sum data/items.json
```
