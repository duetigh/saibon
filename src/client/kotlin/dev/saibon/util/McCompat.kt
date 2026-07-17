package dev.saibon.util

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Saibon builds against MC 26.2, where Mojang moved the "current screen"
 * state from `Minecraft.screen` (a public field, present through 26.1.2)
 * to `Gui.screen()` (a method on the HUD renderer, 26.2+). Since this
 * project runs on unobfuscated (Mojang-mapped) MC, a direct reference to
 * either API only resolves on the version it actually exists on and throws
 * `NoSuchMethodError`/`NoSuchFieldError` on the other — this looks both up
 * once via reflection so the same jar works on 26.1.2 and 26.2.
 */
object McCompat {
    private val guiScreenMethod = runCatching {
        Class.forName("net.minecraft.client.gui.Gui").getMethod("screen")
    }.getOrNull()

    private val minecraftScreenField = runCatching {
        Minecraft::class.java.getField("screen")
    }.getOrNull()

    fun currentScreen(): Screen? {
        val mc = Minecraft.getInstance()
        guiScreenMethod?.let { return it.invoke(mc.gui) as Screen? }
        minecraftScreenField?.let { return it.get(mc) as Screen? }
        return null
    }
}
