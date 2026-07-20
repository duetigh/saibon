package dev.saibon.market.value

import dev.saibon.hud.modules.EstimatedValueHudModule
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Appends a single red "Est. value: ..." line to every item's real tooltip
 * (inventory, chests, Auction House — anywhere `ItemStack.getTooltipLines`
 * is rendered), via Fabric API's `ItemTooltipCallback` — already available
 * through this project's `fabric-api` dependency (`fabric-item-api-v1`), so
 * no new mixin is needed here (the project's only other mixin,
 * `AbstractContainerScreenAccessor`, is unrelated). The same callback
 * invocation also feeds [EstimatedValueHudModule]'s "what's currently
 * hovered" state, reusing this one hook as the hover signal instead of
 * writing separate hover-detection plumbing.
 */
object EstimatedValueTooltipAppender {
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            val result = EstimatedItemValueCalculator.compute(stack)
            if (result != null) {
                EstimatedValueHudModule.onHover(result)
                lines.add(tooltipLine(result))
            }
        }
    }

    private fun tooltipLine(result: EstimatedValueResult): Component {
        val suffix = if (result.isPartial) " (partial)" else ""
        return Component.literal("Est. value: ${ValueFormat.compact(result.total)} coins$suffix")
            .withStyle { it.withColor(ChatFormatting.RED) }
    }
}
