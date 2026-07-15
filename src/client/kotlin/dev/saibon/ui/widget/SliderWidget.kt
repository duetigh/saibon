package dev.saibon.ui.widget

import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.network.chat.Component

/**
 * Float-range slider bound to a config value. [AbstractSliderButton.value] is
 * normalized to 0..1 by the vanilla base class; this maps it to [min]..[max]
 * on the way in/out.
 */
class SliderWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val min: Float,
    private val max: Float,
    initial: Float,
    private val format: (Float) -> String,
    private val onChange: (Float) -> Unit
) : AbstractSliderButton(x, y, width, height, Component.literal(format(initial)), normalize(initial, min, max)) {

    init {
        updateMessage()
    }

    private fun currentValue(): Float = min + (max - min) * value.toFloat()

    override fun updateMessage() {
        message = Component.literal(format(currentValue()))
    }

    override fun applyValue() {
        onChange(currentValue())
    }

    companion object {
        private fun normalize(v: Float, min: Float, max: Float): Double =
            (((v - min) / (max - min)).coerceIn(0f, 1f)).toDouble()
    }
}
