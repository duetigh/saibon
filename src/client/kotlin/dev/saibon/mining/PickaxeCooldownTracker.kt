package dev.saibon.mining

import dev.saibon.core.Saibon
import dev.saibon.hud.TabListChanged
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One raw pickaxe-ability line as reported by the tab list under Hypixel's
 * "Pickaxe Ability:" header — see [PickaxeCooldownTracker]'s doc comment for
 * why this is kept as raw, unparsed text rather than a structured
 * ability-name/cooldown model.
 */
data class PickaxeCooldownLine(val text: String)

/**
 * Mirrors Hypixel's "Pickaxe Ability:" tab-list section
 * ([dev.saibon.hud.TabListReader] — no new mixins, no menu/container reads),
 * the "pickaxe cooldown" sub-bullet of the Mining/Dwarven overlay bullet
 * group. The header itself is confirmed real by reading SkyHanni's live
 * GitHub source directly: `data/model/TabWidget.kt`'s
 * `PICKAXE_COOLDOWN("Pickaxe Ability:")` entry, and `features/gui/
 * TabWidgetDisplay.kt` confirms SkyHanni renders this widget the same
 * generic, unparsed way it renders `FORGE` — both are just entries in a
 * shared `TabWidgetDisplay` enum whose `onGuiRenderOverlay()` mirrors
 * `widget.lines` verbatim via `toRenderables()`, with no bespoke per-line
 * parser for either. A repo-wide search of both SkyHanni and its
 * `SkyHanni-REPO` data repo turned up no `REGEX-TEST`/example line for what
 * text actually appears under the "Pickaxe Ability:" header (only chat-line
 * patterns for *other* pickaxe-ability chat messages, not the tab-list
 * section), so — same as [ForgeTracker] — this tracker captures each raw,
 * already color-stripped (per [dev.saibon.hud.TabListReader]) line under the
 * header verbatim rather than guessing a parsed format, stopping at the
 * first blank line (Hypixel tab-list sections are blank-line-separated) or
 * after [MAX_LINES_AFTER_HEADER] lines as a safety cap only.
 *
 * **Unverified against a live server** (this sandbox can't reach one), same
 * caveat as every other tab-list parser in this codebase — plus the added
 * caveat that the per-line shape itself is unconfirmed by any source, not
 * just unconfirmed live; showing raw text instead of a guessed regex is the
 * deliberate mitigation for that gap, identical to [ForgeTracker].
 */
object PickaxeCooldownTracker {
    private const val HEADER = "pickaxe ability:"
    private const val MAX_LINES_AFTER_HEADER = 6

    private val initialized = AtomicBoolean(false)

    @Volatile private var lines: List<PickaxeCooldownLine> = emptyList()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        Saibon.events.subscribe<TabListChanged> { onTabList(it) }
    }

    fun currentLines(): List<PickaxeCooldownLine> = lines

    private fun onTabList(event: TabListChanged) {
        val headerIndex = event.entries.indexOfFirst { it.trim().equals(HEADER, ignoreCase = true) }
        if (headerIndex < 0) {
            lines = emptyList()
            return
        }

        val found = mutableListOf<PickaxeCooldownLine>()
        for (line in event.entries.drop(headerIndex + 1).take(MAX_LINES_AFTER_HEADER)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) break
            found += PickaxeCooldownLine(trimmed)
        }

        lines = found
    }
}
