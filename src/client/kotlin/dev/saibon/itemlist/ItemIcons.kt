package dev.saibon.itemlist

import dev.saibon.data.model.SkyblockItem
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.DyedItemColor

/**
 * Resolves a [SkyblockItem.material] id to the vanilla [ItemStack] used to
 * render its icon. This only ever sets real vanilla item id + data
 * components (dye color, etc.) — never a Saibon-side texture swap — so the
 * result renders through the ordinary vanilla/resource-pack item pipeline
 * and picks up whatever texture/CIT pack the player has active, the same as
 * any other item in their inventory.
 */
object ItemIcons {
    fun stackFor(item: SkyblockItem): ItemStack {
        val identifier = Identifier.tryParse(item.material) ?: return ItemStack(BuiltInRegistries.ITEM.getValue(DEFAULT))
        val stack = ItemStack(BuiltInRegistries.ITEM.getValue(identifier))
        item.color?.let { rgb -> stack.set(DataComponents.DYED_COLOR, DyedItemColor(rgb)) }
        return stack
    }

    private val DEFAULT = Identifier.fromNamespaceAndPath("minecraft", "paper")
}
