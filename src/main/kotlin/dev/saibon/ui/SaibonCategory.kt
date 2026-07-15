package dev.saibon.ui

/**
 * Settings screen sidebar categories. Stage 1 has no settings to put behind
 * any of these yet; they exist so the sidebar/search shell has real content
 * to lay out, filter, and select against.
 */
enum class SaibonCategory(val displayName: String) {
    GENERAL("General"),
    HUD("HUD"),
    FEATURES("Features"),
    DATA("Data"),
    UPDATES("Updates"),
    ABOUT("About")
}
