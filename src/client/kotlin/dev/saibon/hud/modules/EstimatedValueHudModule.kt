package dev.saibon.hud.modules

import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.market.value.EstimatedValueLine
import dev.saibon.market.value.EstimatedValueResult
import dev.saibon.market.value.ValueCategory
import dev.saibon.market.value.ValueFormat
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * SkyHanni-style full cost breakdown for whichever item is currently under
 * the mouse — driven entirely by
 * [dev.saibon.market.value.EstimatedValueTooltipAppender]'s
 * `ItemTooltipCallback` registration calling [onHover] every frame a
 * tooltip is shown; there's no separate hover-detection hook. [render]
 * hides itself once [onHover] hasn't fired for [STALE_AFTER_MILLIS] (i.e.
 * the mouse moved off any item) — the same "no active state = no-op
 * render" shape [FlipAlertHudModule] uses, just driven by a rolling
 * staleness window instead of a fixed post-event expiry, since there's no
 * single "stopped hovering" event to key off of.
 */
object EstimatedValueHudModule : HudModule {
    override val id = "estimated_value_breakdown"
    override val title = "Estimated Value Breakdown"
    override val defaultState = HudElementState(anchor = HudAnchor.MIDDLE_RIGHT, offsetX = 8, offsetY = 0)

    private const val STALE_AFTER_MILLIS = 150L
    private const val LINE_HEIGHT = 10
    private const val PADDING = 4
    private const val MAX_ENCHANT_LINES = 6

    private const val NAME_COLOR = 0xFFFFAA00.toInt()
    private const val TOTAL_COLOR = 0xFFFF5555.toInt()
    private const val COLLAPSED_COLOR = 0xFFAAAAAA.toInt()
    private const val CHECKMARK_COLOR = 0xFF55FF55.toInt()

    /** Category -> (header color, line color). Categories absent here (e.g. [ValueCategory.BASE]) render flat with no group header. */
    private val CATEGORY_COLOR: Map<ValueCategory, Int> = mapOf(
        ValueCategory.BASE to 0xFFE0E0E0.toInt(),
        ValueCategory.BASE_PART to 0xFF55FFFF.toInt(),
        ValueCategory.REFORGE to 0xFF00AAAA.toInt(),
        ValueCategory.BOOLEAN_UPGRADE to 0xFFE0E0E0.toInt(),
        ValueCategory.STARS to 0xFFFF55FF.toInt(),
        ValueCategory.POTATO to 0xFFFFAA00.toInt(),
        ValueCategory.ABILITY_SCROLL to 0xFF5555FF.toInt(),
        ValueCategory.GEM_SLOT to 0xFFAA00AA.toInt(),
        ValueCategory.GEM to 0xFFFF55FF.toInt(),
        ValueCategory.ENCHANTMENT to 0xFF55FF55.toInt(),
        ValueCategory.MISC to 0xFFAAAAAA.toInt()
    )

    /** Group header text for categories that get a subtotal + indented children. Categories not listed render every line flat at top level instead. */
    private val GROUP_HEADER: Map<ValueCategory, String> = mapOf(
        ValueCategory.BASE_PART to "Crafted from",
        ValueCategory.ABILITY_SCROLL to "Ability Scrolls",
        ValueCategory.GEM_SLOT to "Gemstone Slots",
        ValueCategory.GEM to "Gemstones Applied",
        ValueCategory.ENCHANTMENT to "Enchantments"
    )

    /** Render order — categories not present on a given item are skipped entirely. */
    private val CATEGORY_ORDER = listOf(
        ValueCategory.BASE, ValueCategory.BASE_PART, ValueCategory.REFORGE, ValueCategory.BOOLEAN_UPGRADE,
        ValueCategory.STARS, ValueCategory.POTATO, ValueCategory.ABILITY_SCROLL, ValueCategory.GEM_SLOT,
        ValueCategory.GEM, ValueCategory.ENCHANTMENT, ValueCategory.MISC
    )

    private var active: EstimatedValueResult? = null
    private var lastSeenMillis: Long = 0

    /** Called by [dev.saibon.market.value.EstimatedValueTooltipAppender] every time a tooltip is rendered — doubles as this module's hover signal. */
    fun onHover(result: EstimatedValueResult) {
        active = result
        lastSeenMillis = System.currentTimeMillis()
    }

    private fun currentlyVisible(): EstimatedValueResult? {
        val result = active ?: return null
        if (System.currentTimeMillis() - lastSeenMillis > STALE_AFTER_MILLIS) {
            active = null
            return null
        }
        return result
    }

    override fun measure(): HudSize {
        val result = currentlyVisible() ?: return HudSize(1, 1)
        val font = Minecraft.getInstance().font
        val lines = displayLines(result)
        return HudSize(lines.maxOf { font.width(it.first) } + PADDING * 2, lines.size * LINE_HEIGHT + PADDING * 2)
    }

    override fun render(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val result = currentlyVisible() ?: return
        val font = Minecraft.getInstance().font
        val lines = displayLines(result)
        val width = lines.maxOf { font.width(it.first) } + PADDING * 2
        val height = lines.size * LINE_HEIGHT + PADDING * 2

        Panel.draw(extractor, 0, 0, width, height)
        lines.forEachIndexed { index, (text, color) ->
            extractor.text(font, text, PADDING, PADDING + index * LINE_HEIGHT, color, false)
        }
    }

    /**
     * SkyHanni-style grouped breakdown: categories with a [GROUP_HEADER]
     * entry (gemstones, ability scrolls, enchantments, drill/forge parts)
     * render as a colored header line carrying the category's subtotal
     * followed by indented children; every other category renders its lines
     * flat at top level, in [CATEGORY_ORDER]. A long enchantment list is
     * capped at [MAX_ENCHANT_LINES] (highest-cost first), folding the rest
     * into a single "N more enchantments (sum)" line rather than dominating
     * the tooltip.
     */
    private fun displayLines(result: EstimatedValueResult): List<Pair<String, Int>> = buildList {
        add(result.itemName to NAME_COLOR)

        val grouped = result.lines.groupBy { it.category }
        for (category in CATEGORY_ORDER) {
            val lines = grouped[category] ?: continue
            val color = CATEGORY_COLOR[category] ?: COLLAPSED_COLOR
            val header = GROUP_HEADER[category]
            if (header == null) {
                lines.forEach { add(flatLineText(it) to color) }
                continue
            }

            val subtotal = lines.sumOf { it.cost }
            add("$header: ${formatCompact(subtotal)}" to color)
            val sorted = lines.sortedByDescending { it.cost }
            val visible = if (category == ValueCategory.ENCHANTMENT) sorted.take(MAX_ENCHANT_LINES) else sorted
            visible.forEach { add("  ${it.label} (${formatCompact(it.cost)})" to color) }
            val hidden = sorted.drop(visible.size)
            if (hidden.isNotEmpty()) {
                add("  ${hidden.size} more enchantments (${formatCompact(hidden.sumOf { it.cost })})" to COLLAPSED_COLOR)
            }
        }

        add("Total: ${formatCompact(result.total)} coins${if (result.isPartial) " (partial)" else ""}" to TOTAL_COLOR)
    }

    private fun flatLineText(line: EstimatedValueLine): String =
        if (line.checkmark) "✓ ${line.label} (${formatCompact(line.cost)})" else "${line.label}: ${formatCompact(line.cost)}"

    private fun formatCompact(value: Double): String = ValueFormat.compact(value)
}
