package dev.saibon.client.chat

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor

/**
 * Every client-authored chat line starts with this "[Saibon] " prefix, with
 * "Saibon" itself rendered as a per-letter red gradient instead of flat text.
 */
object SaibonChat {
    private const val GRADIENT_START = 0xFF5555
    private const val GRADIENT_END = 0x8B0000
    private const val NAME = "Saibon"

    fun prefix(): MutableComponent {
        val built = Component.literal("[")
        NAME.forEachIndexed { index, char ->
            built.append(
                Component.literal(char.toString())
                    .withStyle { it.withColor(colorAt(index, NAME.length)) }
            )
        }
        return built.append(Component.literal("] "))
    }

    fun message(text: String): MutableComponent = prefix().append(Component.literal(text))

    fun message(text: Component): MutableComponent = prefix().append(text)

    private fun colorAt(index: Int, total: Int): TextColor {
        val t = if (total <= 1) 0f else index / (total - 1).toFloat()
        val startR = (GRADIENT_START shr 16) and 0xFF
        val startG = (GRADIENT_START shr 8) and 0xFF
        val startB = GRADIENT_START and 0xFF
        val endR = (GRADIENT_END shr 16) and 0xFF
        val endG = (GRADIENT_END shr 8) and 0xFF
        val endB = GRADIENT_END and 0xFF
        val r = (startR + (endR - startR) * t).toInt()
        val g = (startG + (endG - startG) * t).toInt()
        val b = (startB + (endB - startB) * t).toInt()
        return TextColor.fromRgb((r shl 16) or (g shl 8) or b)
    }
}
