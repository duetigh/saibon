package dev.saibon.slayer.voidgloom

import dev.saibon.core.Saibon
import dev.saibon.slayer.SlayerTracker
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
 * miniboss names appears. The names themselves are copied from SkyHanni and have not been
 * checked against a live Hypixel session (no reachable server from this sandbox) — verify
 * in-game and adjust [MINIBOSS_NAMES] if Hypixel's actual nameplates differ.
 */
object VoidgloomMinibossAlert {
    private val MINIBOSS_NAMES = setOf("Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac")

    private val initialized = AtomicBoolean(false)
    private val alertedEntityIds = mutableSetOf<Int>()
    private var wasActive = false

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { poll() })
    }

    private fun poll() {
        if (!Saibon.config.data.voidgloom.minibossAlertEnabled) {
            if (wasActive) alertedEntityIds.clear()
            wasActive = false
            return
        }

        val state = SlayerTracker.currentState()
        val isVoidgloom = state.active && state.bossName?.contains("Voidgloom", ignoreCase = true) == true

        if (!isVoidgloom) {
            if (wasActive) alertedEntityIds.clear()
            wasActive = false
            return
        }
        wasActive = true

        val level = Minecraft.getInstance().level ?: return
        val player = Minecraft.getInstance().player ?: return

        for (entity in level.entitiesForRendering()) {
            val name = entity.customName?.string ?: continue
            if (name !in MINIBOSS_NAMES) continue
            if (!alertedEntityIds.add(entity.id)) continue

            val pos = player.blockPosition()
            val zoneLabel = VoidgloomZones.zoneFor(pos)?.label ?: "Unknown area"
            runCatching {
                Minecraft.getInstance().connection?.sendCommand("pc $zoneLabel: ${pos.x} ${pos.y} ${pos.z}")
            }.onFailure { Saibon.logger.warn("Failed to send Voidgloom miniboss party chat alert", it) }
        }
    }
}
