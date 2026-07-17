package dev.saibon.ui.style

import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Shared beveled-panel look (dark fill + light top/left edge + dark
 * bottom/right edge, NEU/inventory-GUI style) used across Saibon's custom
 * screens and widgets so they read as one consistent look instead of the
 * flat single-color rectangles Stage 1 shipped with.
 */
object Panel {
    // Fully opaque (alpha FF): nothing rendered behind a Saibon panel/button — another
    // widget, a game slot, another mod's HUD — should ever be visible through it.
    const val BACKGROUND = 0xFF1C1C1C.toInt()
    const val HOVER_BACKGROUND = 0xFF2E2E2E.toInt()
    const val SELECTED_BACKGROUND = 0xFF404040.toInt()
    const val BORDER_LIGHT = 0x50C0C0C0.toInt()
    const val BORDER_DARK = 0x90000000.toInt()
    const val ACCENT = 0xFFF2F2F2.toInt()
    const val TOGGLE_ON = 0xFF2E7D32.toInt()
    const val TOGGLE_OFF = 0xFF7D2E2E.toInt()

    fun draw(extractor: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, background: Int = BACKGROUND) {
        extractor.fill(x, y, x + width, y + height, background)
        extractor.horizontalLine(x, x + width - 1, y, BORDER_LIGHT)
        extractor.verticalLine(x, y, y + height - 1, BORDER_LIGHT)
        extractor.horizontalLine(x, x + width - 1, y + height - 1, BORDER_DARK)
        extractor.verticalLine(x + width - 1, y, y + height - 1, BORDER_DARK)
    }
}
