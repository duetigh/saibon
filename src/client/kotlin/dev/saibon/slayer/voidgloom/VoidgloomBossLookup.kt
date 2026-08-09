package dev.saibon.slayer.voidgloom

import net.minecraft.world.entity.Entity

/**
 * Shared "whose slayer mob is this" resolution for Voidgloom Seraph content — used for both
 * the boss itself ([VoidgloomBossOwnerTracker]) and its minibosses
 * ([VoidgloomMinibossAlert], so it only announces the player's *own* miniboss spawns, not a
 * nearby party member's). Hypixel doesn't put the owner's name on the mob's own nameplate; it
 * spawns a separate nearby hologram (an invisible `ArmorStand` reading `Spawned by: <name>`)
 * next to each owned mob — confirmed against SkyHanni's open-source `data/mob/Mob.kt`/
 * `MobFilter.kt` (`summonOwnerPattern`), which applies the same hologram lookup to every
 * `MobCategory.SLAYER` mob, boss and minibosses alike. Not yet checked against a live Hypixel
 * session (no reachable server from this sandbox) — verify in-game and adjust [SPAWNED_BY] if
 * the real text differs.
 */
object VoidgloomBossLookup {
    private val SPAWNED_BY = Regex("Spawned by: (?<name>.+)", RegexOption.IGNORE_CASE)
    private const val OWNER_HOLOGRAM_RADIUS = 6.0

    /** Null means no owner hologram was found nearby yet — could be a genuinely un-owned mob, or the hologram just hasn't rendered/loaded yet; callers should keep retrying rather than treat null as a final answer. */
    fun ownerOf(mob: Entity, nearbyEntities: List<Entity>): String? {
        val mobPos = mob.position()
        for (entity in nearbyEntities) {
            if (entity.id == mob.id) continue
            val name = entity.customName?.string ?: continue
            val match = SPAWNED_BY.matchEntire(name) ?: continue
            if (entity.position().distanceTo(mobPos) > OWNER_HOLOGRAM_RADIUS) continue
            return match.groups["name"]?.value?.trim()
        }
        return null
    }
}
