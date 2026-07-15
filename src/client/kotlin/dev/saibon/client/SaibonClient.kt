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

    private fun openSettings(): Int = guardedScreen("open the settings screen") {
        Minecraft.getInstance().setScreenAndShow(SaibonScreen())
    }

    private fun openChangelog(): Int = guardedScreen("open the changelog") {
        val manifest = UpdateChecker.latestManifest
        if (manifest == null) {
            Minecraft.getInstance().player?.sendSystemMessage(Component.literal("No update info yet — try again in a moment."))
            return@guardedScreen
        }
        Minecraft.getInstance().setScreenAndShow(ChangelogScreen(manifest))
    }

    private fun installUpdate(): Int = guarded("install the update") {
        val manifest = UpdateChecker.latestManifest ?: return@guarded
        UpdateInstaller.install(manifest)
    }

    /**
     * `.executes` for "/saibon"/"/sb" runs synchronously inside
     * `ChatScreen.keyPressed` for the Enter key — and that method
     * unconditionally closes the chat screen (`setScreen(null)`) right
     * after dispatching the command. Opening our own screen inline gets
     * immediately stomped by that close call (the settings screen flashes
     * open and shuts again), so defer it to the next queued client task,
     * which runs after the current key-press handling has finished.
     */
    private fun guardedScreen(action: String, block: () -> Unit): Int {
        Minecraft.getInstance().execute {
            try {
                block()
            } catch (t: Throwable) {
                Saibon.logger.error("Failed to $action", t)
                Minecraft.getInstance().player?.sendSystemMessage(
                    Component.literal("[Saibon] Couldn't $action — see log for details: ${t.message}")
                )
            }
        }
        return 1
    }

    /**
     * Client commands run their `.executes` block synchronously in the chat
     * dispatch path — an uncaught exception there otherwise surfaces to the
     * player as a bare, confusing fragment of the internal crash-report
     * description rather than a real explanation. Catch it here, log the
     * full trace, and tell the player plainly.
     */
    private fun guarded(action: String, block: () -> Unit): Int {
        return try {
            block()
            1
        } catch (t: Throwable) {
            Saibon.logger.error("Failed to $action", t)
            Minecraft.getInstance().player?.sendSystemMessage(
                Component.literal("[Saibon] Couldn't $action — see log for details: ${t.message}")
            )
            0
        }
    }
}
