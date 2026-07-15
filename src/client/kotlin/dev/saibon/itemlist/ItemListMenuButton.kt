package dev.saibon.itemlist

import dev.saibon.core.Saibon
import dev.saibon.mixin.AbstractContainerScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

/**
 * Adds an "Items" button above the Hypixel SkyBlock menu (the chest GUI
 * opened from the hotbar compass/nether star) that opens [ItemListScreen] —
 * the second of the two entry points `docs/planning/NEU_FEATURE_PARITY.md`
 * #1 calls for, alongside the `/saibonitems` command.
 */
object ItemListMenuButton {
    private const val SKYBLOCK_MENU_TITLE = "SkyBlock Menu"
    private const val BUTTON_WIDTH = 44
    private const val BUTTON_HEIGHT = 18

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            if (!Saibon.config.data.itemList.showMenuButton) return@register
            if (screen.title.string != SKYBLOCK_MENU_TITLE) return@register
            if (!isOnHypixel()) return@register
            attach(screen)
        }
    }

    private fun isOnHypixel(): Boolean =
        Minecraft.getInstance().currentServer?.ip?.contains("hypixel.net", ignoreCase = true) == true

    private fun attach(screen: AbstractContainerScreen<*>) {
        val accessor = screen as AbstractContainerScreenAccessor
        val x = accessor.getLeftPos() + accessor.getImageWidth() - BUTTON_WIDTH
        val y = accessor.getTopPos() - BUTTON_HEIGHT - 2

        val button = Button.builder(Component.literal("Items")) {
            Minecraft.getInstance().setScreenAndShow(ItemListScreen())
        }.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()

        Screens.getWidgets(screen).add(button)
    }
}
