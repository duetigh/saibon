package dev.saibon.data

/** Persisted data-repository preferences. */
data class DataConfig(
    var autoRefresh: Boolean = true,
    var refreshIntervalMinutes: Int = 360
)
