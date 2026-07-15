package dev.saibon.itemlist

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Item List" settings section. Called once from `SaibonClient.onInitializeClient()`. */
object ItemListSettings {
    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.FEATURES, "Item List") {
                val config = Saibon.config.data.itemList

                toggle("Show item list button in the SkyBlock menu", config.showMenuButton) {
                    config.showMenuButton = it
                    Saibon.config.save()
                }
            }
        )
    }
}
