package dev.saibon.ui.screen

import dev.saibon.ui.widget.SliderWidget
import dev.saibon.ui.widget.TextFieldWidget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Small RGBA color-picker popup: four channel sliders + a hex field + a live swatch. */
class ColorPickerScreen(
    private val parent: Screen,
    initial: Int,
    private val onPicked: (Int) -> Unit
) : Screen(Component.literal("Color")) {

    companion object {
        private const val MARGIN = 12
        private const val ROW_HEIGHT = 20
        private const val ROW_GAP = 4
        private const val FIELD_WIDTH = 200
        private const val SWATCH_SIZE = 32
        private val CHANNELS = listOf(
            Triple("Alpha", 24, "A"),
            Triple("Red", 16, "R"),
            Triple("Green", 8, "G"),
            Triple("Blue", 0, "B")
        )
    }

    private var argb = initial

    override fun init() {
        rebuild()
    }

    private fun rebuild() {
        clearWidgets()
        var y = MARGIN + SWATCH_SIZE + MARGIN

        CHANNELS.forEach { (name, shift, short) ->
            val current = (argb ushr shift) and 0xFF
            addRenderableWidget(
                SliderWidget(
                    MARGIN, y, FIELD_WIDTH, ROW_HEIGHT,
                    0f, 255f, current.toFloat(),
                    { v -> "$short: ${v.toInt()}" }
                ) { v -> setChannel(shift, v.toInt()) }
            )
            y += ROW_HEIGHT + ROW_GAP
        }

        addRenderableWidget(
            TextFieldWidget.create(font, MARGIN, y, FIELD_WIDTH, ROW_HEIGHT, "%08X".format(argb)) { text ->
                text.trim().removePrefix("#").toLongOrNull(16)?.let { parsed ->
                    argb = parsed.toInt()
                    rebuild()
                }
            }
        )
        y += ROW_HEIGHT + MARGIN

        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                onPicked(argb)
                Minecraft.getInstance().setScreenAndShow(parent)
            }.bounds(MARGIN, y, 80, ROW_HEIGHT).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Cancel")) {
                Minecraft.getInstance().setScreenAndShow(parent)
            }.bounds(MARGIN + 88, y, 80, ROW_HEIGHT).build()
        )
    }

    private fun setChannel(shift: Int, value: Int) {
        val clamped = value.coerceIn(0, 255)
        argb = (argb and (0xFF shl shift).inv()) or (clamped shl shift)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(extractor, mouseX, mouseY, delta)
        extractor.outline(MARGIN, MARGIN, SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFFFF.toInt())
        extractor.fill(MARGIN + 1, MARGIN + 1, MARGIN + SWATCH_SIZE - 1, MARGIN + SWATCH_SIZE - 1, argb)
    }

    override fun isPauseScreen(): Boolean = false
}
