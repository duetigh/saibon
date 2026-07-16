package dev.saibon.market.ui

import dev.saibon.client.chat.SaibonChat
import dev.saibon.core.Saibon
import dev.saibon.data.model.SkyblockItem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

enum class BazaarAction(val label: String) {
    BUY_INSTANTLY("Buy Instantly"),
    SELL_INSTANTLY("Sell Instantly"),
    CREATE_BUY_ORDER("Create Buy Order"),
    CREATE_SELL_OFFER("Create Sell Offer")
}

/**
 * Entry point [BazaarMenuOverlay]'s action buttons call after the user
 * confirms (see [BazaarMenuOverlay]'s confirm-prompt state). Builds the
 * click sequence via [BazaarActionSteps] and hands it to
 * [BazaarActionNavigator], routing through dry-run or live execution per
 * [dev.saibon.market.MarketConfig.bazaarActionDryRun]. All navigator
 * progress/failures are echoed to chat so the player can see exactly what
 * Saibon is doing/attempting, especially important while dry-run is the
 * only mode that's actually been exercised.
 */
object BazaarActionController {
    fun execute(action: BazaarAction, screen: AbstractContainerScreen<*>, item: SkyblockItem) {
        val alreadyOnProductScreen = screen.title.string.contains(item.name, ignoreCase = true)
        val steps = BazaarActionSteps.forAction(action, item, alreadyOnProductScreen)
        val player = Minecraft.getInstance().player

        val onLog: (String) -> Unit = { message -> player?.sendSystemMessage(SaibonChat.message(message)) }
        val onComplete: () -> Unit = { player?.sendSystemMessage(SaibonChat.message("Done: ${action.label} for ${item.name}.")) }
        val onFail: (String) -> Unit = { message -> player?.sendSystemMessage(SaibonChat.message("Bazaar action stopped: $message")) }

        if (Saibon.config.data.market.bazaarActionDryRun) {
            BazaarActionNavigator.runDryRun(screen, steps, onLog, onComplete, onFail)
        } else {
            BazaarActionNavigator.run(screen, steps, onLog, onComplete, onFail)
        }
    }
}
