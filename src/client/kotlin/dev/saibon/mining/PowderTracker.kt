package dev.saibon.mining

import dev.saibon.core.Saibon
import dev.saibon.hud.ScoreboardChanged
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** The three powder currencies Hypixel tracks across the mining islands (Dwarven Mines/Crystal Hollows/Glacite Tunnel). */
enum class PowderType(val label: String) { MITHRIL("Mithril"), GEMSTONE("Gemstone"), GLACITE("Glacite") }

/** Current total + session gain/rate for one [PowderType], as read off the sidebar scoreboard. */
data class PowderState(
    val type: PowderType,
    val current: Long,
    val gainedThisSession: Long,
    val perHour: Double
)

/**
 * Tracks Mithril/Gemstone/Glacite Powder totals purely from the sidebar
 * scoreboard ([dev.saibon.hud.ScoreboardReader] — no new mixins), the same
 * research method used for [CommissionTracker]/`SlayerTracker`: line shape
 * confirmed by reading SkyHanni's live GitHub source directly
 * (`features/gui/customscoreboard/ScoreboardPattern.kt`'s `powderPattern`,
 * `"(?:§.)*᠅ §.(?<type>Gemstone|Mithril|Glacite)(?: Powder)?(?:§.)*:? (?:§.)*(?<amount>[\d,.]*)"`,
 * with its own `REGEX-TEST` lines showing the raw sidebar text as
 * `"§2᠅ §fMithril Powder§f: §235,448"` / `"§2᠅ §fMithril§f: §235,448"` etc. for
 * all three types), not guessed from training-data recollection alone.
 * [dev.saibon.hud.ScoreboardReader] already strips formatting codes
 * (`Component.string`), so the line this class matches is the color-stripped
 * shape: `"᠅ Mithril Powder: 35,448"` (the " Powder" suffix and the leading
 * "᠅ " symbol are both optional per SkyHanni's pattern, so both are optional
 * here too).
 *
 * The scoreboard only reports the *current total*, not a gain rate, so this
 * class records a baseline (value + timestamp) the first time each type is
 * seen and derives "gained this session" / "powder per hour" from the delta
 * — mirroring NEU/SkyHanni's powder tracker concept without needing an
 * instance-join hook this codebase doesn't have yet. The baseline is set once
 * per client session (never reset on zone change), so "session" here means
 * "since this Minecraft instance was launched," not "since entering this
 * mining island" — a coarser scope than SkyHanni's own tracker, left as a
 * known simplification for a follow-up pass (would need a world/instance-join
 * event this codebase doesn't have).
 *
 * **Unverified against a live server** (this sandbox can't reach one), same
 * caveat as every other chat/scoreboard/tab-list parser in this codebase —
 * but the line shape itself comes from SkyHanni's actual source rather than
 * a guess, same confidence level as [CommissionTracker]/`SlayerTracker`.
 */
object PowderTracker {
    private val POWDER_LINE = Regex("""^᠅?\s*(Gemstone|Mithril|Glacite)(?:\s+Powder)?:\s*([\d,.]+)$""", RegexOption.IGNORE_CASE)
    private const val MIN_ELAPSED_MILLIS_FOR_RATE = 5_000L

    private data class Baseline(val amount: Long, val atMillis: Long)

    private val initialized = AtomicBoolean(false)
    private val baselines = ConcurrentHashMap<PowderType, Baseline>()

    @Volatile private var current: List<PowderState> = emptyList()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        Saibon.events.subscribe<ScoreboardChanged> { onScoreboard(it) }
    }

    fun currentStates(): List<PowderState> = current

    private fun onScoreboard(event: ScoreboardChanged) {
        val now = System.currentTimeMillis()
        val found = mutableListOf<PowderState>()

        for (line in event.lines) {
            val match = POWDER_LINE.matchEntire(line.trim()) ?: continue
            val type = PowderType.entries.find { it.label.equals(match.groupValues[1], ignoreCase = true) } ?: continue
            val amount = match.groupValues[2].replace(",", "").toLongOrNull() ?: continue

            val baseline = baselines.computeIfAbsent(type) { Baseline(amount, now) }
            val gained = amount - baseline.amount
            val elapsedMillis = now - baseline.atMillis
            val perHour = if (elapsedMillis >= MIN_ELAPSED_MILLIS_FOR_RATE) {
                gained.toDouble() * 3_600_000.0 / elapsedMillis
            } else {
                0.0
            }

            found += PowderState(type, amount, gained, perHour)
        }

        current = found
    }
}
