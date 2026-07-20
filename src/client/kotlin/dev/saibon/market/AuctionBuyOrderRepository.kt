package dev.saibon.market

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import java.util.concurrent.ConcurrentHashMap

/**
 * Passive cache of the Auction House's "Create Buy Order" screen — Hypixel's
 * per-SKU order book for AH-only commodities (enchanted books, potions,
 * runes, ...), the AH-side equivalent of the Bazaar's `buy_summary` top buy
 * order. Unlike Bazaar orders, Hypixel's public API has **no endpoint** for
 * this data (verified: only `/v2/skyblock/bazaar` exposes an order book) —
 * the only way to see it at all is the real in-game GUI shown when a player
 * opens it. [PLAN.md]'s non-negotiable against automating gameplay rules out
 * ever opening that screen ourselves, so this only ever *reads* the lore
 * already sent to the client when the *player* opens it manually — same
 * zero-click, zero-mutation technique [dev.saibon.market.ui.LiveListingMatcher]
 * uses for live BIN listing prices. That means coverage is inherently
 * partial (only items you've actually browsed get a cached price) — see
 * [dev.saibon.market.flip.IngredientPriceResolver], which treats a cache
 * miss here as "no data," falling through to the AH sale-history estimate
 * exactly like before.
 *
 * TODO verify against a live server: the screen title and the "Top Orders"
 * lore line shape are inferred from the pasted reference screenshot, not
 * confirmed live from this sandbox (no reachable Minecraft/Hypixel session
 * here) — same caveat [dev.saibon.market.ui.MarketMenuOverlay] already
 * carries for the regular AH GUI.
 */
object AuctionBuyOrderRepository {
    private val SCREEN_TITLE = Regex("Create Buy Order", RegexOption.IGNORE_CASE)

    /** Matches the first (best/top) `"<price> coins each"` line in the order book's lore — that top line is what a patient buyer's order would need to beat, the AH analogue of Bazaar's `topBuyOrderPrice`. */
    private val TOP_ORDER_LINE = Regex("([\\d,]+(?:\\.\\d+)?)\\s*coins each", RegexOption.IGNORE_CASE)

    /** `"<name> <roman numeral level>"` — how enchant book display names are parsed back into the `ENCHANTMENT_<NAME>_<LEVEL>` itemId convention [dev.saibon.market.value.EstimatedItemValueCalculator] already prices via. Only covers enchants whose display name matches their internal name word-for-word (the overwhelming majority) — irregular Ultimate-enchant renames are a genuine cache-miss gap, not a guess. */
    private val ENCHANT_DISPLAY_NAME = Regex("^(.+?)\\s+([IVXLCDM]+)$")

    /** A cached price older than this is more likely to be stale/misleading than a fresh sale-history estimate — Bazaar's own order book can move within minutes, and AH buy orders are no different. */
    private const val MAX_AGE_MILLIS = 6L * 3_600_000

    private data class CachedOrder(val price: Double, val capturedAtMillis: Long)
    private val cache = ConcurrentHashMap<String, CachedOrder>()

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            if (!isOnHypixel()) return@register
            if (!SCREEN_TITLE.containsMatchIn(screen.title.string)) return@register
            runCatching { capture(screen) }.onFailure { Saibon.logger.warn("Saibon AH buy-order capture failed", it) }
        }
    }

    /** Cached top buy-order price for [itemId], or `null` on a cache miss or stale entry — see [IngredientPriceResolver] for how this fits into the overall resolution order. */
    fun cachedTopBuyOrder(itemId: String): Double? {
        val entry = cache[itemId.uppercase()] ?: return null
        if (System.currentTimeMillis() - entry.capturedAtMillis > MAX_AGE_MILLIS) return null
        return entry.price.takeIf { it > 0 }
    }

    private fun isOnHypixel(): Boolean =
        Minecraft.getInstance().currentServer?.ip?.contains("hypixel.net", ignoreCase = true) == true

    private fun capture(screen: AbstractContainerScreen<*>) {
        val player = Minecraft.getInstance().player
        val slot = screen.menu.slots.firstOrNull { slot ->
            slot.container !== player?.inventory && !slot.item.isEmpty &&
                loreLines(slot).any { it.contains("Top Orders", ignoreCase = true) }
        } ?: return

        val lore = loreLines(slot)
        val topPrice = lore.firstNotNullOfOrNull { line -> TOP_ORDER_LINE.find(line)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() }
            ?: return
        val itemId = resolveItemId(slot.item.hoverName.string) ?: return

        cache[itemId] = CachedOrder(topPrice, System.currentTimeMillis())
    }

    private fun loreLines(slot: net.minecraft.world.inventory.Slot): List<String> =
        slot.item.get(DataComponents.LORE)?.lines()?.map { it.string } ?: emptyList()

    private fun resolveItemId(displayName: String): String? {
        DataRepository.allItems().firstOrNull { it.name.equals(displayName, ignoreCase = true) }?.let { return it.id.uppercase() }
        return parseEnchantItemId(displayName)
    }

    private fun parseEnchantItemId(displayName: String): String? {
        val match = ENCHANT_DISPLAY_NAME.find(displayName.trim()) ?: return null
        val level = fromRoman(match.groupValues[2]) ?: return null
        val name = match.groupValues[1].trim().uppercase().replace(Regex("[^A-Z0-9]+"), "_")
        if (name.isEmpty()) return null
        return "ENCHANTMENT_${name}_$level"
    }

    private val ROMAN_VALUES = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
        100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
        10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
    )

    private fun fromRoman(text: String): Int? {
        var result = 0
        var index = 0
        for ((value, symbol) in ROMAN_VALUES) {
            while (text.startsWith(symbol, index)) {
                result += value
                index += symbol.length
            }
        }
        return result.takeIf { index == text.length && it > 0 }
    }
}
