package dev.saibon.itemlist

import com.google.common.collect.HashMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import dev.saibon.data.model.SkyblockItem
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ResolvableProfile
import java.util.UUID

/**
 * Resolves a [SkyblockItem.material] id to the vanilla [ItemStack] used to
 * render its icon. This only ever sets real vanilla item id + data
 * components (dye color, player-head profile texture, item model) — never a
 * Saibon-side texture swap — so the result renders through the ordinary
 * vanilla/resource-pack item pipeline and picks up whatever texture/CIT pack
 * the player has active, the same as any other item in their inventory.
 * Custom head textures ([SkyblockItem.skullTexture]) use the same real
 * Mojang skin data a vanilla player head carries, resolved client-side.
 * [SkyblockItem.itemModel] just points `minecraft:item_model` at the same
 * `hypixel_skyblock:item/...` id the real game uses, which only resolves to
 * anything if the player already has Hypixel's own server resource pack
 * active (as virtually every SkyBlock player does) — no Hypixel resource-
 * pack assets are ever bundled or referenced by Saibon itself.
 */
object ItemIcons {
    fun stackFor(item: SkyblockItem): ItemStack {
        val identifier = Identifier.tryParse(item.material) ?: return ItemStack(BuiltInRegistries.ITEM.getValue(DEFAULT))
        val stack = ItemStack(BuiltInRegistries.ITEM.getValue(identifier))
        item.color?.let { rgb -> stack.set(DataComponents.DYED_COLOR, DyedItemColor(rgb)) }
        item.skullTexture?.let { texture -> stack.set(DataComponents.PROFILE, resolvedProfile(item.id, texture)) }
        item.itemModel?.let { model -> Identifier.tryParse(model)?.let { stack.set(DataComponents.ITEM_MODEL, it) } }
        return stack
    }

    private fun resolvedProfile(itemId: String, texture: String): ResolvableProfile {
        val multimap = HashMultimap.create<String, Property>()
        multimap.put("textures", Property("textures", texture))
        val profile = GameProfile(UUID.nameUUIDFromBytes(itemId.toByteArray()), "", PropertyMap(multimap))
        return ResolvableProfile.createResolved(profile)
    }

    private val DEFAULT = Identifier.fromNamespaceAndPath("minecraft", "paper")
}
