package dev.saibon.client

import dev.saibon.core.Saibon
import dev.saibon.core.command.CommandRegistry
import dev.saibon.search.SearchSettings
import dev.saibon.ui.overlay.InventorySearchOverlay
import dev.saibon.ui.screen.ChangelogScreen
import dev.saibon.ui.screen.SaibonScreen
import dev.saibon.update.UpdateChecker
import dev.saibon.update.UpdateSettings
import dev.saibon.update.installer.UpdateInstaller
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object SaibonClient : ClientModInitializer {
    override fun onInitializeClient() {
        CommandRegistry.init()
        CommandRegistry.register { dispatcher ->
            dispatcher.register(
                literal("saibon")
                    .executes { openSettings() }
                    .then(
                        literal("updatecheck")
                            .then(literal("changelog").executes { openChangelog() })
                            .then(literal("install").executes { installUpdate() })
                    )
            )
            dispatcher.register(literal("sb").executes { openSettings() })
        }

        UpdateChecker.init()
        UpdateSettings.register()
        InventorySearchOverlay.init()
        SearchSettings.register()

        Saibon.logger.info("Saibon client initialized")
    }

    private fun openSettings(): Int {
        Minecraft.getInstance().setScreenAndShow(SaibonScreen())
        return 1
    }

    private fun openChangelog(): Int {
        val manifest = UpdateChecker.latestManifest
        if (manifest == null) {
            Minecraft.getInstance().player?.sendSystemMessage(Component.literal("No update info yet — try again in a moment."))
            return 0
        }
        Minecraft.getInstance().setScreenAndShow(ChangelogScreen(manifest))
        return 1
    }

    private fun installUpdate(): Int {
        val manifest = UpdateChecker.latestManifest ?: return 0
        UpdateInstaller.install(manifest)
        return 1
    }
}
