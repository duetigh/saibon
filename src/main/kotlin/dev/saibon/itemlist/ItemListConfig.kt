package dev.saibon.itemlist

/** Persisted Item List preferences. */
data class ItemListConfig(
    var showMenuButton: Boolean = true,
    var sidebarEnabled: Boolean = true,
    var sidebarWidth: Int = 220
)
