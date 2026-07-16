package dev.saibon.hud

import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.Scoreboard
import java.util.concurrent.atomic.AtomicBoolean

/** Posted on `Saibon.events` whenever the sidebar scoreboard's text changes — future consumers: commission progress bars, dungeon score overlay, key/door counters. */
data class ScoreboardChanged(val title: String, val lines: List<String>)

/**
 * Polls the client's own sidebar scoreboard once per client tick and posts a
 * [ScoreboardChanged] only when the rendered text actually changes — this is
 * exactly the same data vanilla already renders in the corner of the screen,
 * read back rather than duplicated blind. Hypixel (like most servers) renders
 * custom sidebar lines via either a per-entry display-name override
 * (`PlayerScoreEntry.display()`) or the classic fake-scoreholder + team
 * prefix/suffix trick — both are handled, but this hasn't been checked
 * against a live Hypixel session yet (no reachable server from this
 * environment), so treat the exact line text as unverified until confirmed
 * in-game, same caveat as `MarketMenuOverlay`'s regexes.
 */
object ScoreboardReader {
    private var lastTitle: String = ""
    private var lastLines: List<String> = emptyList()
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { poll() })
    }

    fun currentTitle(): String = lastTitle
    fun currentLines(): List<String> = lastLines

    private fun poll() {
        val scoreboard = Minecraft.getInstance().level?.scoreboard
        val objective = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (scoreboard == null || objective == null) {
            update("", emptyList())
            return
        }

        val lines = scoreboard.listPlayerScores(objective)
            .sortedByDescending { it.value() }
            .map { lineFor(scoreboard, it) }
        update(objective.displayName.string, lines)
    }

    private fun lineFor(scoreboard: Scoreboard, entry: PlayerScoreEntry): String {
        val display = entry.display()
        if (display != null) return display.string

        val team = scoreboard.getPlayersTeam(entry.owner())
        return if (team != null) {
            "${team.playerPrefix.string}${entry.ownerName().string}${team.playerSuffix.string}"
        } else {
            entry.ownerName().string
        }
    }

    private fun update(title: String, lines: List<String>) {
        if (title == lastTitle && lines == lastLines) return
        lastTitle = title
        lastLines = lines
        runCatching { Saibon.events.post(ScoreboardChanged(title, lines)) }
            .onFailure { Saibon.logger.warn("Saibon scoreboard event dispatch failed", it) }
    }
}
