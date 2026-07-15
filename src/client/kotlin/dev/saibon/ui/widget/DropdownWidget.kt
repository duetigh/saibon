package dev.saibon.ui.widget

import net.minecraft.client.gui.components.CycleButton
import net.minecraft.network.chat.Component

/** Thin factory around vanilla's [CycleButton] for cycling through a fixed value list. */
object DropdownWidget {
    fun <T : Any> create(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: Component,
        options: List<T>,
        initial: T,
        stringify: (T) -> Component,
        onChange: (T) -> Unit
    ): CycleButton<T> =
        CycleButton.builder(stringify, initial)
            .withValues(options)
            .create(x, y, width, height, label) { _, value -> onChange(value) }
}
