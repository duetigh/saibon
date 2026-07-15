package dev.saibon.client

import dev.saibon.core.Saibon
import dev.saibon.core.command.CommandRegistry
import dev.saibon.ui.screen.SaibonScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.minecraft.client.Minecraft

object SaibonClient : ClientModInitializer {
    override fun onInitializeClient() {
        CommandRegistry.init()
        CommandRegistry.register { dispatcher ->
            dispatcher.register(literal("saibon").executes { openSettings() })
            dispatcher.register(literal("sb").executes { openSettings() })
        }

        Saibon.logger.info("Saibon client initialized")
    }

    private fun openSettings(): Int {
        Minecraft.getInstance().setScreenAndShow(SaibonScreen())
        return 1
    }
}
