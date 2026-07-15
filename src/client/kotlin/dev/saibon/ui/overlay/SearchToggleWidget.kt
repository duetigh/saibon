package dev.saibon.ui.overlay

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Small collapsed tab docked under a Skyblock inventory screen; double-click
 * reveals the full search [net.minecraft.client.gui.components.EditBox]
 * ([InventorySearchOverlay] swaps this widget out for one). Double-click
 * detection reuses the `doubled` flag the vanilla widget click pipeline
 * already computes, rather than hand-rolled timing.
 */
class SearchToggleWidget(
    x: Int, y: Int, width: Int, height: Int,
    private val onExpand: () -> Unit
) : AbstractWidget(x, y, width, height, Component.literal("🔍 Search (double-click)")) {

    override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
        if (doubled) onExpand()
    }

    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractor.fill(x, y, x + width, y + height, if (isHovered) 0xAA333333.toInt() else 0x88222222.toInt())
        extractor.outline(x, y, width, height, 0xFF8A8A8A.toInt())
        extractor.text(Minecraft.getInstance().font, message, x + 3, y + (height - 8) / 2, 0xFFFFFFFF.toInt())
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }
}
