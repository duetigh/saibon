package dev.saibon.update

import dev.saibon.client.chat.SaibonChat
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * Builds and sends the local, client-authored chat prompt for a newer
 * release. The download line runs Saibon's own registered
 * `/saibon updatecheck install` subcommand via [ClickEvent.RunCommand] rather
 * than opening anything or installing anything directly from chat, per the
 * "explicit chat prompt + click" consent requirement.
 */
object UpdatePrompt {
    fun send(manifest: VersionManifest) {
        val player = Minecraft.getInstance().player ?: return

        player.sendSystemMessage(
            SaibonChat.message("New version found! (${UpdateChecker.currentVersion()} → ${manifest.latestVersion})")
        )
        player.sendSystemMessage(
            SaibonChat.prefix()
                .append(link("Click to download.", "/saibon updatecheck install", "Download and install the update"))
        )
    }

    private fun link(text: String, command: String, hover: String): Component =
        Component.literal(text).withStyle {
            it.withClickEvent(ClickEvent.RunCommand(command))
                .withHoverEvent(HoverEvent.ShowText(Component.literal(hover)))
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
        }
}
