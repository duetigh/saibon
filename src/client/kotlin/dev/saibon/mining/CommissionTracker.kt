package dev.saibon.mining

import dev.saibon.core.Saibon
import dev.saibon.hud.TabListChanged
import java.util.concurrent.atomic.AtomicBoolean

/** One active mining commission as read off the tab list. */
data class MiningCommission(
    val description: String,
    val percent: Double
) {
    val completed: Boolean get() = percent >= 100.0
}

/**
 * Tracks active Dwarven Mines / Crystal Hollows / Glacite Tunnel commissions
 * purely from the tab player list ([dev.saibon.hud.TabListReader]) — no new
 * mixins, no menu reads. Line shapes come from reading SkyHanni's live
 * GitHub source directly (`data/model/TabWidget.kt`'s `COMMISSIONS("Commissions:")`
 * entry, and `features/mining/MiningCommissionsBlocksColor.kt`'s
 * `event.tabList.any { it.string.startsWith(" ${block.commissionName}: ") && !it.string.contains("DONE") }`
 * check), not guessed from training-data recollection — same research method
 * used for [dev.saibon.slayer.SlayerTracker]. Still **unverified against a
 * live server** (this sandbox can't reach one), same caveat as every other
 * chat/scoreboard/tab-list parser in this codebase.
 *
 * Tab list shape while commissions are active:
 * ```
 * Commissions:
 *  Amber Gemstone Collector: 45%
 *  Mithril Everywhere: DONE
 * ```
 */
object CommissionTracker {
    private const val HEADER = "commissions:"
    private val COMMISSION_LINE = Regex("""^(.+?):\s*(DONE|[\d,]+(?:\.\d+)?%)$""", RegexOption.IGNORE_CASE)
    private const val MAX_LINES_AFTER_HEADER = 12

    private val initialized = AtomicBoolean(false)

    @Volatile private var commissions: List<MiningCommission> = emptyList()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        Saibon.events.subscribe<TabListChanged> { onTabList(it) }
    }

    fun currentCommissions(): List<MiningCommission> = commissions

    private fun onTabList(event: TabListChanged) {
        val headerIndex = event.entries.indexOfFirst { it.trim().equals(HEADER, ignoreCase = true) }
        if (headerIndex < 0) {
            commissions = emptyList()
            return
        }

        val found = mutableListOf<MiningCommission>()
        for (line in event.entries.drop(headerIndex + 1).take(MAX_LINES_AFTER_HEADER)) {
            val match = COMMISSION_LINE.matchEntire(line.trim()) ?: break
            val (name, value) = match.destructured
            val percent = if (value.equals("DONE", ignoreCase = true)) {
                100.0
            } else {
                value.removeSuffix("%").replace(",", "").toDoubleOrNull() ?: continue
            }
            found += MiningCommission(name.trim(), percent)
        }

        commissions = found
    }
}
