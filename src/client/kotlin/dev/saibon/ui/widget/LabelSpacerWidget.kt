package dev.saibon.ui.widget

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

/**
 * Zero-size, non-interactive placeholder for [dev.saibon.ui.settings.SettingsSectionBuilder.label]
 * rows: the actual text renders via the row's own `entry.label` (the same
 * left-column text every other setting type already uses), so this widget's
 * only job is to occupy the row's right-column widget slot without drawing
 * or intercepting clicks.
 */
class LabelSpacerWidget(x: Int, y: Int) : AbstractWidget(x, y, 0, 0, Component.empty()) {
    override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}
    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}
