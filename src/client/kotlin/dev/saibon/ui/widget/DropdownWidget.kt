package dev.saibon.ui.widget

import dev.saibon.ui.style.Panel
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.min

/**
 * A real dropdown menu: click the button to open a popup list of every
 * option positioned under it (or above it, if there isn't room below),
 * click a row to select it and close the popup. Popup rows are injected
 * into [screen]'s own widget list ([Screens.getWidgets], the same
 * Fabric-API mechanism every overlay panel in this codebase already uses to
 * add widgets to a screen it doesn't own) rather than opening a new screen —
 * replacing the screen would force-close a real, server-driven Hypixel menu
 * for the overlay use sites (AH/Bazaar panels), which is not safe to do from
 * a settings widget.
 */
object DropdownWidget {
    private const val ROW_HEIGHT = 14
    private const val MAX_VISIBLE_ROWS = 10

    fun <T : Any> create(
        screen: Screen,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: Component,
        options: List<T>,
        initial: T,
        stringify: (T) -> Component,
        onChange: (T) -> Unit
    ): AbstractWidget = DropdownButtonWidget(screen, x, y, width, height, label, options, initial, stringify, onChange)

    private class DropdownButtonWidget<T : Any>(
        private val screen: Screen,
        x: Int, y: Int, width: Int, height: Int,
        private val label: Component,
        private val options: List<T>,
        initial: T,
        private val stringify: (T) -> Component,
        private val onChange: (T) -> Unit
    ) : AbstractWidget(x, y, width, height, label) {

        private var selected: T = initial
        private val popupRows = mutableListOf<AbstractWidget>()
        private val isOpen get() = popupRows.isNotEmpty()

        override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
            if (isOpen) closePopup() else openPopup()
        }

        private fun openPopup() {
            closePopup()
            val widgets = Screens.getWidgets(screen)
            val visibleRows = min(options.size, MAX_VISIBLE_ROWS)
            val popupHeight = visibleRows * ROW_HEIGHT
            val openUpward = y + height + popupHeight > screen.height
            var rowY = if (openUpward) y - popupHeight else y + height

            for (option in options) {
                val row = DropdownRowWidget(x, rowY, width, ROW_HEIGHT, stringify(option), option == selected) {
                    selected = option
                    onChange(option)
                    closePopup()
                }
                popupRows += row
                widgets += row
                rowY += ROW_HEIGHT
            }
        }

        private fun closePopup() {
            if (popupRows.isEmpty()) return
            val widgets = Screens.getWidgets(screen)
            popupRows.forEach { widgets.remove(it) }
            popupRows.clear()
        }

        override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            Panel.draw(extractor, x, y, width, height, if (isHovered || isOpen) Panel.HOVER_BACKGROUND else Panel.BACKGROUND)
            val font = Minecraft.getInstance().font
            extractor.text(font, "${label.string}: ", x + 4, y + (height - 8) / 2, 0xFFE0E0E0.toInt())
            val valueX = x + 4 + font.width("${label.string}: ")
            extractor.text(font, stringify(selected), valueX, y + (height - 8) / 2, 0xFFFFFFFF.toInt())
            extractor.text(font, if (isOpen) "^" else "v", x + width - 10, y + (height - 8) / 2, 0xFFA0A0A0.toInt())
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            output.add(NarratedElementType.TITLE, message)
        }
    }

    private class DropdownRowWidget(
        x: Int, y: Int, width: Int, height: Int,
        label: Component,
        private val selected: Boolean,
        private val onPick: () -> Unit
    ) : AbstractWidget(x, y, width, height, label) {

        override fun onClick(event: MouseButtonEvent, doubled: Boolean) = onPick()

        override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val background = when {
                selected -> Panel.SELECTED_BACKGROUND
                isHovered -> Panel.HOVER_BACKGROUND
                else -> Panel.BACKGROUND
            }
            Panel.draw(extractor, x, y, width, height, background)
            extractor.text(Minecraft.getInstance().font, message, x + 4, y + (height - 8) / 2, 0xFFFFFFFF.toInt())
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            output.add(NarratedElementType.TITLE, message)
        }
    }
}
