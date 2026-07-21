package dev.saibon.mining

import dev.saibon.core.Saibon
import dev.saibon.hud.TabListChanged
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One raw forge-queue line as reported by the tab list under Hypixel's
 * "Forges:" header — see [ForgeTracker]'s doc comment for why this is kept
 * as raw, unparsed text rather than a structured slot/time-remaining model.
 */
data class ForgeLine(val text: String)

/**
 * Mirrors Hypixel's Dwarven Mines "Forges:" tab-list section
 * ([dev.saibon.hud.TabListReader] — no new mixins, no menu/container reads),
 * the "forge slot timers" sub-bullet of the Mining/Dwarven overlay bullet
 * group. The header itself is confirmed real, the same research method used
 * for [CommissionTracker]/`PowderTracker`: SkyHanni's own data repo
 * (`hannibal002/SkyHanni-REPO`, `constants/regexesModern.json`, key
 * `tab.widgetcomponent.enum.forge`) defines the exact `"Forges:"` header
 * pattern that SkyHanni's `data/model/TabWidget.kt` enum also uses
 * (`FORGE("Forges:")`), confirming Hypixel really does send this section.
 *
 * Unlike the commission/powder trackers, though, no source — SkyHanni's own
 * code included — confirms the shape of the per-slot lines underneath that
 * header. Reading `features/gui/TabWidgetDisplay.kt` shows SkyHanni defines
 * a `FORGE` header pattern but never actually wires it into a feature the
 * way it does e.g. `PICKAXE_COOLDOWN` (which it renders by mirroring the
 * tab-list lines under that header verbatim, with no bespoke per-line
 * parser). Rather than guess a `"<item>: <time>"` regex with no source to
 * confirm it against — the exact mistake this codebase's research method
 * exists to avoid — this tracker does what SkyHanni's own generic widget
 * mirror does: captures each raw (already color-stripped, per
 * [dev.saibon.hud.TabListReader]) line under the header verbatim, no
 * per-line pattern assumed, stopping at the first blank line (Hypixel
 * tab-list sections are blank-line-separated) or after
 * [MAX_LINES_AFTER_HEADER] lines — 8, one more than the documented maximum
 * of 7 forge slots (2 base slots plus one each from HotM tiers 3/4 and Peak
 * of the Mountain 2, per public Hypixel SkyBlock wiki guides) — as a safety
 * cap only, not a parsed slot count.
 *
 * **Unverified against a live server** (this sandbox can't reach one), same
 * caveat as every other tab-list/scoreboard parser in this codebase — plus
 * the added caveat above that the per-line shape itself isn't confirmed by
 * any source, not just unconfirmed live. Displaying raw text sidesteps that
 * gap instead of guessing through it; a follow-up pass with live access
 * could tighten this into a structured per-slot/time-remaining model the
 * way [CommissionTracker]/`PowderTracker` already are.
 */
object ForgeTracker {
    private const val HEADER = "forges:"
    private const val MAX_LINES_AFTER_HEADER = 8

    private val initialized = AtomicBoolean(false)

    @Volatile private var lines: List<ForgeLine> = emptyList()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        Saibon.events.subscribe<TabListChanged> { onTabList(it) }
    }

    fun currentLines(): List<ForgeLine> = lines

    private fun onTabList(event: TabListChanged) {
        val headerIndex = event.entries.indexOfFirst { it.trim().equals(HEADER, ignoreCase = true) }
        if (headerIndex < 0) {
            lines = emptyList()
            return
        }

        val found = mutableListOf<ForgeLine>()
        for (line in event.entries.drop(headerIndex + 1).take(MAX_LINES_AFTER_HEADER)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) break
            found += ForgeLine(trimmed)
        }

        lines = found
    }
}
