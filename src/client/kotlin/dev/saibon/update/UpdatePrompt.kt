package dev.saibon.update

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * Builds and sends the local, client-authored chat prompt for a newer
 * release. Both links run Saibon's own registered `/saibon updatecheck ...`
 * subcommands via [ClickEvent.RunCommand] rather than opening anything or
 * installing anything directly from chat, per the "explicit chat prompt +
 * click" consent requirement.
 */
object UpdatePrompt {
    fun send(manifest: VersionManifest) {
        val player = Minecraft.getInstance().player ?: return

        val message = Component.literal("Saibon v${manifest.latestVersion} is available → ")
            .append(link("[View Changelog]", "/saibon updatecheck changelog", "Open the changelog"))
            .append(Component.literal(" "))
            .append(link("[Update Now]", "/saibon updatecheck install", "Download and install the update"))

        player.sendSystemMessage(message)
    }

    private fun link(text: String, command: String, hover: String): Component =
        Component.literal(text).withStyle {
            it.withClickEvent(ClickEvent.RunCommand(command))
                .withHoverEvent(HoverEvent.ShowText(Component.literal(hover)))
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
        }
}
