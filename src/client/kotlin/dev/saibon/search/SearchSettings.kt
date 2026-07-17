package dev.saibon.search

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Search & Highlight" settings section. Called once from `SaibonClient.onInitializeClient()`. */
object SearchSettings {
    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.FEATURES, "Search & Highlight") {
                val config = Saibon.config.data.search

                toggle("Enable inventory search overlay", { config.enabled }) {
                    config.enabled = it
                    Saibon.config.save()
                }
                colorPicker("Match highlight color", { config.matchColor }) {
                    config.matchColor = it
                    Saibon.config.save()
                }
                colorPicker("Non-match dim color", { config.dimColor }) {
                    config.dimColor = it
                    Saibon.config.save()
                }
            }
        )
    }
}
