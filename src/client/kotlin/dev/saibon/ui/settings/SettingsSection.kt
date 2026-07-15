package dev.saibon.ui.settings

import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.widget.ColorPickerWidget
import dev.saibon.ui.widget.DropdownWidget
import dev.saibon.ui.widget.KeybindWidget
import dev.saibon.ui.widget.SliderWidget
import dev.saibon.ui.widget.TextFieldWidget
import dev.saibon.ui.widget.ToggleWidget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** One settings row: a label plus a factory that builds the bound, positioned widget. */
class SettingEntry(
    val label: String,
    val build: (screen: Screen, x: Int, y: Int, width: Int, height: Int) -> AbstractWidget
)

/** A titled group of [SettingEntry] rows shown under one [SaibonCategory]. */
class SettingsSection(
    val category: SaibonCategory,
    val title: String,
    val order: Int,
    val entries: List<SettingEntry>
)

/** Builder DSL passed to the [SettingsSection] factory function below. */
class SettingsSectionBuilder(private val category: SaibonCategory, private val title: String, private val order: Int) {
    private val entries = mutableListOf<SettingEntry>()

    fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        entries += SettingEntry(label) { _, x, y, w, h ->
            ToggleWidget.create(x, y, w, h, Component.literal(label), initial, onChange)
        }
    }

    fun <T : Any> dropdown(label: String, options: List<T>, initial: T, stringify: (T) -> String, onChange: (T) -> Unit) {
        entries += SettingEntry(label) { _, x, y, w, h ->
            DropdownWidget.create(x, y, w, h, Component.literal(label), options, initial, { Component.literal(stringify(it)) }, onChange)
        }
    }

    fun slider(label: String, min: Float, max: Float, initial: Float, format: (Float) -> String, onChange: (Float) -> Unit) {
        entries += SettingEntry(label) { _, x, y, w, h ->
            SliderWidget(x, y, w, h, min, max, initial, format, onChange)
        }
    }

    fun textField(label: String, initial: String, onChange: (String) -> Unit) {
        entries += SettingEntry(label) { _, x, y, w, h ->
            TextFieldWidget.create(Minecraft.getInstance().font, x, y, w, h, initial, onChange)
        }
    }

    fun keybind(label: String, initialKeyName: String, onChange: (String) -> Unit) {
        entries += SettingEntry(label) { _, x, y, w, h ->
            KeybindWidget(x, y, w, h, initialKeyName, onChange)
        }
    }

    fun colorPicker(label: String, initial: Int, onChange: (Int) -> Unit) {
        entries += SettingEntry(label) { screen, x, y, w, h ->
            ColorPickerWidget(x, y, w, h, Component.literal(label), initial, screen, onChange)
        }
    }

    fun build(): SettingsSection = SettingsSection(category, title, order, entries)
}

/** Factory used by each feature module to self-register a settings section, e.g.:
 * ```
 * SettingsRegistry.register(SettingsSection(SaibonCategory.UPDATES, "Updates") {
 *     toggle("Auto-check for updates", config.autoCheck) { config.autoCheck = it }
 * })
 * ```
 */
fun SettingsSection(
    category: SaibonCategory,
    title: String,
    order: Int = 0,
    build: SettingsSectionBuilder.() -> Unit
): SettingsSection {
    val builder = SettingsSectionBuilder(category, title, order)
    builder.build()
    return builder.build()
}
