package dev.saibon.chat

import dev.saibon.core.Saibon
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lets feature modules register a `(Regex) -> handler` pair against the
 * stripped chat stream instead of each adding its own
 * `ClientReceiveMessageEvents` listener — no consumers yet this pass (future:
 * farming crop counter, dungeon room/score parser, key/door counters,
 * calendar announcements, chat tweaks), but this is the shared entry point
 * they'll all register through.
 */
object ChatPatternRegistry {
    private data class Entry(val pattern: Regex, val onMatch: (MatchResult) -> Unit)

    private val entries = CopyOnWriteArrayList<Entry>()
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        Saibon.events.subscribe<ChatLineReceived> { onChatLine(it) }
    }

    /** Registers [onMatch] to run whenever a chat line matches [pattern] (searched anywhere in the stripped line, not required to match the whole line). */
    fun register(pattern: Regex, onMatch: (MatchResult) -> Unit) {
        entries += Entry(pattern, onMatch)
    }

    private fun onChatLine(event: ChatLineReceived) {
        for (entry in entries) {
            val match = entry.pattern.find(event.stripped) ?: continue
            runCatching { entry.onMatch(match) }
                .onFailure { Saibon.logger.warn("Saibon chat pattern handler threw, skipping", it) }
        }
    }
}
