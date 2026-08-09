package dev.saibon.slayer.voidgloom

import dev.saibon.core.Saibon
import dev.saibon.core.debug.DebugConsole
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
 * miniboss names appears nearby. Matched with `contains`, not exact equality — an earlier
 * exact-match version of this check never alerted in real play (user-reported), because
 * SkyHanni's own nameplate regex (`MobFilter.slayerNameFilter`) shows Hypixel appends a
 * level/tier icon and a live health-count suffix to the mob's name on the same nametag line
 * (e.g. `"✥ Voidling Devotee IV 15.2k❤"`), so the nametag is never exactly the bare name.
 * [VoidgloomBossOwnerTracker] already used `contains` for the main boss for the same reason.
 * Deliberately does NOT also require `SlayerTracker.currentState()` to already know it's a
 * Voidgloom quest — that state depends on the sidebar boss/tier line having been parsed
 * correctly first, which is one more thing that can silently fail; the miniboss names alone are
 * specific enough to be a reliable signal on their own.
 *
 * **Own-miniboss-only filter**: in a party, everyone's minibosses are visible to everyone
 * nearby, so this only alerts for minibosses owned by the local player — resolved via
 * [VoidgloomBossLookup]'s entity-ID-offset "Spawned by: <name>" hologram lookup, compared
 * against `Minecraft.getInstance().user.name`. If that hologram hasn't spawned yet on the tick a
 * miniboss first appears, the entity is retried on later ticks (not marked as handled) rather
 * than silently skipped, so a not-yet-spawned hologram can't suppress a real alert. An earlier
 * version of [VoidgloomBossLookup] used a several-block proximity search instead of ID offsets,
 * which live testing showed could take 7-30+ seconds to resolve — long enough for a short-lived
 * miniboss to die and disappear first, which is why this alert wasn't firing for the player's
 * own minis at all.
 *
 * [VoidgloomZones]'s bounding boxes are still unset placeholders, so every alert currently
 * reports the area as `"blank"` (see [VoidgloomZones]) until the user fills them in via
 * `/saibonvoidgloomscan` — the coordinates still get posted either way.
 */
object VoidgloomMinibossAlert {
    private val MINIBOSS_NAMES = setOf("Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac")

    private val initialized = AtomicBoolean(false)
    private val handledEntityIds = mutableSetOf<Int>()

    // TEMPORARY diagnostic aid for the "no chat message on mini spawn" bug report — logs what
    // this client actually sees around a detected miniboss so the real nameplate/hologram
    // format can be confirmed from a live session instead of guessed at. Remove once the bug is
    // confirmed fixed.
    private val loggedDetectionIds = mutableSetOf<Int>()
    private val noOwnerRetryCount = mutableMapOf<Int, Int>()
    private const val NO_OWNER_LOG_EVERY_TICKS = 60 // ~3s at 20 TPS
    private const val NO_OWNER_LOG_MAX_ATTEMPTS = 10

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

        val playerPos = player.blockPosition()

        for (entity in entities) {
            val name = entity.customName?.string ?: continue
            if (MINIBOSS_NAMES.none { name.contains(it, ignoreCase = true) }) continue
            presentIds += entity.id
            if (entity.id in handledEntityIds) continue

            if (loggedDetectionIds.add(entity.id)) {
                debugLog(
                    "Miniboss detected: name=\"$name\" id=${entity.id} " +
                        "myPos=(${playerPos.x}, ${playerPos.y}, ${playerPos.z}) myName=$myName",
                )
            }

            val owner = VoidgloomBossLookup.ownerOf(entity, level)
            if (owner == null) {
                val attempts = noOwnerRetryCount.getOrDefault(entity.id, 0) + 1
                noOwnerRetryCount[entity.id] = attempts
                if (attempts <= NO_OWNER_LOG_MAX_ATTEMPTS * NO_OWNER_LOG_EVERY_TICKS &&
                    attempts % NO_OWNER_LOG_EVERY_TICKS == 0
                ) {
                    val idPlus1 = level.getEntity(entity.id + 1)?.customName?.string
                    val idPlus2 = level.getEntity(entity.id + 2)?.customName?.string
                    debugLog(
                        "Still no owner hologram for id=${entity.id} after $attempts ticks (~${attempts / 20}s); " +
                            "id+1=\"$idPlus1\" id+2=\"$idPlus2\"",
                    )
                }
                continue
            }
            handledEntityIds += entity.id
            debugLog("Owner resolved: \"$owner\" (mine=\"$myName\") for id=${entity.id}")
            if (!owner.equals(myName, ignoreCase = true)) continue

            val zoneLabel = VoidgloomZones.zoneFor(playerPos)?.label ?: "blank"
            debugLog("Sending own miniboss alert at (${playerPos.x}, ${playerPos.y}, ${playerPos.z}), zone=$zoneLabel")
            runCatching {
                Minecraft.getInstance().connection?.sendCommand("pc $zoneLabel: ${playerPos.x} ${playerPos.y} ${playerPos.z}")
            }.onFailure { Saibon.logger.warn("Failed to send Voidgloom miniboss party chat alert", it) }
        }
        handledEntityIds.retainAll(presentIds)
        loggedDetectionIds.retainAll(presentIds)
        noOwnerRetryCount.keys.retainAll(presentIds)
    }

    private fun debugLog(message: String) {
        Saibon.logger.info("[Voidgloom debug] $message")
        DebugConsole.log("[Voidgloom] $message")
    }
}
