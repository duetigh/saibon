package dev.saibon.ui.style

import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Shared beveled-panel look (dark fill + light top/left edge + dark
 * bottom/right edge, NEU/inventory-GUI style) used across Saibon's custom
 * screens and widgets so they read as one consistent look instead of the
 * flat single-color rectangles Stage 1 shipped with.
 */
object Panel {
    const val BACKGROUND = 0xE8241A14.toInt()
    const val HOVER_BACKGROUND = 0xE8382A1E.toInt()
    const val SELECTED_BACKGROUND = 0xE84A3820.toInt()
    const val BORDER_LIGHT = 0x50FFECC8.toInt()
    const val BORDER_DARK = 0x90000000.toInt()
    const val ACCENT = 0xFFC8942A.toInt()
    const val TOGGLE_ON = 0xE82E7D32.toInt()
    const val TOGGLE_OFF = 0xE87D2E2E.toInt()

    fun draw(extractor: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, background: Int = BACKGROUND) {
        extractor.fill(x, y, x + width, y + height, background)
        extractor.horizontalLine(x, x + width - 1, y, BORDER_LIGHT)
        extractor.verticalLine(x, y, y + height - 1, BORDER_LIGHT)
        extractor.horizontalLine(x, x + width - 1, y + height - 1, BORDER_DARK)
        extractor.verticalLine(x + width - 1, y, y + height - 1, BORDER_DARK)
    }
}
