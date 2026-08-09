package dev.saibon.slayer

/** One tracked player's running Voidgloom Seraph boss-kill tally, reported to party chat each time their boss dies. */
data class TrackedPlayerState(
    var displayName: String,
    var kills: Int = 0
)

/** Persisted Voidgloom Seraph slayer-helper state, keyed by lowercased username for case-insensitive add/remove/lookup. */
data class VoidgloomConfig(
    var minibossAlertEnabled: Boolean = true,
    var bossOwnerTrackerEnabled: Boolean = true,
    var trackedPlayers: MutableMap<String, TrackedPlayerState> = mutableMapOf()
)
