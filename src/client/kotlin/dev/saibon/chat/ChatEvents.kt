package dev.saibon.chat

import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import java.util.concurrent.atomic.AtomicBoolean

/** Posted on `Saibon.events` for every chat/action-bar line the client receives, stripped of formatting codes for easy regex matching (`ChatPatternRegistry`) — the raw component is kept too, for features that need real styling/click events. */
data class ChatLineReceived(val raw: String, val stripped: String)

/**
 * First real hook into inbound chat: registers Fabric API's
 * `ClientReceiveMessageEvents.Game`/`.Chat` (confirmed present in the
 * already-depended-on `fabric-message-api-v1` module via `javap`) and posts a
 * [ChatLineReceived] onto `Saibon.events` for every line — the shared feed
 * every future chat-driven feature (farming counter, dungeon parser, key/door
 * counters, calendar announcements, chat tweaks) reads from instead of each
 * registering its own listener. Read-only: this never sends, cancels, or
 * modifies a message.
 */
object ChatEvents {
    private val FORMATTING_CODE = Regex("§.")
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return

        ClientReceiveMessageEvents.GAME.register(
            ClientReceiveMessageEvents.Game { message, _ -> post(message.string) }
        )
        ClientReceiveMessageEvents.CHAT.register(
            ClientReceiveMessageEvents.Chat { message, _, _, _, _ -> post(message.string) }
        )
    }

    private fun post(raw: String) {
        runCatching {
            Saibon.events.post(ChatLineReceived(raw, FORMATTING_CODE.replace(raw, "")))
        }.onFailure { Saibon.logger.warn("Saibon chat event dispatch failed", it) }
    }
}
