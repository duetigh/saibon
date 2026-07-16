package dev.saibon.ui.widget

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/** Thin config-binding factory around vanilla [EditBox]. */
object TextFieldWidget {
    fun create(
        font: Font,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        initial: String,
        onChange: (String) -> Unit
    ): EditBox {
        val box = SearchEditBox(font, x, y, width, height, Component.literal("Value"))
        box.setValue(initial)
        box.setResponder(onChange)
        return box
    }
}
