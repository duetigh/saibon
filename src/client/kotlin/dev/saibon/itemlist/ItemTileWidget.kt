package dev.saibon.itemlist

import dev.saibon.data.model.SkyblockItem
import dev.saibon.ui.style.Panel
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/** One grid cell in the Item List: an item icon over a rarity-tinted background, selectable. */
class ItemTileWidget(
    x: Int,
    y: Int,
    size: Int,
    val item: SkyblockItem,
    private var selected: Boolean,
    private val onSelect: (SkyblockItem) -> Unit
) : AbstractWidget(x, y, size, size, Component.literal(item.name)) {

    private val icon: ItemStack = ItemIcons.stackFor(item)

    fun setSelected(value: Boolean) {
        selected = value
    }

    override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
        onSelect(item)
    }

    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val background = when {
            selected -> Panel.SELECTED_BACKGROUND
            isHovered -> Panel.HOVER_BACKGROUND
            else -> Panel.BACKGROUND
        }
        Panel.draw(extractor, x, y, width, height, background)
        if (selected || isHovered) {
            extractor.outline(x, y, width, height, RarityColors.of(item.tier))
        }
        extractor.item(icon, x + (width - 16) / 2, y + (height - 16) / 2)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }
}
