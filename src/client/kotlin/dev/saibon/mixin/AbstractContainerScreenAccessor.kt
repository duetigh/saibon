package dev.saibon.mixin

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker

/**
 * [AbstractContainerScreen.leftPos]/`topPos` are `protected` with no public
 * getter — the one place in the whole inventory-search-overlay design that
 * genuinely needs a mixin rather than a Fabric API event, since it's the
 * only way to turn a [net.minecraft.world.inventory.Slot]'s relative
 * `x`/`y` into an absolute on-screen rect for the highlight overlay.
 *
 * [invokeSlotClicked] is [dev.saibon.market.ui.BazaarActionNavigator]'s only
 * way to drive a real click through the exact same code path a physical
 * mouse click would (client-side prediction + the server packet), since
 * `slotClicked` is `protected`. Confirmed live against the decompiled 26.2
 * jar: this MC build renamed the historical `ClickType` enum to
 * `net.minecraft.world.inventory.ContainerInput` (same PICKUP/QUICK_MOVE/
 * SWAP/CLONE/THROW/QUICK_CRAFT/PICKUP_ALL values) — don't pattern-match on
 * the older `ClickType` name from pre-26.x tutorials/training data.
 */
@Mixin(AbstractContainerScreen::class)
interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    fun getLeftPos(): Int

    @Accessor("topPos")
    fun getTopPos(): Int

    @Accessor("imageWidth")
    fun getImageWidth(): Int

    @Accessor("imageHeight")
    fun getImageHeight(): Int

    @Invoker("slotClicked")
    fun invokeSlotClicked(slot: Slot?, slotId: Int, mouseButton: Int, type: ContainerInput)
}
