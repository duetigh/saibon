package dev.saibon.hud.modules

import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.slayer.SlayerOutcome
import dev.saibon.slayer.SlayerPhase
import dev.saibon.slayer.SlayerTracker
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * NEU parity item #4 "Slayer overlay — boss phase/progress tracker,
 * quest-fail detection": a small persistent panel showing the active
 * quest's boss/tier and current phase (grinding kills, boss spawned, boss
 * slain), plus a few-second toast when the quest ends — reusing
 * [SlayerTracker] for all state, the same read-only-render shape as
 * [FlipAlertHudModule]/[EstimatedValueHudModule]. Renders nothing when no
 * quest is active and no recent toast is pending.
 */
object SlayerHudModule : HudModule {
    override val id = "slayer_quest_tracker"
    override val title = "Slayer Quest Tracker"
    override val defaultState = HudElementState(anchor = HudAnchor.TOP_RIGHT, offsetX = 8, offsetY = 60)

    private const val LINE_HEIGHT = 10
    private const val PADDING = 4
    private const val TOAST_DISPLAY_MILLIS = 5000L

    private const val NAME_COLOR = 0xFFFFAA00.toInt()
    private const val PROGRESS_COLOR = 0xFFE0E0E0.toInt()
    private const val BOSS_SPAWNED_COLOR = 0xFFFF5555.toInt()
    private const val BOSS_SLAIN_COLOR = 0xFF55FF55.toInt()
    private const val COMPLETED_COLOR = 0xFF55FF55.toInt()
    private const val FAILED_COLOR = 0xFFFF5555.toInt()

    /** Sample content sized like a real quest readout, for [editorPreviewSize] only. */
    private val PREVIEW_LINES = listOf("Revenant Horror V", "Kills: 3/9")

    private var toastText: String? = null
    private var toastColor: Int = 0
    private var toastExpiresAtMillis: Long = 0

    fun init() {
        SlayerTracker.onOutcome { outcome -> showToast(outcome) }
    }

    private fun showToast(outcome: SlayerOutcome) {
        when (outcome) {
            SlayerOutcome.COMPLETED -> {
                toastText = "Slayer quest complete!"
                toastColor = COMPLETED_COLOR
            }
            SlayerOutcome.FAILED -> {
                toastText = "Slayer quest failed"
                toastColor = FAILED_COLOR
            }
        }
        toastExpiresAtMillis = System.currentTimeMillis() + TOAST_DISPLAY_MILLIS
    }

    private fun activeToast(): Pair<String, Int>? {
        val text = toastText ?: return null
        if (System.currentTimeMillis() > toastExpiresAtMillis) {
            toastText = null
            return null
        }
        return text to toastColor
    }

    override fun measure(): HudSize {
        val lines = displayLines()
        if (lines.isEmpty()) return HudSize(1, 1)
        return sizeFor(lines.map { it.first })
    }

    override fun editorPreviewSize(): HudSize = sizeFor(PREVIEW_LINES)

    private fun sizeFor(lines: List<String>): HudSize {
        val font = Minecraft.getInstance().font
        val width = lines.maxOf { font.width(it) } + PADDING * 2
        return HudSize(width, lines.size * LINE_HEIGHT + PADDING * 2)
    }

    override fun render(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val lines = displayLines()
        if (lines.isEmpty()) return
        val font = Minecraft.getInstance().font
        val width = lines.maxOf { font.width(it.first) } + PADDING * 2
        val height = lines.size * LINE_HEIGHT + PADDING * 2

        Panel.draw(extractor, 0, 0, width, height)
        lines.forEachIndexed { index, (text, color) ->
            extractor.text(font, text, PADDING, PADDING + index * LINE_HEIGHT, color, false)
        }
    }

    private fun displayLines(): List<Pair<String, Int>> = buildList {
        val state = SlayerTracker.currentState()
        if (state.active) {
            val header = listOfNotNull(state.bossName, state.tier).joinToString(" ").ifEmpty { "Slayer Quest" }
            add(header to NAME_COLOR)
            when (state.phase) {
                SlayerPhase.GRINDING -> {
                    val progress = if (state.kills != null && state.killsRequired != null) {
                        "Kills: ${state.kills}/${state.killsRequired}"
                    } else {
                        "Grinding..."
                    }
                    add(progress to PROGRESS_COLOR)
                }
                SlayerPhase.BOSS_SPAWNED -> add("Boss spawned - fight!" to BOSS_SPAWNED_COLOR)
                SlayerPhase.BOSS_SLAIN -> add("Boss slain!" to BOSS_SLAIN_COLOR)
            }
        }
        activeToast()?.let { add(it) }
    }
}
