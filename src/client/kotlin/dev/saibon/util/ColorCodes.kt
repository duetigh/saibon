package dev.saibon.util

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Some data-repo item names carry Hypixel's `%%color%%` placeholder tokens
 * instead of real formatting (e.g. `"%%green%%Ballista Fuel Cell"`, seen in
 * `data/items.json`) — drawn literally, that token text shows up in the UI
 * instead of colored text. This renders those names correctly by switching
 * color at each token rather than printing it.
 */
object ColorCodes {
    private val TOKEN = Regex("%%([a-z_]+)%%")
    private val COLORS = mapOf(
        "black" to 0x000000, "dark_blue" to 0x0000AA, "dark_green" to 0x00AA00,
        "dark_aqua" to 0x00AAAA, "dark_red" to 0xAA0000, "dark_purple" to 0xAA00AA,
        "gold" to 0xFFAA00, "gray" to 0xAAAAAA, "dark_gray" to 0x555555,
        "blue" to 0x5555FF, "green" to 0x55FF55, "aqua" to 0x55FFFF,
        "red" to 0xFF5555, "light_purple" to 0xFF55FF, "yellow" to 0xFFFF55,
        "white" to 0xFFFFFF
    )

    fun hasTokens(text: String): Boolean = TOKEN.containsMatchIn(text)

    /** Strips `%%token%%` placeholders, leaving plain text — for narration/plain-string contexts. */
    fun strip(text: String): String = TOKEN.replace(text, "")

    /** Rendered width of [text] with `%%token%%` placeholders removed (for tooltip/box sizing). */
    fun width(font: Font, text: String): Int = font.width(strip(text))

    /** Draws [text], switching color at each `%%color%%` token instead of printing it literally. */
    fun drawText(extractor: GuiGraphicsExtractor, font: Font, text: String, x: Int, y: Int, defaultColor: Int, shadow: Boolean) {
        if (!hasTokens(text)) {
            extractor.text(font, text, x, y, defaultColor, shadow)
            return
        }

        var drawX = x
        var color = defaultColor
        var lastEnd = 0
        for (match in TOKEN.findAll(text)) {
            val segment = text.substring(lastEnd, match.range.first)
            if (segment.isNotEmpty()) {
                extractor.text(font, segment, drawX, y, color, shadow)
                drawX += font.width(segment)
            }
            color = COLORS[match.groupValues[1]]?.let { it or 0xFF000000.toInt() } ?: color
            lastEnd = match.range.last + 1
        }
        val tail = text.substring(lastEnd)
        if (tail.isNotEmpty()) {
            extractor.text(font, tail, drawX, y, color, shadow)
        }
    }
}
