package dev.saibon.update

/**
 * Mirrors the `version.json` asset published by `.github/workflows/release.yml`
 * on every tagged release. Gson-mapped by field name — keep these in sync
 * with the `jq` object built in that workflow.
 */
data class VersionManifest(
    val latestVersion: String = "",
    val minGameVersion: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val changelogUrl: String = "",
    val changelog: String = ""
)
