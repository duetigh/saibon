package dev.saibon.itemlist

import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import java.util.IdentityHashMap

/**
 * Attaches [ItemListSidebarPanel] to the player's own inventory screen — the
 * literal "when I open my inventory" entry point, distinct from
 * [ItemListMenuButton]'s SkyBlock-menu button and the `/saibonitems` command,
 * which both open the full [ItemListScreen] directly.
 */
object ItemListSidebarOverlay {
    private val panels = IdentityHashMap<Screen, ItemListSidebarPanel>()

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is InventoryScreen) return@register
            if (!Saibon.config.data.itemList.sidebarEnabled) return@register
            if (!isOnHypixel()) return@register
            attach(screen)
        }
    }

    private fun isOnHypixel(): Boolean =
        Minecraft.getInstance().currentServer?.ip?.contains("hypixel.net", ignoreCase = true) == true

    private fun attach(screen: InventoryScreen) {
        val panel = ItemListSidebarPanel(screen)
        panel.attach()
        panels[screen] = panel

        ScreenEvents.remove(screen).register {
            panel.detach()
            panels.remove(screen)
        }
        ScreenEvents.afterExtract(screen).register { _, extractor, mouseX, mouseY, _ -> panel.render(extractor, mouseX, mouseY) }
    }
}
