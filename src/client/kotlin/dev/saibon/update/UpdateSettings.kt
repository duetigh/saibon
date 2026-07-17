package dev.saibon.update

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Updates" settings section. Called once from `SaibonClient.onInitializeClient()`. */
object UpdateSettings {
    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.UPDATES, "Updates") {
                val config = Saibon.config.data.update
                toggle("Auto-check for updates", { config.autoCheck }) {
                    config.autoCheck = it
                    Saibon.config.save()
                }
            }
        )
    }
}
