package dev.saibon.slayer.voidgloom

import com.mojang.brigadier.arguments.StringArgumentType
import dev.saibon.client.chat.SaibonChat
import dev.saibon.core.Saibon
import dev.saibon.core.command.CommandRegistry
import dev.saibon.slayer.TrackedPlayerState
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.minecraft.client.Minecraft

/**
 * `/saibonvoidgloom` (no args) opens [VoidgloomTrackerScreen], the GUI half of the boss-owner
 * kill tracker. `track`/`untrack`/`list` are the chat-command equivalent of the same
 * add/remove/view actions, for anyone who'd rather not open a screen. Both manage the same
 * list consumed by [VoidgloomBossOwnerTracker]. `/saibonvoidgloomscan` is a one-time authoring
 * aid — stand at a Voidgloom arena zone boundary, run it with a label, and use the printed
 * coordinates to fill in [VoidgloomZones]'s placeholder bounding boxes; it isn't needed once
 * those are set.
 */
object VoidgloomCommands {
    fun register() {
        CommandRegistry.register { dispatcher ->
            dispatcher.register(
                literal("saibonvoidgloom")
                    .executes { guardedScreen("open the Voidgloom boss tracker") { openScreen() } }
                    .then(
                        literal("track")
                            .then(argument("name", StringArgumentType.word()).executes { ctx ->
                                guarded("track a Voidgloom boss owner") { track(StringArgumentType.getString(ctx, "name")) }
                            })
                    )
                    .then(
                        literal("untrack")
                            .then(argument("name", StringArgumentType.word()).executes { ctx ->
                                guarded("untrack a Voidgloom boss owner") { untrack(StringArgumentType.getString(ctx, "name")) }
                            })
                    )
                    .then(literal("list").executes { guarded("list tracked Voidgloom boss owners") { list() } })
            )
        }
        CommandRegistry.register { dispatcher ->
            dispatcher.register(
                literal("saibonvoidgloomscan")
                    .then(argument("label", StringArgumentType.greedyString()).executes { ctx ->
                        guarded("scan the current position") { scan(StringArgumentType.getString(ctx, "label")) }
                    })
            )
        }
    }

    private fun openScreen() {
        Minecraft.getInstance().setScreenAndShow(VoidgloomTrackerScreen())
    }

    private fun track(name: String) {
        val key = name.lowercase()
        if (Saibon.config.data.voidgloom.trackedPlayers.containsKey(key)) {
            sendMessage("$name is already tracked.")
            return
        }
        Saibon.config.data.voidgloom.trackedPlayers[key] = TrackedPlayerState(name)
        Saibon.config.save()
        sendMessage("Now tracking $name's Voidgloom Seraph kills.")
    }

    private fun untrack(name: String) {
        val removed = Saibon.config.data.voidgloom.trackedPlayers.remove(name.lowercase())
        if (removed == null) {
            sendMessage("$name isn't tracked.")
            return
        }
        Saibon.config.save()
        sendMessage("Stopped tracking ${removed.displayName} (had ${removed.kills} kills).")
    }

    private fun list() {
        val tracked = Saibon.config.data.voidgloom.trackedPlayers.values
        if (tracked.isEmpty()) {
            sendMessage("No tracked Voidgloom boss owners.")
            return
        }
        sendMessage(tracked.joinToString(", ") { "${it.displayName}: ${it.kills}" })
    }

    private fun scan(label: String) {
        val pos = Minecraft.getInstance().player?.blockPosition() ?: return
        val line = "[$label] ${pos.x} ${pos.y} ${pos.z}"
        Saibon.logger.info(line)
        sendMessage(line)
    }

    private fun sendMessage(text: String) {
        Minecraft.getInstance().player?.sendSystemMessage(SaibonChat.message(text))
    }

    /** Same catch-and-report shape as `SaibonClient.guarded` — client commands run their `.executes` block synchronously in the chat dispatch path. */
    private fun guarded(action: String, block: () -> Unit): Int {
        return try {
            block()
            1
        } catch (t: Throwable) {
            Saibon.logger.error("Failed to $action", t)
            sendMessage("Couldn't $action — see log for details: ${t.message}")
            0
        }
    }

    /** Same deferred-open shape as `SaibonClient.guardedScreen` — `.executes` for a "/..." command runs inside `ChatScreen.keyPressed`, which unconditionally closes the chat screen right after; opening a screen inline gets immediately stomped by that close call. */
    private fun guardedScreen(action: String, block: () -> Unit): Int {
        Minecraft.getInstance().execute {
            try {
                block()
            } catch (t: Throwable) {
                Saibon.logger.error("Failed to $action", t)
                sendMessage("Couldn't $action — see log for details: ${t.message}")
            }
        }
        return 1
    }
}
