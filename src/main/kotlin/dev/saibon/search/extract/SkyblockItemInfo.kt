package dev.saibon.search.extract

/** One enchant line found in an item's lore, e.g. "Protection V" -> ("Protection", 5). */
data class EnchantEntry(val name: String, val level: Int)

/**
 * Structured fields pulled from an [net.minecraft.world.item.ItemStack]'s
 * display name + lore by [SkyblockItemExtractor]. Regex/text-based rather
 * than reading vanilla enchantment data components, since Hypixel SkyBlock
 * items carry their real stats as Hypixel-authored lore text, not vanilla
 * enchantments — see [SkyblockItemExtractor]'s doc comment.
 */
data class SkyblockItemInfo(
    val name: String,
    val loreText: String,
    val rarity: String?,
    val reforge: String?,
    val enchants: List<EnchantEntry>,
    val stars: Int,
    val type: String?
) {
    /** Display name + every lore line, lowercased once, for bare-term substring matching. */
    val searchableText: String = (name + " " + loreText).lowercase()
}
