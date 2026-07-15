package dev.saibon.market

import dev.saibon.core.Saibon
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import java.io.ByteArrayInputStream
import java.util.Base64

/**
 * Decodes the base64 gzip'd NBT itemstack blob Hypixel's public
 * `/v2/skyblock/auctions` endpoint returns per listing (`item_bytes`),
 * verified live against the real endpoint during planning: it's a standard
 * itemstack NBT blob with `tag.ExtraAttributes.id` holding the Skyblock item
 * id (e.g. `MITHRIL_COAT`), same convention `DataRepository`'s item ids use.
 */
object AuctionItemDecoder {
    fun extraAttributesId(itemBytesBase64: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(itemBytesBase64)
        val root = NbtIo.readCompressed(ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap())
        val item = root.getListOrEmpty("i").getCompoundOrEmpty(0)
        val extraAttributes = item.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes")
        extraAttributes.getString("id").orElse(null)
    }.onFailure {
        Saibon.logger.debug("Saibon failed to decode an auction item_bytes blob, skipping", it)
    }.getOrNull()
}
