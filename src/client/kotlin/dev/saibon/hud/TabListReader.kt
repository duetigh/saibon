package dev.saibon.hud

import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.concurrent.atomic.AtomicBoolean

/** Posted on `Saibon.events` whenever the tab-player-list's visible entries change — future consumers: tab-list widgets (effects display, first-launch hint), click-a-name-to-open-profile. */
data class TabListChanged(val entries: List<String>)

/** Polls the client's own tab list once per client tick, same shape/caveats as [ScoreboardReader]. */
object TabListReader {
    private var lastEntries: List<String> = emptyList()
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { poll() })
    }

    fun currentEntries(): List<String> = lastEntries

    private fun poll() {
        val connection = Minecraft.getInstance().connection
        val names = connection?.listedOnlinePlayers?.map { info ->
            info.tabListDisplayName?.string ?: info.profile.name
        } ?: emptyList()

        if (names == lastEntries) return
        lastEntries = names
        runCatching { Saibon.events.post(TabListChanged(names)) }
            .onFailure { Saibon.logger.warn("Saibon tab-list event dispatch failed", it) }
    }
}
