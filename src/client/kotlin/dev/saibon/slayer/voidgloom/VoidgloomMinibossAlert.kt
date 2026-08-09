package dev.saibon.slayer.voidgloom

import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Announces a Voidgloom Seraph miniboss spawn to party chat with the player's current
 * sub-area + coordinates, e.g. `/pc Void Mid: 123 70 -456`. Deliberate exception to Saibon's
 * "never act without direct user input" rule — approved explicitly by the user for this
 * feature (see project memory), same category as the opt-in Bazaar quick-action buttons.
 *
 * There is no chat line for a miniboss spawning — confirmed against SkyHanni's open-source
 * implementation (`features/slayer/SlayerType.kt`'s `SlayerMiniBossType`), which detects it
 * from the entity's own nameplate via a mixin'd spawn hook. This project avoids mixins, so
 * instead this polls loaded entities once per client tick (same no-mixin approach as
 * `ScoreboardReader`/`TabListReader`) and reacts the first time one of the three known
 * miniboss names appears nearby. Deliberately does NOT also require
 * `SlayerTracker.currentState()` to already know it's a Voidgloom quest — that state depends
 * on the sidebar boss/tier line having been parsed correctly first, which is one more thing
 * that can silently fail; the miniboss names alone are specific enough to be a reliable signal
 * on their own. The names themselves are copied from SkyHanni and have not been checked
 * against a live Hypixel session (no reachable server from this sandbox) — verify in-game and
 * adjust [MINIBOSS_NAMES] if Hypixel's actual nameplates differ.
 *
 * **Own-miniboss-only filter**: in a party, everyone's minibosses are visible to everyone
 * nearby, so this only alerts for minibosses owned by the local player — resolved via
 * [VoidgloomBossLookup]'s "Spawned by: <name>" hologram lookup, compared against
 * `Minecraft.getInstance().user.name`. If that hologram hasn't rendered yet on the tick a
 * miniboss first appears, the entity is retried on later ticks (not marked as handled) rather
 * than silently skipped, so a slow-to-render hologram can't suppress a real alert.
 *
 * [VoidgloomZones]'s bounding boxes are still unset placeholders, so every alert currently
 * reports the area as `"blank"` (see [VoidgloomZones]) until the user fills them in via
 * `/saibonvoidgloomscan` — the coordinates still get posted either way.
 */
object VoidgloomMinibossAlert {
    private val MINIBOSS_NAMES = setOf("Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac")

    private val initialized = AtomicBoolean(false)
    private val handledEntityIds = mutableSetOf<Int>()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { poll() })
    }

    private fun poll() {
        if (!Saibon.config.data.voidgloom.minibossAlertEnabled) return

        val level = Minecraft.getInstance().level ?: return
        val player = Minecraft.getInstance().player ?: return
        val myName = Minecraft.getInstance().user.name

        val entities = level.entitiesForRendering().toList()
        val presentIds = mutableSetOf<Int>()

        for (entity in entities) {
            val name = entity.customName?.string ?: continue
            if (name !in MINIBOSS_NAMES) continue
            presentIds += entity.id
            if (entity.id in handledEntityIds) continue

            val owner = VoidgloomBossLookup.ownerOf(entity, entities) ?: continue
            handledEntityIds += entity.id
            if (!owner.equals(myName, ignoreCase = true)) continue

            val pos = player.blockPosition()
            val zoneLabel = VoidgloomZones.zoneFor(pos)?.label ?: "blank"
            runCatching {
                Minecraft.getInstance().connection?.sendCommand("pc $zoneLabel: ${pos.x} ${pos.y} ${pos.z}")
            }.onFailure { Saibon.logger.warn("Failed to send Voidgloom miniboss party chat alert", it) }
        }
        handledEntityIds.retainAll(presentIds)
    }
}
