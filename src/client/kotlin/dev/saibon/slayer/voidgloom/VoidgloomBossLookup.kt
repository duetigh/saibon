package dev.saibon.slayer.voidgloom

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level

/**
 * Shared "whose slayer mob is this" resolution for Voidgloom Seraph content — used for both
 * the boss itself ([VoidgloomBossOwnerTracker]) and its minibosses ([VoidgloomMinibossAlert]).
 * Hypixel doesn't put the owner's name on the mob's own nameplate; it spawns extra armor stand
 * entities riding along with it, one of which reads `Spawned by: <name>`.
 *
 * Looked up by **entity ID offset**, not proximity search — confirmed against SkyHanni's
 * open-source `data/mob/Mob.kt` (`hologram2` = `MobUtils.getArmorStand(armorStand ?: baseEntity,
 * 2)`, i.e. `level.getEntity(mob.id + 2)`) and `MobFactories.kt`/`OtherPlayersSlayerApi.kt`,
 * which apply this to every `MobCategory.SLAYER` mob, boss and minibosses alike. Hypixel spawns
 * a slayer mob's entity, its name/health armor stand, and its extra holograms as a contiguous
 * batch of entity IDs in the same tick, so `id+1`/`id+2` from the detected (name-bearing) entity
 * reliably lands on those holograms the instant they exist — no waiting on render distance or
 * risk of grabbing a neighboring mob's hologram in a crowded fight.
 *
 * An earlier version of this searched for the nearest "Spawned by:" text within a several-block
 * radius instead. Live testing showed that worked, but slowly and unreliably — resolution could
 * take 7-30+ seconds, long enough for a miniboss to die and disappear before its owner was ever
 * known, which is why the miniboss alert wasn't firing for the player's own minis. The ID-offset
 * approach should resolve on the same tick the hologram exists instead.
 */
object VoidgloomBossLookup {
    private val SPAWNED_BY = Regex("Spawned by: (?<name>.+)", RegexOption.IGNORE_CASE)

    /** Null means no owner hologram was found at `mob.id + 1`/`+2` yet — could be a genuinely un-owned mob, or the hologram just hasn't spawned/loaded yet; callers should keep retrying rather than treat null as a final answer. */
    fun ownerOf(mob: Entity, level: Level): String? {
        for (offset in 1..2) {
            val candidate = level.getEntity(mob.id + offset) ?: continue
            val name = candidate.customName?.string ?: continue
            val match = SPAWNED_BY.find(name) ?: continue
            return match.groups["name"]?.value?.trim()
        }
        return null
    }
}
