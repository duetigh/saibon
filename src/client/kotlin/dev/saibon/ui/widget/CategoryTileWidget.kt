package dev.saibon.ui.widget

import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.style.Panel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/** Sidebar category entry: item-icon + label tile, NEU-style, replacing a plain text [net.minecraft.client.gui.components.Button]. */
class CategoryTileWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    label: Component,
    val category: SaibonCategory,
    private val icon: ItemStack,
    private var selected: Boolean,
    private val onSelect: (SaibonCategory) -> Unit
) : AbstractWidget(x, y, width, height, label) {

    fun setSelected(value: Boolean) {
        selected = value
    }

    override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
        onSelect(category)
    }

    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val background = when {
            selected -> Panel.SELECTED_BACKGROUND
            isHovered -> Panel.HOVER_BACKGROUND
            else -> Panel.BACKGROUND
        }
        Panel.draw(extractor, x, y, width, height, background)
        extractor.item(icon, x + 3, y + (height - 16) / 2)
        extractor.text(
            Minecraft.getInstance().font,
            message,
            x + 3 + 16 + 4,
            y + (height - 8) / 2,
            if (selected) Panel.ACCENT else 0xFFE0E0E0.toInt()
        )
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }
}
