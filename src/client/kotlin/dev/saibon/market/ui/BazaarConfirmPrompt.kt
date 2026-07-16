package dev.saibon.market.ui

import dev.saibon.data.model.SkyblockItem

/**
 * State for the second explicit confirmation [BazaarMenuOverlay] shows
 * before the first synthesized click of any action sequence (gated by
 * [dev.saibon.market.MarketConfig.bazaarActionConfirmRequired]). The user's
 * click on the overlay's action button is the "direct concurrent user
 * input" the project's information-only rule requires, but because
 * execution then chains across several real screens, this second, explicit
 * confirmation keeps "one click -> one reviewed action" instead of "one
 * click -> an unreviewed chain." Rendered/owned by [BazaarMenuOverlay]
 * itself (not a separate `Screen`) since replacing the screen mid-flow would
 * send a close-container packet and tear down the real menu this whole
 * feature depends on staying open.
 */
data class PendingBazaarConfirm(val action: BazaarAction, val item: SkyblockItem) {
    fun promptText(): String = "${action.label}: ${item.name}?"
}
