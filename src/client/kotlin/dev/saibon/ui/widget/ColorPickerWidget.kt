package dev.saibon.ui.widget

import dev.saibon.ui.screen.ColorPickerScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/** Swatch button showing a packed-ARGB color; click opens [ColorPickerScreen]. */
class ColorPickerWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    label: Component,
    initial: Int,
    private val parentScreen: net.minecraft.client.gui.screens.Screen,
    private val onChange: (Int) -> Unit
) : AbstractWidget(x, y, width, height, label) {

    private var color = initial

    override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
        Minecraft.getInstance().setScreenAndShow(
            ColorPickerScreen(parentScreen, color) { picked ->
                color = picked
                onChange(picked)
            }
        )
    }

    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractor.outline(x, y, width, height, 0xFF8A8A8A.toInt())
        extractor.fill(x + 1, y + 1, x + width - 1, y + height - 1, color)
        extractor.text(Minecraft.getInstance().font, message, x + width + 4, y + (height - 8) / 2, 0xFFFFFFFF.toInt())
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }
}
