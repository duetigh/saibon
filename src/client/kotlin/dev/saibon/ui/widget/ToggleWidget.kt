package dev.saibon.ui.widget

import net.minecraft.client.gui.components.CycleButton
import net.minecraft.network.chat.Component

/** Thin factory around vanilla's on/off [CycleButton] for boolean settings. */
object ToggleWidget {
    fun create(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: Component,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ): CycleButton<Boolean> =
        CycleButton.onOffBuilder(initial)
            .create(x, y, width, height, label) { _, value -> onChange(value) }
}
