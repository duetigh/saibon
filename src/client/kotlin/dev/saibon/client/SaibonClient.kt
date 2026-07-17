package dev.saibon.client

import dev.saibon.about.AboutSettings
import dev.saibon.chat.ChatEvents
import dev.saibon.chat.ChatPatternRegistry
import dev.saibon.client.chat.SaibonChat
import dev.saibon.core.Saibon
import dev.saibon.core.command.CommandRegistry
import dev.saibon.data.DataRepository
import dev.saibon.data.DataSettings
import dev.saibon.hud.HudEngine
import dev.saibon.hud.HudSettings
import dev.saibon.hud.ScoreboardReader
import dev.saibon.hud.TabListReader
import dev.saibon.hud.modules.EstimatedValueHudModule
import dev.saibon.hud.modules.FlipAlertHudModule
import dev.saibon.itemlist.ItemListMenuButton
import dev.saibon.itemlist.ItemListScreen
import dev.saibon.itemlist.ItemListSettings
import dev.saibon.itemlist.ItemListSidebarOverlay
import dev.saibon.market.AuctionMenuSettings
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.AuctionSalesHistoryRepository
import dev.saibon.market.BazaarMenuSettings
import dev.saibon.market.FlipFinderSettings
import dev.saibon.market.MarketPriceRepository
import dev.saibon.market.MarketSettings
import dev.saibon.market.flip.AhFlipChatNotifier
import dev.saibon.market.flip.FlipEngine
import dev.saibon.market.flip.FlipEngineSettings
import dev.saibon.market.ui.BazaarActionNavigator
import dev.saibon.market.ui.BazaarMenuOverlay
import dev.saibon.market.ui.FlipScreen
import dev.saibon.market.ui.MarketMenuOverlay
import dev.saibon.market.value.EstimatedValueTooltipAppender
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
        CommandRegistry.register { dispatcher ->
            dispatcher.register(literal("saibonitems").executes { openItemList() })
            dispatcher.register(literal("sbi").executes { openItemList() })
        }
        CommandRegistry.register { dispatcher ->
            dispatcher.register(literal("saibonflips").executes { openFlipScreen() })
        }

        UpdateChecker.init()
        UpdateSettings.register()
        HudEngine.init()
        ChatEvents.init()
        ChatPatternRegistry.init()
        ScoreboardReader.init()
        TabListReader.init()
        InventorySearchOverlay.init()
        SearchSettings.register()
        DataRepository.init()
        DataSettings.register()
        ItemListSettings.register()
        ItemListMenuButton.init()
        ItemListSidebarOverlay.init()
        MarketPriceRepository.init()
        AuctionPriceRepository.init()
        AuctionSalesHistoryRepository.init()
        MarketMenuOverlay.init()
        BazaarActionNavigator.init()
        BazaarMenuOverlay.init()
        FlipEngine.init()
        FlipAlertHudModule.init()
        AhFlipChatNotifier.init()
        HudEngine.register(FlipAlertHudModule)
        EstimatedValueTooltipAppender.init()
        HudEngine.register(EstimatedValueHudModule)
        MarketSettings.register()
        AuctionMenuSettings.register()
        FlipFinderSettings.register()
        FlipEngineSettings.register()
        BazaarMenuSettings.register()
        AboutSettings.register()
        HudSettings.register()

        Saibon.logger.info("Saibon client initialized")
    }

    private fun openSettings(): Int = guardedScreen("open the settings screen") {
        Minecraft.getInstance().setScreenAndShow(SaibonScreen())
    }

    private fun openItemList(): Int = guardedScreen("open the item list") {
        Minecraft.getInstance().setScreenAndShow(ItemListScreen())
    }

    private fun openFlipScreen(): Int = guardedScreen("open the flip finder") {
        Minecraft.getInstance().setScreenAndShow(FlipScreen())
    }

    private fun openChangelog(): Int = guardedScreen("open the changelog") {
        val manifest = UpdateChecker.latestManifest
        if (manifest == null) {
            Minecraft.getInstance().player?.sendSystemMessage(SaibonChat.message("No update info yet — try again in a moment."))
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
                    SaibonChat.message("Couldn't $action — see log for details: ${t.message}")
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
                SaibonChat.message("Couldn't $action — see log for details: ${t.message}")
            )
            0
        }
    }
}
