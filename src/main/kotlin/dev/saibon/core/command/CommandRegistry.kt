package dev.saibon.core.command

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

/**
 * Thin wrapper around Fabric's client command registration event so feature
 * modules register commands in one place rather than each hooking the
 * callback directly.
 */
object CommandRegistry {
    private val registrars = mutableListOf<(CommandDispatcher<FabricClientCommandSource>) -> Unit>()
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            registrars.forEach { it(dispatcher) }
        }
    }

    fun register(registrar: (CommandDispatcher<FabricClientCommandSource>) -> Unit) {
        registrars.add(registrar)
    }
}
