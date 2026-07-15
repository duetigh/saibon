package dev.saibon.core.config

/**
 * Root config document persisted to config/saibon/config.json.
 * [schemaVersion] exists from day one so later stages can migrate old files
 * instead of resetting them.
 */
data class SaibonConfig(
    var schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    var exampleValue: String = "hello-saibon"
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
