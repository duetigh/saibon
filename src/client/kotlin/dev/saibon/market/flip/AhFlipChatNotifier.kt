package dev.saibon.market.flip

import dev.saibon.client.chat.SaibonChat
import dev.saibon.core.Saibon
import dev.saibon.market.PlayerNameResolver
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional chat line for newly-found Auction House flips: item, asking
 * price, margin, and resell price, followed by a "[Saibon] Buy now." line
 * that runs `/ah <seller>` on click — same direct-click command pattern as
 * [dev.saibon.update.UpdatePrompt] and `FlipScreen`'s seller button, never
 * auto-run. Off by default; only fires for candidates backed by one real
 * listing+seller ([FlipEngine.onNewCandidate] already only calls listeners
 * for genuinely new candidates, so no extra dedup is needed here).
 */
object AhFlipChatNotifier {
    /** Matches `FlipScreen`'s `PRICE_COLOR` so a cost/margin/value figure reads the same whether it's seen in chat or in the flip GUI. */
    private val NUMBER_COLOR = ChatFormatting.YELLOW
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        FlipEngine.onNewCandidate { candidate -> maybeNotify(candidate) }
    }

    private fun maybeNotify(candidate: FlipCandidate) {
        val config = Saibon.config.data.flip
        if (!config.chatNotifyEnabled) return
        if (candidate.estimatedProfit < config.alertMinProfit) return
        if (candidate.marginPercent < config.alertMinMarginPercent) return
        val sellerUuid = candidate.sellerUuid ?: return

        PlayerNameResolver.resolve(sellerUuid).thenAccept { name ->
            if (name == null) return@thenAccept
            Minecraft.getInstance().execute { send(candidate, name) }
        }
    }

    private fun send(candidate: FlipCandidate, sellerName: String) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(SaibonChat.message(flipLine(candidate)))
        player.sendSystemMessage(SaibonChat.prefix().append(buyNowLink(sellerName)))
    }

    /** Item name and connecting words stay default white; the figures a player actually scans for (cost, margin, resell price) are pulled out in [NUMBER_COLOR] so they stand out from the sentence around them. */
    private fun flipLine(candidate: FlipCandidate): Component =
        Component.literal("${candidate.item.name}: selling for ")
            .append(number("${format(candidate.cost)} coins"))
            .append(Component.literal(" ("))
            .append(number("%.1f%%".format(candidate.marginPercent)))
            .append(Component.literal(" margin) — sell for "))
            .append(number("${format(candidate.estimatedValue)} coins"))

    private fun number(text: String): Component =
        Component.literal(text).withStyle { it.withColor(NUMBER_COLOR) }

    private fun buyNowLink(sellerName: String): Component =
        Component.literal("Buy now.").withStyle {
            it.withClickEvent(ClickEvent.RunCommand("ah $sellerName"))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("Open $sellerName's Auction House page")))
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
        }

    private fun format(value: Double): String = "%,.0f".format(value)
}
