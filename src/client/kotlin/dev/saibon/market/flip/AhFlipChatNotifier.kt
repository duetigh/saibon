package dev.saibon.market.flip

import dev.saibon.client.chat.SaibonChat
import dev.saibon.core.Saibon
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional chat line for newly-found Auction House flips: item, asking
 * price, margin, and resell price, followed by a "[Saibon] Buy now." line
 * that runs `/viewauction <uuid>` on click — jumps straight to the specific
 * listing rather than the seller's whole AH page, and (unlike the old
 * `/ah <seller>` link) needs no async seller-name resolution to build, so a
 * slow/failed Mojang lookup can no longer silently swallow the message. Same
 * direct-click command pattern as [dev.saibon.update.UpdatePrompt] and
 * `FlipScreen`'s auction button, never auto-run. Off by default; only fires
 * for candidates backed by one real listing ([FlipEngine.onNewCandidate]
 * already only calls listeners for genuinely new candidates, so no extra
 * dedup is needed here).
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
        if (candidate.estimatedProfit <= 0) return
        if (candidate.estimatedProfit < config.alertMinProfit) return
        if (candidate.marginPercent < config.alertMinMarginPercent) return
        val auctionUuid = candidate.auctionUuid ?: return

        Minecraft.getInstance().execute { send(candidate, auctionUuid) }
    }

    private fun send(candidate: FlipCandidate, auctionUuid: String) {
        val player = Minecraft.getInstance().player ?: return
        player.sendSystemMessage(SaibonChat.message(flipLine(candidate)))
        player.sendSystemMessage(SaibonChat.prefix().append(buyNowLink(auctionUuid)))
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

    private fun buyNowLink(auctionUuid: String): Component =
        Component.literal("Buy now.").withStyle {
            it.withClickEvent(ClickEvent.RunCommand("viewauction $auctionUuid"))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("View this auction")))
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
        }

    private fun format(value: Double): String = "%,.0f".format(value)
}
