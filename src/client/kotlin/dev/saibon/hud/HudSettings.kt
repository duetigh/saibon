package dev.saibon.hud

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection
import net.minecraft.client.Minecraft

/** Registers the "HUD Locations" settings section — one enable toggle per registered [HudModule] plus the button that opens [HudEditScreen] for drag-to-reposition/scale editing. */
object HudSettings {
    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.HUD, "HUD Locations") {
                label("Drag, resize, and toggle every in-world overlay from one screen.")
                button("Edit HUD positions") {
                    Minecraft.getInstance().setScreenAndShow(HudEditScreen())
                }
                for (module in HudEngine.allModules()) {
                    val state = HudEngine.stateFor(module)
                    toggle(module.title, { state.enabled }) {
                        state.enabled = it
                        Saibon.config.save()
                    }
                }
            }
        )
    }
}
