package dev.saibon.ui.widget

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

/**
 * An [EditBox] that, while focused, swallows every key press instead of only
 * the navigation/editing keys [EditBox] itself understands. Plain letter and
 * digit keys never reach [EditBox.keyPressed] at all (they arrive through
 * `charTyped` instead), so without this a search box sitting on top of an
 * `AbstractContainerScreen` lets those keys fall through unconsumed to the
 * container's own key handling — typing "e" closes the inventory, "q" drops
 * the hovered item, "1".."9" swaps it into a hotbar slot — all while the box
 * still has focus and is receiving the same keystroke as text.
 */
class SearchEditBox(font: Font, x: Int, y: Int, width: Int, height: Int, message: Component) :
    EditBox(font, x, y, width, height, message) {

    override fun keyPressed(event: KeyEvent): Boolean {
        if (super.keyPressed(event)) return true
        return isActive && isFocused
    }
}
