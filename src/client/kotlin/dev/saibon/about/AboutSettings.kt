package dev.saibon.about

import dev.saibon.client.chat.SaibonChat
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.screen.ChangelogScreen
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection
import dev.saibon.update.UpdateChecker
import net.minecraft.client.Minecraft

/** Fills the previously-empty [SaibonCategory.ABOUT] tab. Called once from `SaibonClient.onInitializeClient()`. */
object AboutSettings {
    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.ABOUT, "About") {
                label("Saibon v${UpdateChecker.currentVersion()}")
                button("Check for updates now") {
                    UpdateChecker.checkNow(force = true)
                }
                button("View changelog") {
                    val manifest = UpdateChecker.latestManifest
                    if (manifest == null) {
                        Minecraft.getInstance().player?.sendSystemMessage(
                            SaibonChat.message("No update info yet — try \"Check for updates now\" first.")
                        )
                    } else {
                        Minecraft.getInstance().setScreenAndShow(ChangelogScreen(manifest))
                    }
                }
            }
        )
    }
}
