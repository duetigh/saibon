package dev.saibon.market

import dev.saibon.core.Saibon
import dev.saibon.market.model.ItemModifier
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import java.io.ByteArrayInputStream
import java.util.Base64

/**
 * One decoded auction listing's item id, a stable "modifier signature"
 * string built from whichever enchants/hot-potato-count/recombobulated/star
 * fields are present in `ExtraAttributes` (used for exact-match bucketing —
 * see [AuctionItemDecoder.modifierSignature]), and the same item's
 * [modifiers] as an atomic, per-upgrade list (used by
 * [dev.saibon.market.ModifierValueModel] to price upgrades individually
 * instead of requiring an identical full combo to have sold before). Two
 * listings of the same base item with a different signature/modifier set are
 * meaningfully different items for resale-price purposes (a Sharpness 7
 * sword isn't worth what a bare one is). Both are empty when the item
 * carries none of those fields (a common, expected case for plain items, not
 * a decode failure).
 */
data class DecodedAuctionItem(
    val itemId: String,
    val modifierSignature: String,
    val modifiers: List<ItemModifier> = emptyList()
)

/**
 * Decodes the base64 gzip'd NBT itemstack blob Hypixel's public
 * `/v2/skyblock/auctions` endpoint returns per listing (`item_bytes`),
 * verified live against the real endpoint during planning: it's a standard
 * itemstack NBT blob with `tag.ExtraAttributes.id` holding the Skyblock item
 * id (e.g. `MITHRIL_COAT`), same convention `DataRepository`'s item ids use.
 * [decode]'s modifier fields (`modifier`/`hot_potato_count`/
 * `rarity_upgrades`/`dungeon_item_level`/`enchantments`, plus the newer
 * `gems`/`talisman_enrichment`/`ability_scroll`/`art_of_war_count`/
 * `art_of_peace_applied`/`wood_singularity_count`/`farming_for_dummies_count`)
 * are the commonly documented `ExtraAttributes` keys for these — not yet
 * checked against a live server response the way the base `id` field was,
 * same caveat as `ScoreboardReader`. Each is isolated behind its own small
 * accessor below so a wrong field name is a one-function fix, not a decoder
 * rewrite. [modifierSignature] is derived from the same [itemModifiers] list
 * these accessors feed, so a modifier kind only has to be taught here once to
 * be excluded from "plain item" sale-price bucketing everywhere.
 */
object AuctionItemDecoder {
    /** Convenience wrapper for callers that only need the item id (e.g. [dev.saibon.market.AuctionPriceRepository]'s lowest-BIN sweep, which isn't modifier-aware). */
    fun extraAttributesId(itemBytesBase64: String): String? = decode(itemBytesBase64)?.itemId

    fun decode(itemBytesBase64: String): DecodedAuctionItem? = runCatching {
        val bytes = Base64.getDecoder().decode(itemBytesBase64)
        val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap())
        val item = root.getListOrEmpty("i").getCompoundOrEmpty(0)
        val extraAttributes = item.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes")
        val id = extraAttributes.getString("id").orElse(null) ?: return@runCatching null
        DecodedAuctionItem(id, modifierSignature(extraAttributes), itemModifiers(extraAttributes))
    }.onFailure {
        Saibon.logger.debug("Saibon failed to decode an auction item_bytes blob, skipping", it)
    }.getOrNull()

    /**
     * Exact-match bucketing key — do not change this format lightly, it's a
     * persisted/published cache key ([AuctionSalesHistoryRepository],
     * [AuctionPriceRepository], `data/fair_prices.json`). Derived from
     * [itemModifiers] (each modifier's own `poolKey`, sorted) rather than
     * re-parsing `extraAttributes` separately: an earlier version of this
     * function only checked reforge/hot-potato/recomb/stars/enchants, so a
     * sale carrying gems, talisman enrichment, an ability scroll, or another
     * modifier added later would still yield an empty signature and get
     * miscounted as a *plain* sale — silently inflating the "clean item" fair
     * price for anything commonly sold with one of those upgrades (e.g. a
     * Juju Shortbow with an Ender Slayer scroll). Deriving from the same
     * decomposition [itemModifiers] uses guarantees new modifier kinds can't
     * reintroduce that gap.
     */
    private fun modifierSignature(extraAttributes: CompoundTag): String =
        itemModifiers(extraAttributes).map { it.poolKey }.sorted().joinToString("|")

    /**
     * Atomic per-upgrade decomposition for [dev.saibon.market.ModifierValueModel]
     * — [modifierSignature] is derived *from* this list (each modifier's
     * `poolKey`, sorted and joined), not the other way around, so every kind
     * handled here automatically participates in exact-match bucketing too.
     */
    private fun itemModifiers(extraAttributes: CompoundTag): List<ItemModifier> {
        val modifiers = mutableListOf<ItemModifier>()
        reforgeModifier(extraAttributes)?.let { modifiers += it }
        hotPotatoModifier(extraAttributes)?.let { modifiers += it }
        recombModifier(extraAttributes)?.let { modifiers += it }
        dungeonStarsModifier(extraAttributes)?.let { modifiers += it }
        modifiers += enchantModifiers(extraAttributes)
        modifiers += gemModifiers(extraAttributes)
        enrichmentModifier(extraAttributes)?.let { modifiers += it }
        modifiers += abilityScrollModifiers(extraAttributes)
        artOfWarModifier(extraAttributes)?.let { modifiers += it }
        artOfPeaceModifier(extraAttributes)?.let { modifiers += it }
        woodSingularityModifier(extraAttributes)?.let { modifiers += it }
        farmingForDummiesModifier(extraAttributes)?.let { modifiers += it }
        return modifiers
    }

    private fun reforgeModifier(extra: CompoundTag): ItemModifier? =
        extra.getString("modifier").map { ItemModifier("reforge", it) }.orElse(null)

    private fun hotPotatoModifier(extra: CompoundTag): ItemModifier? {
        val count = extra.getIntOr("hot_potato_count", 0)
        return if (count > 0) ItemModifier("potato", count.toString()) else null
    }

    private fun recombModifier(extra: CompoundTag): ItemModifier? =
        if (extra.getIntOr("rarity_upgrades", 0) > 0) ItemModifier("recomb", "applied") else null

    private fun dungeonStarsModifier(extra: CompoundTag): ItemModifier? {
        val level = extra.getIntOr("dungeon_item_level", 0)
        return if (level > 0) ItemModifier("stars", level.toString()) else null
    }

    private fun enchantModifiers(extra: CompoundTag): List<ItemModifier> {
        val enchantments = extra.getCompoundOrEmpty("enchantments")
        return enchantments.keySet().sorted().map { name -> ItemModifier("ench", "$name${enchantments.getIntOr(name, 0)}") }
    }

    /** `gems` compound convention: one string-valued key per occupied slot holding its quality (e.g. `"COMBAT_0": "PERFECT"`), an optional `"<slot>_gem"` companion key for the gem type, and a non-slot `"unlocked_slots"` list — skipped here since [CompoundTag.getString] safely yields nothing for a key that isn't actually a string tag. */
    private fun gemModifiers(extra: CompoundTag): List<ItemModifier> {
        val gems = extra.getCompoundOrEmpty("gems")
        return gems.keySet()
            .filter { it != "unlocked_slots" && !it.endsWith("_gem") }
            .sorted()
            .mapNotNull { slot ->
                val quality = gems.getString(slot).orElse(null) ?: return@mapNotNull null
                val gemType = gems.getString("${slot}_gem").orElse(null)
                ItemModifier("gem", if (gemType != null) "$slot:$gemType:$quality" else "$slot:$quality")
            }
    }

    private fun enrichmentModifier(extra: CompoundTag): ItemModifier? =
        extra.getString("talisman_enrichment").map { ItemModifier("enrich", it) }.orElse(null)

    private fun abilityScrollModifiers(extra: CompoundTag): List<ItemModifier> {
        val scrolls = extra.getListOrEmpty("ability_scroll")
        return (0 until scrolls.size).mapNotNull { i ->
            scrolls.getString(i).orElse(null)?.let { ItemModifier("scroll", it) }
        }
    }

    private fun artOfWarModifier(extra: CompoundTag): ItemModifier? =
        if (extra.getIntOr("art_of_war_count", 0) > 0) ItemModifier("book", "art_of_war") else null

    private fun artOfPeaceModifier(extra: CompoundTag): ItemModifier? =
        if (extra.getIntOr("art_of_peace_applied", 0) > 0) ItemModifier("book", "art_of_peace") else null

    private fun woodSingularityModifier(extra: CompoundTag): ItemModifier? =
        if (extra.getIntOr("wood_singularity_count", 0) > 0) ItemModifier("upgrade", "wood_singularity") else null

    private fun farmingForDummiesModifier(extra: CompoundTag): ItemModifier? {
        val count = extra.getIntOr("farming_for_dummies_count", 0)
        return if (count > 0) ItemModifier("upgrade", "farming_for_dummies:$count") else null
    }
}
