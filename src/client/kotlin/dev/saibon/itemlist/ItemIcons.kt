package dev.saibon.itemlist

import dev.saibon.data.model.SkyblockItem
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/** Resolves a [SkyblockItem.material] id to the vanilla [ItemStack] used to render its icon. */
object ItemIcons {
    fun stackFor(item: SkyblockItem): ItemStack {
        val identifier = Identifier.tryParse(item.material) ?: return ItemStack(BuiltInRegistries.ITEM.getValue(DEFAULT))
        return ItemStack(BuiltInRegistries.ITEM.getValue(identifier))
    }

    private val DEFAULT = Identifier.fromNamespaceAndPath("minecraft", "paper")
}
