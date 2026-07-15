package dev.saibon.mixin

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/**
 * [AbstractContainerScreen.leftPos]/`topPos` are `protected` with no public
 * getter — the one place in the whole inventory-search-overlay design that
 * genuinely needs a mixin rather than a Fabric API event, since it's the
 * only way to turn a [net.minecraft.world.inventory.Slot]'s relative
 * `x`/`y` into an absolute on-screen rect for the highlight overlay.
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
}
