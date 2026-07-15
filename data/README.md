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
cache/checksum/version pipeline end-to-end. Replace with real, sourced data
before building the Item List feature
([`NEU_FEATURE_PARITY.md`](../docs/planning/NEU_FEATURE_PARITY.md) #1).

To publish a change to a dataset: edit the file, bump its `version` in
`index.json`, and recompute its `sha256`:

```sh
sha256sum data/items.json
```
