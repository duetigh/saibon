package dev.saibon.search

/**
 * Persisted inventory search/highlight preferences.
 * Colors are packed ARGB ints, same form [dev.saibon.ui.widget.ColorPickerWidget]
 * and `GuiGraphicsExtractor.fill`/`outline` already consume.
 */
data class SearchConfig(
    var enabled: Boolean = true,
    var matchColor: Int = 0xFF55FF55.toInt(),
    var dimColor: Int = 0x88000000.toInt()
)
