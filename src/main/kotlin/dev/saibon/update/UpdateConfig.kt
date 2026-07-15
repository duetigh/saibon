package dev.saibon.update

/**
 * Persisted update-checker preferences.
 * [dismissedVersion] lets a user silence a single release's chat prompt
 * without disabling checking entirely; it's cleared once a newer version
 * than the dismissed one is seen.
 */
data class UpdateConfig(
    var autoCheck: Boolean = true,
    var dismissedVersion: String? = null
)
