package dev.saibon.slayer.voidgloom

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection
import net.minecraft.client.Minecraft

/** Registers the "Voidgloom Seraph Slayer" settings section. Called once from `SaibonClient.onInitializeClient()`. */
object VoidgloomSettings {
    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.FEATURES, "Voidgloom Seraph Slayer") {
                val config = Saibon.config.data.voidgloom

                toggle("Announce miniboss spawn area/coords in party chat", { config.minibossAlertEnabled }) {
                    config.minibossAlertEnabled = it
                    Saibon.config.save()
                }
                toggle("Announce tracked players' boss kills in party chat", { config.bossOwnerTrackerEnabled }) {
                    config.bossOwnerTrackerEnabled = it
                    Saibon.config.save()
                }
                button("Open Boss Tracker (add/remove tracked players)") {
                    Minecraft.getInstance().setScreenAndShow(VoidgloomTrackerScreen())
                }
            }
        )
    }
}
