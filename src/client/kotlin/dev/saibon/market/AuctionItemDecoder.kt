package dev.saibon.market

import dev.saibon.core.Saibon
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import java.io.ByteArrayInputStream
import java.util.Base64

/**
 * One decoded auction listing's item id plus a stable "modifier signature"
 * string built from whichever enchants/hot-potato-count/recombobulated/star
 * fields are present in `ExtraAttributes` — two listings of the same base
 * item with different signatures are meaningfully different items for
 * resale-price purposes (a Sharpness 7 sword isn't worth what a bare one is).
 * Empty string when the item carries none of those fields (a common,
 * expected case for plain/unenchanted items, not a decode failure).
 */
data class DecodedAuctionItem(val itemId: String, val modifierSignature: String)

/**
 * Decodes the base64 gzip'd NBT itemstack blob Hypixel's public
 * `/v2/skyblock/auctions` endpoint returns per listing (`item_bytes`),
 * verified live against the real endpoint during planning: it's a standard
 * itemstack NBT blob with `tag.ExtraAttributes.id` holding the Skyblock item
 * id (e.g. `MITHRIL_COAT`), same convention `DataRepository`'s item ids use.
 * [decode]'s modifier-signature fields (`modifier`/`hot_potato_count`/
 * `rarity_upgrades`/`dungeon_item_level`/`enchantments`) are the commonly
 * documented `ExtraAttributes` keys for these — not yet checked against a
 * live server response the way the base `id` field was, same caveat as
 * `ScoreboardReader`.
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
        DecodedAuctionItem(id, modifierSignature(extraAttributes))
    }.onFailure {
        Saibon.logger.debug("Saibon failed to decode an auction item_bytes blob, skipping", it)
    }.getOrNull()

    private fun modifierSignature(extraAttributes: net.minecraft.nbt.CompoundTag): String {
        val parts = mutableListOf<String>()

        extraAttributes.getString("modifier").ifPresent { parts += "reforge:$it" }

        val hotPotato = extraAttributes.getIntOr("hot_potato_count", 0)
        if (hotPotato > 0) parts += "potato:$hotPotato"

        val rarityUpgrades = extraAttributes.getIntOr("rarity_upgrades", 0)
        if (rarityUpgrades > 0) parts += "recomb"

        val dungeonLevel = extraAttributes.getIntOr("dungeon_item_level", 0)
        if (dungeonLevel > 0) parts += "stars:$dungeonLevel"

        val enchantments = extraAttributes.getCompoundOrEmpty("enchantments")
        val enchantParts = enchantments.keySet().sorted().map { name -> "$name${enchantments.getIntOr(name, 0)}" }
        if (enchantParts.isNotEmpty()) parts += "ench:${enchantParts.joinToString(",")}"

        return parts.joinToString("|")
    }
}
