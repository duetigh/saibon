package dev.saibon.mining

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import java.util.concurrent.atomic.AtomicBoolean

/** Current/max fuel reading for whichever drill is currently held, plus the derived percentage for the bar fill. */
data class FuelState(val current: Int, val max: Int) {
    val percent: Float get() = if (max <= 0) 0f else (current.toFloat() / max).coerceIn(0f, 1f)
}

/**
 * Reads the currently held item's drill fuel level — the "fuel bar"
 * sub-bullet of the Mining/Dwarven overlay bullet group (`NEU_FEATURE_PARITY.md`
 * #4) — from data the client already has for its own held item, no new mixins
 * or menu/container reads: [net.minecraft.world.entity.LivingEntity.getMainHandItem]'s
 * `DataComponents.CUSTOM_DATA` tag for the live `drill_fuel` counter, and
 * `DataComponents.LORE` for the tank capacity, which Hypixel only ever exposes
 * as display text (there's no separate "max fuel" NBT field).
 *
 * Unlike [CommissionTracker]/`PowderTracker`/[ForgeTracker], SkyHanni has no
 * equivalent feature to cross-check this against — a `git clone` + grep of its
 * source turned up nothing under "fuel"/"drill" for mining at all — so this is
 * instead a direct read of NotEnoughUpdates' own still-current `FuelBar.java`
 * (`overlays/FuelBar.java`, confirmed present in NEU's real GitHub source, not
 * recalled from training data): `drill_fuel` is the ExtraAttributes/CUSTOM_DATA
 * key for current fuel, and the max is parsed off a lore line matching
 * `"Fuel: <current>/<max>"` where `<max>` may carry a `k`/`m`/`b` suffix. NEU's
 * own regex is written against 1.8.9-era formatted (color-coded) text; this
 * reader works against this codebase's already-color-stripped lore lines
 * ([net.minecraft.network.chat.Component.getString], the same convention
 * [ForgeTracker]/`PowderTracker` use), so it's the same underlying line shape
 * adapted to a different text encoding rather than a different pattern.
 *
 * The item allowlist ([DRILL_ITEM_IDS]) is deliberately an explicit list of
 * real, currently-populated `data/items.json` ids rather than NEU's substring
 * check (`internalname.contains("_DRILL_")`): that substring also matches
 * `MITHRIL_DRILL_ENGINE`/`TITANIUM_DRILL_ENGINE`/the polished-engine items in
 * this codebase's real item dataset (they weren't separate craftable items
 * with `_DRILL_`-containing ids in NEU's original 1.8.9-era item set), which
 * would misfire the tracker on a drill *part* sitting in the player's hand.
 *
 * No location gating (Dwarven Mines/Crystal Hollows/mineshaft) like NEU's
 * `SBInfo.getLocation()` check — this codebase has no island/location tracker
 * yet (see doc comments on other trackers in this package). Gating on
 * "holding an item with a real `drill_fuel` NBT value" is actually a tighter
 * signal than location alone (a drill NBT value only exists on drills, and
 * only means something while it's in hand), so the omission isn't a gap here.
 *
 * **Unverified against a live server** (this sandbox can't reach one) — plus
 * the added caveat that the lore line's *exact* modern text (spacing, suffix
 * usage, whether current fuel is ever itself abbreviated) is only confirmed
 * against NEU's own regex, not a live Hypixel session; a non-matching lore
 * line degrades to "no max fuel known" (module renders nothing) rather than
 * guessing a number, the same "don't fabricate a value" rule
 * [dev.saibon.market.value.EstimatedItemValueCalculator] follows throughout.
 */
object FuelTracker {
    private val DRILL_ITEM_IDS = setOf(
        "GEMSTONE_DRILL_1", "GEMSTONE_DRILL_2", "GEMSTONE_DRILL_3", "GEMSTONE_DRILL_4",
        "MITHRIL_DRILL_1", "MITHRIL_DRILL_2",
        "TITANIUM_DRILL_1", "TITANIUM_DRILL_2", "TITANIUM_DRILL_3", "TITANIUM_DRILL_4",
        "DIVAN_DRILL"
    )

    private val FUEL_LORE_LINE = Regex("""(?i)Fuel:\s*[\d,.]*\s*/\s*([\d,.]+[kKmMbB]?)""")
    private val ABBREVIATED_NUMBER = Regex("""^([\d,.]+)([kKmMbB]?)$""")

    private val initialized = AtomicBoolean(false)

    @Volatile private var state: FuelState? = null

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { poll() })
    }

    fun currentState(): FuelState? = state

    private fun poll() {
        state = computeState()
    }

    private fun computeState(): FuelState? {
        val player = Minecraft.getInstance().player ?: return null
        val held = player.mainHandItem
        if (held.isEmpty) return null

        val extraAttributes = held.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        val itemId = extraAttributes.getString("id").orElse(null) ?: return null
        if (itemId !in DRILL_ITEM_IDS) return null

        val currentFuel = extraAttributes.getIntOr("drill_fuel", -1)
        if (currentFuel < 0) return null

        val loreLines = held.get(DataComponents.LORE)?.lines()?.map { it.string } ?: emptyList()
        val maxFuel = loreLines.firstNotNullOfOrNull { line ->
            FUEL_LORE_LINE.find(line)?.groupValues?.get(1)?.let(::parseAbbreviated)
        } ?: return null
        if (maxFuel <= 0) return null

        return FuelState(currentFuel, maxFuel.coerceAtLeast(currentFuel))
    }

    private fun parseAbbreviated(text: String): Int? {
        val match = ABBREVIATED_NUMBER.matchEntire(text.trim()) ?: return null
        val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase()) {
            "k" -> 1_000.0
            "m" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (number * multiplier).toInt()
    }
}
