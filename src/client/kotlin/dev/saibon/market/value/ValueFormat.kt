package dev.saibon.market.value

import kotlin.math.abs

/** Shared coin-amount formatting for [EstimatedValueResult] consumers ([dev.saibon.hud.modules.EstimatedValueHudModule], [EstimatedValueTooltipAppender]) — kept in one place so the HUD breakdown and the plain tooltip line always agree. */
object ValueFormat {
    /** Compact SkyHanni-style number formatting (`8.2M`, `129M`, `1.58B`) instead of a full comma-grouped integer — trims a trailing `.0` so whole values (`1M`, not `1.0M`) match the reference layout. */
    fun compact(value: Double): String {
        val sign = if (value < 0) "-" else ""
        val magnitude = abs(value)
        val (scaled, suffix) = when {
            magnitude >= 1_000_000_000 -> magnitude / 1_000_000_000 to "B"
            magnitude >= 1_000_000 -> magnitude / 1_000_000 to "M"
            magnitude >= 1_000 -> magnitude / 1_000 to "k"
            else -> return "$sign${"%,.0f".format(magnitude)}"
        }
        val decimals = if (scaled < 100) 1 else 0
        val formatted = "%.${decimals}f".format(scaled).removeSuffix(".0")
        return "$sign$formatted$suffix"
    }
}
