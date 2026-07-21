package dev.saibon.mining

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import java.util.concurrent.atomic.AtomicBoolean

/** One perk slot read from the real Heart of the Mountain menu: name, current/max level, and lock state. */
data class HotmPerkInfo(val name: String, val level: Int, val maxLevel: Int, val unlocked: Boolean)

/** Everything read from one Heart of the Mountain menu visit, plus when it was read. */
data class HotmSnapshot(val perks: List<HotmPerkInfo>, val tokensAvailable: Int?, val readAtMillis: Long)

/**
 * "Heart of the Mountain perk preview" — the next unbuilt sub-bullet of the
 * Mining/Dwarven overlay group (`NEU_FEATURE_PARITY.md` #4), after commission
 * list/powder/forge/fuel/pickaxe cooldown. Unlike those, this doesn't read a
 * scoreboard or tab-list line — the perk tree only exists inside Hypixel's own
 * "Heart of the Mountain" inventory GUI, so this is this package's first
 * container/menu read, using the exact same "detect a real container screen by
 * title, then read its real `Slot`s" technique [dev.saibon.market.ui.MarketMenuOverlay]
 * already established for the Auction House/Bazaar menus (`ScreenEvents.AFTER_INIT`
 * + a title regex, no new mixins) — render-only, no slot clicks, no menu
 * mutation, same as that overlay.
 *
 * Every HotM perk slot's lore already contains its own current/max level as
 * plain text — confirmed against SkyHanni's own live source
 * (`api/HotmApi.kt`/`data/hotx/HotmData.kt`'s `levelPattern`, with
 * `REGEX-TEST` lines pulled from a real Hypixel session:
 * `"§5§o§7Level 1§8/50 §7(§b0 §l0%§7):skull:"` and `"§7Level 1§8/50"`) — so,
 * unlike NEU/SkyHanni, this reader doesn't need a hardcoded per-perk
 * name→max-level table (SkyHanni's giant `HotmData` enum of ~35 perks with
 * hand-entered cost/reward formulas) at all: the level line itself already
 * says "Level 1/50" once this codebase's already-color-stripped
 * `Component.getString()` convention (same as every other tracker in this
 * package) drops the `§8` between the two numbers, leaving a plain
 * `"Level <n>/<max>"` substring to match directly off the real item. Locked
 * perks are detected the same way SkyHanni's `notUnlockedPattern` does —
 * `"Requires ..."`/`"Click to unlock!"` lines in the lore — rather than
 * inferring lock state from the level number. `Token of the Mountain: N` is
 * read the same way (SkyHanni's `heartTokensPattern`) from whichever slot
 * carries it (the heart item itself).
 *
 * Since Hypixel streams this menu's item contents in over the network after
 * the screen opens rather than all at once, this re-reads every render frame
 * the menu is open ([ScreenEvents.afterExtract], the same hook
 * `MarketMenuOverlay` renders from — here just used to re-snapshot, nothing is
 * drawn over the real menu), so a slow-to-arrive slot still gets captured
 * without needing a fixed "wait N ticks" guess. The result is cached
 * in-memory only (session-scoped, the same simplification
 * [PowderTracker]/`ForgeTracker` already document — no per-profile disk
 * persistence exists in this codebase yet) and survives after the menu
 * closes, so [dev.saibon.hud.modules.HotmPerkHudModule] can render a "preview"
 * of the last-read tree without the player needing to keep the real GUI open.
 *
 * **Unverified against a live server** (this sandbox can't reach one), same
 * caveat as every other tracker in this package — but the level-line and
 * token-line shapes both come from SkyHanni's actual source rather than a
 * guess, the same confidence level as [CommissionTracker]/`PowderTracker`.
 */
object HotmPerkTracker {
    private val HEART_TITLE = Regex("Heart of the Mountain", RegexOption.IGNORE_CASE)
    private val LEVEL_LINE = Regex("""Level\s+(\d+)\s*/\s*(\d+)""")
    private val TOKEN_LINE = Regex("""Token of the Mountain:\s*(\d+)""")
    private const val LOCKED_MARKER = "Click to unlock!"
    private const val REQUIRES_PREFIX = "Requires "

    private val initialized = AtomicBoolean(false)

    @Volatile private var snapshot: HotmSnapshot? = null

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            if (!isOnHypixel()) return@register
            if (!HEART_TITLE.containsMatchIn(screen.title.string)) return@register
            ScreenEvents.afterExtract(screen).register { _, _, _, _, _ -> capture(screen) }
        }
    }

    fun current(): HotmSnapshot? = snapshot

    private fun isOnHypixel(): Boolean =
        Minecraft.getInstance().currentServer?.ip?.contains("hypixel.net", ignoreCase = true) == true

    private fun capture(screen: AbstractContainerScreen<*>) {
        val player = Minecraft.getInstance().player
        val perks = mutableListOf<HotmPerkInfo>()
        var tokens: Int? = null

        for (slot in screen.menu.slots) {
            if (slot.container === player?.inventory) continue
            val item = slot.item
            if (item.isEmpty) continue

            val loreLines = item.get(DataComponents.LORE)?.lines()?.map { it.string } ?: emptyList()
            for (line in loreLines) {
                TOKEN_LINE.find(line)?.let { tokens = it.groupValues[1].toIntOrNull() }
            }

            val levelMatch = loreLines.firstNotNullOfOrNull { LEVEL_LINE.find(it) } ?: continue
            val level = levelMatch.groupValues[1].toIntOrNull() ?: continue
            val maxLevel = levelMatch.groupValues[2].toIntOrNull() ?: continue
            val locked = loreLines.any { it.contains(LOCKED_MARKER) || it.startsWith(REQUIRES_PREFIX) }
            val name = item.hoverName.string.trim()
            if (name.isEmpty()) continue

            perks += HotmPerkInfo(name, level, maxLevel, unlocked = !locked)
        }

        snapshot = HotmSnapshot(perks, tokens, System.currentTimeMillis())
    }
}
