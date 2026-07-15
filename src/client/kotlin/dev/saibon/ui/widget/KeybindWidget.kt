package dev.saibon.ui.widget

import com.mojang.blaze3d.platform.InputConstants
import dev.saibon.ui.style.Panel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Click-to-rebind key capture widget. Persists/exposes the key as
 * [InputConstants.Key.getName] strings (round-trips through
 * [InputConstants.getKey]) rather than raw keycodes, so a saved binding
 * survives keyboard-layout differences the same way vanilla options.txt does.
 * Does not itself register a [net.minecraft.client.KeyMapping] — the owning
 * feature does that and reads the persisted name at startup.
 */
class KeybindWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    initialKeyName: String,
    private val onKeyChanged: (String) -> Unit
) : AbstractWidget(x, y, width, height, Component.literal(displayName(initialKeyName))) {

    private var listening = false

    override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
        listening = true
        setMessage(Component.literal("> Press a key <"))
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!listening) return false
        val key = InputConstants.getKey(event)
        listening = false
        setMessage(Component.literal(displayName(key.name)))
        onKeyChanged(key.name)
        return true
    }

    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val background = if (listening) Panel.SELECTED_BACKGROUND else if (isHovered) Panel.HOVER_BACKGROUND else Panel.BACKGROUND
        Panel.draw(extractor, x, y, width, height, background)
        extractor.text(Minecraft.getInstance().font, message, x + 4, y + (height - 8) / 2, 0xFFFFFFFF.toInt())
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }

    companion object {
        private fun displayName(keyName: String): String =
            InputConstants.getKey(keyName).displayName.string
    }
}
