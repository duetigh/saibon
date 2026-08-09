package dev.saibon.hud

import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.level.GameType
import java.util.concurrent.atomic.AtomicBoolean

/** Posted on `Saibon.events` whenever the tab-player-list's visible entries change — future consumers: tab-list widgets (effects display, first-launch hint), click-a-name-to-open-profile. */
data class TabListChanged(val entries: List<String>)

/**
 * Polls the client's own tab list once per client tick, same shape/caveats as [ScoreboardReader].
 *
 * `connection.listedOnlinePlayers` is *not* in display order — it's whatever order the
 * backing collection happens to iterate in. The vanilla tab-list overlay
 * (`net.minecraft.client.gui.components.PlayerTabOverlay`, confirmed by decompiling
 * `minecraft-clientonly-deobf-26.2.jar`'s `PLAYER_COMPARATOR`/`getPlayerInfos()`) sorts by
 * each entry's `tabListOrder` (descending) before rendering, since that's the field servers
 * use to control tab-list layout. Hypixel's "widget" sections (Forges:, Powder:, commissions,
 * etc.) depend on that ordering — without replicating it here, header-relative line lookups
 * like [ForgeTracker]/[CommissionTracker] can pick up unrelated entries (other players'
 * names, other widgets' lines) that just happen to sit near the header in iteration order.
 */
object TabListReader {
    private var lastEntries: List<String> = emptyList()
    private val initialized = AtomicBoolean(false)

    private val PLAYER_COMPARATOR = compareByDescending<PlayerInfo> { it.tabListOrder }
        .thenBy { if (it.gameMode == GameType.SPECTATOR) 1 else 0 }
        .thenBy { it.team?.name ?: "" }
        .thenComparator { a, b -> a.profile.name.compareTo(b.profile.name, ignoreCase = true) }

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { poll() })
    }

    fun currentEntries(): List<String> = lastEntries

    private fun poll() {
        val connection = Minecraft.getInstance().connection
        val names = connection?.listedOnlinePlayers
            ?.sortedWith(PLAYER_COMPARATOR)
            ?.map { info -> info.tabListDisplayName?.string ?: info.profile.name }
            ?: emptyList()

        if (names == lastEntries) return
        lastEntries = names
        runCatching { Saibon.events.post(TabListChanged(names)) }
            .onFailure { Saibon.logger.warn("Saibon tab-list event dispatch failed", it) }
    }
}
