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
 * components (dye color, player-head profile texture) — never a Saibon-side
 * texture swap — so the result renders through the ordinary vanilla/
 * resource-pack item pipeline and picks up whatever texture pack the player
 * has active, the same as any other item in their inventory. Custom head
 * textures ([SkyblockItem.skullTexture]) use the same real Mojang skin data
 * a vanilla player head carries, resolved client-side, so those always
 * render regardless of texture pack.
 *
 * Deliberately does NOT set `minecraft:item_model` from
 * [SkyblockItem.itemModel]: that field points at a `hypixel_skyblock:
 * item/...` id that only resolves through Hypixel's own server resource
 * pack, and plenty of players (this mod's own author included) run with
 * that pack disabled in favor of their own texture pack. Setting it would
 * render as the missing-texture checkerboard for anyone without Hypixel's
 * pack active. Falling back to the plain vanilla material means every item
 * always renders as *something* real and themeable by the player's own
 * pack, at the cost of not showing Hypixel's reskin (e.g. an Abiphone shows
 * as paper, not a phone icon) for players who do have that pack loaded.
 */
object ItemIcons {
    fun stackFor(item: SkyblockItem): ItemStack {
        val identifier = Identifier.tryParse(item.material) ?: return ItemStack(BuiltInRegistries.ITEM.getValue(DEFAULT))
        val stack = ItemStack(BuiltInRegistries.ITEM.getValue(identifier))
        item.color?.let { rgb -> stack.set(DataComponents.DYED_COLOR, DyedItemColor(rgb)) }
        item.skullTexture?.let { texture -> stack.set(DataComponents.PROFILE, resolvedProfile(item.id, texture)) }
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
