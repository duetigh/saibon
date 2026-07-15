package dev.saibon.data

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Data" settings section. Called once from `SaibonClient.onInitializeClient()`. */
object DataSettings {
    private val REFRESH_INTERVALS = listOf(60, 180, 360, 720, 1440)

    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.DATA, "Data Repository") {
                val config = Saibon.config.data.dataRepo

                toggle("Auto-refresh game data", config.autoRefresh) {
                    config.autoRefresh = it
                    Saibon.config.save()
                }
                dropdown(
                    "Refresh interval",
                    REFRESH_INTERVALS,
                    config.refreshIntervalMinutes,
                    { minutes -> if (minutes < 60) "$minutes min" else "${minutes / 60}h" }
                ) {
                    config.refreshIntervalMinutes = it
                    Saibon.config.save()
                }
                button("Refresh data now") {
                    DataRepository.refreshNow()
                }
            }
        )
    }
}
