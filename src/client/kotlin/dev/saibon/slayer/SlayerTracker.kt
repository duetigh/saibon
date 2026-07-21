package dev.saibon.slayer

import dev.saibon.chat.ChatPatternRegistry
import dev.saibon.core.Saibon
import dev.saibon.hud.ScoreboardChanged
import java.util.concurrent.atomic.AtomicBoolean

/** Which stage of a slayer fight the sidebar currently reports. */
enum class SlayerPhase { GRINDING, BOSS_SPAWNED, BOSS_SLAIN }

/** How the most recently active quest ended, for [SlayerTracker.onOutcome]'s one-shot toast callback. */
enum class SlayerOutcome { COMPLETED, FAILED }

/** Current slayer quest as read off the sidebar scoreboard. `active == false` means no quest is in progress. */
data class SlayerQuestState(
    val active: Boolean,
    val bossName: String? = null,
    val tier: String? = null,
    val kills: Int? = null,
    val killsRequired: Int? = null,
    val phase: SlayerPhase = SlayerPhase.GRINDING
)

/**
 * Tracks the active Slayer quest purely from data the client already
 * receives — the sidebar scoreboard ([dev.saibon.hud.ScoreboardReader]) and
 * chat ([ChatPatternRegistry]) — no new mixins, no world/entity reads. Line
 * shapes below are modeled on SkyHanni's `ScoreboardElementSlayer`/
 * `SlayerApi`/`ChatFilter` source (its `REGEX-TEST` comments are literal
 * sidebar/chat strings pulled from a live session), since this sandbox has
 * no reachable Hypixel server to verify against directly — same
 * "unverified against a live server" caveat as `ScoreboardReader`'s own
 * doc comment and `MarketMenuOverlay`'s regexes.
 *
 * Sidebar shape while a quest is active:
 * ```
 * Slayer Quest
 * - Voidgloom Seraph III
 * - 12/120 Kills        (before the boss spawns)
 * - Slay the boss!       (boss is alive, fight in progress)
 * - Boss slain!          (kill registered, reward not yet claimed)
 * ```
 * Hypixel doesn't send a dedicated "quest failed" chat line (per the same
 * source) — SkyHanni infers it the same way this class does: the "Slayer
 * Quest" block disappearing from the sidebar without a prior
 * "SLAYER QUEST COMPLETE!" chat line means the quest was abandoned (left
 * the area) or timed out.
 */
object SlayerTracker {
    private val BOSS_TIER_LINE = Regex("""^-\s*(.+?)\s+(I{1,3}|IV|V)\s*$""")
    private val KILLS_LINE = Regex("""^-\s*[\d,]+\s*/\s*[\d,]+\s*Kills\s*$""")
    private val KILLS_NUMBERS = Regex("""(\d[\d,]*)\s*/\s*(\d[\d,]*)""")
    private val QUEST_STARTED = Regex("SLAYER QUEST STARTED!", RegexOption.IGNORE_CASE)
    private val QUEST_COMPLETE = Regex("SLAYER QUEST COMPLETE!", RegexOption.IGNORE_CASE)

    private val initialized = AtomicBoolean(false)

    @Volatile private var state = SlayerQuestState(active = false)
    private var sawCompletionChat = false
    private var outcomeCallback: ((SlayerOutcome) -> Unit)? = null

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        Saibon.events.subscribe<ScoreboardChanged> { onScoreboard(it) }
        ChatPatternRegistry.register(QUEST_STARTED) { onQuestStarted() }
        ChatPatternRegistry.register(QUEST_COMPLETE) { onQuestComplete() }
    }

    fun currentState(): SlayerQuestState = state

    /** Registers the one callback fired when an active quest ends — [SlayerHudModule] uses this to drive its post-quest toast. Last registration wins, matching this codebase's single-consumer HUD module callbacks (e.g. `EstimatedValueHudModule.onHover`). */
    fun onOutcome(callback: (SlayerOutcome) -> Unit) {
        outcomeCallback = callback
    }

    private fun onQuestStarted() {
        sawCompletionChat = false
        state = SlayerQuestState(active = true)
    }

    private fun onQuestComplete() {
        sawCompletionChat = true
        if (state.active) {
            state = state.copy(phase = SlayerPhase.BOSS_SLAIN)
            outcomeCallback?.invoke(SlayerOutcome.COMPLETED)
        }
        state = SlayerQuestState(active = false)
    }

    private fun onScoreboard(event: ScoreboardChanged) {
        val headerIndex = event.lines.indexOfFirst { it.trim() == "Slayer Quest" }
        if (headerIndex < 0) {
            if (state.active && !sawCompletionChat) {
                outcomeCallback?.invoke(SlayerOutcome.FAILED)
            }
            state = SlayerQuestState(active = false)
            return
        }

        var bossName = state.bossName
        var tier = state.tier
        var kills = state.kills
        var killsRequired = state.killsRequired
        var phase = state.phase

        for (line in event.lines.drop(headerIndex + 1).take(3)) {
            val trimmed = line.trim()
            when {
                trimmed.equals("- Slay the boss!", ignoreCase = true) -> phase = SlayerPhase.BOSS_SPAWNED
                trimmed.equals("- Boss slain!", ignoreCase = true) -> phase = SlayerPhase.BOSS_SLAIN
                KILLS_LINE.matches(trimmed) -> {
                    val numbers = KILLS_NUMBERS.find(trimmed)
                    kills = numbers?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
                    killsRequired = numbers?.groupValues?.get(2)?.replace(",", "")?.toIntOrNull()
                    phase = SlayerPhase.GRINDING
                }
                else -> BOSS_TIER_LINE.matchEntire(trimmed)?.let {
                    bossName = it.groupValues[1].trim()
                    tier = it.groupValues[2]
                }
            }
        }

        sawCompletionChat = false
        state = SlayerQuestState(true, bossName, tier, kills, killsRequired, phase)
    }
}
