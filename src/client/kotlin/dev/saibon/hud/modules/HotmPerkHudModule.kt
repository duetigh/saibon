package dev.saibon.hud.modules

import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.mining.HotmPerkTracker
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * NEU parity item #4 "Mining / Dwarven overlay" — "Heart of the Mountain perk
 * preview" sub-feature: a small panel previewing the perks the player has
 * actually invested points into, reusing [HotmPerkTracker] for all state (see
 * its doc comment for how the tree is read from the real HotM menu, and why
 * that only happens while the menu is open). Renders nothing until the player
 * has opened the real Heart of the Mountain menu at least once this session —
 * this is a "preview of previously-seen data," the same shape as the storage/
 * minion-helper tools `NEU_FEATURE_PARITY.md` #6 describes, not a live feed.
 *
 * Capped to [MAX_PERKS_SHOWN] invested (level > 0) perks, sorted by level
 * descending, to keep this panel from growing to the size of the full ~35-perk
 * tree — a "+N more" line covers the rest. Crystal Hollows/Glacite waypoints
 * and the chest highlighter are still left for follow-up passes.
 *
 * **Unverified against a live server** (this sandbox can't reach one), same
 * caveat as every other tracker in this package.
 */
object HotmPerkHudModule : HudModule {
    override val id = "hotm_perk_preview"
    override val title = "HotM Perk Preview"
    override val defaultState = HudElementState(anchor = HudAnchor.TOP_LEFT, offsetX = 8, offsetY = 260)

    private const val MAX_PERKS_SHOWN = 12
    private const val LINE_HEIGHT = 10
    private const val PADDING = 4

    private const val HEADER_COLOR = 0xFFFFAA00.toInt()
    private const val TOKEN_COLOR = 0xFF55FFFF.toInt()
    private const val PERK_COLOR = 0xFFE0E0E0.toInt()
    private const val MAXED_COLOR = 0xFF55FF55.toInt()
    private const val MORE_COLOR = 0xFF808080.toInt()

    /** Sample content sized like a real read tree, for [editorPreviewSize] only. */
    private val PREVIEW_LINES = listOf(
        "Heart of the Mountain (2m ago)",
        "Tokens: 15",
        "Mining Speed: 50/50",
        "Mining Fortune: 42/50",
        "+8 more"
    )

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
        val snapshot = HotmPerkTracker.current() ?: return@buildList
        val elapsedMinutes = (System.currentTimeMillis() - snapshot.readAtMillis) / 60_000L
        val age = if (elapsedMinutes <= 0) "just now" else "${elapsedMinutes}m ago"
        add("Heart of the Mountain ($age)" to HEADER_COLOR)

        snapshot.tokensAvailable?.let { add("Tokens: $it" to TOKEN_COLOR) }

        val invested = snapshot.perks.filter { it.unlocked && it.level > 0 }.sortedByDescending { it.level }
        if (invested.isEmpty()) return@buildList

        for (perk in invested.take(MAX_PERKS_SHOWN)) {
            val color = if (perk.level >= perk.maxLevel) MAXED_COLOR else PERK_COLOR
            add("${perk.name}: ${perk.level}/${perk.maxLevel}" to color)
        }
        val remaining = invested.size - MAX_PERKS_SHOWN
        if (remaining > 0) add("+$remaining more" to MORE_COLOR)
    }
}
