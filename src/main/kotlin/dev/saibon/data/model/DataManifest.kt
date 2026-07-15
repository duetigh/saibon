package dev.saibon.data.model

/**
 * One dataset listed in the manifest: [version] is compared against the
 * locally cached version to decide whether to re-download, and [sha256]
 * is verified against the downloaded body before it's trusted, mirroring
 * how [dev.saibon.update.VersionManifest]'s jar download is checksummed.
 */
data class DatasetEntry(
    val version: Int = 0,
    val url: String = "",
    val sha256: String = ""
)

/**
 * Mirrors `data/index.json` in the Saibon GitHub repo, fetched independently
 * of jar releases so game data can refresh without a restart (PLAN.md
 * `saibon-data`). Adding a new dataset (e.g. `npc_prices`, `dungeon_answers`)
 * later is just adding an entry here plus a case in
 * [dev.saibon.data.DataRepository]'s `applyDataset` — no schema change.
 */
data class DataManifest(
    val manifestVersion: Int = 1,
    val datasets: Map<String, DatasetEntry> = emptyMap()
)
