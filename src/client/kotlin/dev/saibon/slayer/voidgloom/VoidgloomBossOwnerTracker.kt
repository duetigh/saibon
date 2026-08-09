package dev.saibon.slayer.voidgloom

import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
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
 */
object VoidgloomBossOwnerTracker {
    private const val BOSS_NAME = "Voidgloom Seraph"

    private class TrackedBoss(val ownerName: String, var lastHealth: Float)

    private val initialized = AtomicBoolean(false)
    private val tracked = mutableMapOf<Int, TrackedBoss>()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { poll() })
    }

    private fun poll() {
        if (!Saibon.config.data.voidgloom.bossOwnerTrackerEnabled) return
        val level = Minecraft.getInstance().level ?: return
        val entities = level.entitiesForRendering().toList()

        for (entity in entities) {
            if (entity.id in tracked) continue
            val name = entity.customName?.string ?: continue
            if (!name.contains(BOSS_NAME, ignoreCase = true)) continue
            val owner = VoidgloomBossLookup.ownerOf(entity, entities) ?: continue
            val health = (entity as? LivingEntity)?.health ?: continue
            tracked[entity.id] = TrackedBoss(owner, health)
        }

        if (tracked.isEmpty()) return

        val presentById = entities.associateBy { it.id }
        val iterator = tracked.entries.iterator()
        while (iterator.hasNext()) {
            val (id, boss) = iterator.next()
            val entity = presentById[id]
            if (entity == null) {
                // Left render distance without a confirmed kill — drop silently, not a kill.
                iterator.remove()
                continue
            }
            val health = (entity as? LivingEntity)?.health ?: continue
            if (health <= 0f && boss.lastHealth > 0f) {
                onBossKilled(boss.ownerName)
            }
            boss.lastHealth = health
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
