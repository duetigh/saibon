package dev.saibon.slayer.voidgloom

import dev.saibon.core.Saibon
import dev.saibon.slayer.SlayerOutcome
import dev.saibon.slayer.SlayerTracker
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches nearby Voidgloom Seraph boss entities, resolves whose boss each one is, and — for
 * bosses owned by a tracked party member — announces a running kill tally to party chat, e.g.
 * `/pc Steve: 4`. Deliberate exception to Saibon's "never act without direct user input" rule
 * — approved explicitly by the user for this feature, same category as
 * [dev.saibon.slayer.voidgloom.VoidgloomMinibossAlert].
 *
 * Owner resolution ([VoidgloomBossLookup]) is shared with [VoidgloomMinibossAlert] — same
 * "Spawned by: <name>" hologram lookup, since Hypixel doesn't put the owner's name on the
 * boss's own nameplate.
 *
 * **Two separate kill-detection paths, because there's no single reliable signal that covers
 * both:**
 * - **The player's own boss** is detected via [SlayerTracker.onOutcome]'s existing
 *   `COMPLETED` event (the same scoreboard/chat-based signal `SlayerHudModule`'s toast already
 *   relies on) — reliable, and the only option here, because a first-hit-kill boss (e.g. a
 *   one-shot with a strong weapon) can die before an entity-based tick-poll ever gets a chance
 *   to observe it. Also, Hypixel's slayer bosses don't reliably expose a real, live vanilla
 *   `LivingEntity.getHealth()` — SkyBlock mobs commonly render their "real" HP as nametag text
 *   instead of updating the actual entity health attribute, so watching for `getHealth() <= 0`
 *   (the first version of this tracker) never fired for a real kill.
 * - **Other players' bosses** have no such signal available (this client only ever sees its
 *   own quest state) — the fallback here is: once a tracked boss entity has a resolved owner,
 *   treat it disappearing from render range as a kill. This is a deliberate approximation: the
 *   overwhelmingly common reason a boss entity vanishes mid-fight is that it died, but a
 *   teammate abandoning/failing their quest and walking far away would also (incorrectly)
 *   count. Flagged here rather than silently accepted — revisit if false-positive kills turn
 *   out to be a real problem in practice.
 */
object VoidgloomBossOwnerTracker {
    private const val BOSS_NAME = "Voidgloom Seraph"

    private val initialized = AtomicBoolean(false)
    private val tracked = mutableMapOf<Int, String>() // entity id -> owner name

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { poll() })
        SlayerTracker.onOutcome { outcome -> onOwnQuestOutcome(outcome) }
    }

    private fun onOwnQuestOutcome(outcome: SlayerOutcome) {
        if (!Saibon.config.data.voidgloom.bossOwnerTrackerEnabled) return
        if (outcome != SlayerOutcome.COMPLETED) return
        val bossName = SlayerTracker.currentState().bossName ?: return
        if (!bossName.contains(BOSS_NAME, ignoreCase = true)) return
        onBossKilled(Minecraft.getInstance().user.name)
    }

    private fun poll() {
        if (!Saibon.config.data.voidgloom.bossOwnerTrackerEnabled) return
        val level = Minecraft.getInstance().level ?: return
        val myName = Minecraft.getInstance().user.name
        val entities = level.entitiesForRendering().toList()

        for (entity in entities) {
            if (entity.id in tracked) continue
            val name = entity.customName?.string ?: continue
            if (!name.contains(BOSS_NAME, ignoreCase = true)) continue
            val owner = VoidgloomBossLookup.ownerOf(entity, entities) ?: continue
            // The player's own boss is handled via SlayerTracker.onOutcome instead — tracking
            // it here too would double-count the same kill.
            if (owner.equals(myName, ignoreCase = true)) continue
            tracked[entity.id] = owner
        }

        if (tracked.isEmpty()) return

        val presentIds = entities.mapTo(mutableSetOf()) { it.id }
        val iterator = tracked.entries.iterator()
        while (iterator.hasNext()) {
            val (id, owner) = iterator.next()
            if (id in presentIds) continue
            iterator.remove()
            onBossKilled(owner)
        }
    }

    private fun onBossKilled(ownerName: String) {
        val tracker = Saibon.config.data.voidgloom.trackedPlayers[ownerName.lowercase()] ?: return
        tracker.kills += 1
        Saibon.config.save()
        runCatching {
            Minecraft.getInstance().connection?.sendCommand("pc ${tracker.displayName}: ${tracker.kills}")
        }.onFailure { Saibon.logger.warn("Failed to send Voidgloom boss-kill party chat message", it) }
    }
}
