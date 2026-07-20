package dev.saibon.market.flip

import dev.saibon.data.DataRepository
import dev.saibon.market.AuctionSalesHistoryRepository
import dev.saibon.market.MarketPriceRepository

/**
 * Resolves one ingredient's per-unit acquisition cost for the craft-cost
 * recursion ([dev.saibon.market.CraftFlipRanking.craftCostOf]) and for
 * [dev.saibon.market.value.EstimatedItemValueCalculator]'s modifier pricing,
 * per the pasted spec's §6.4 resolution order:
 *
 * 1. Bazaar price ([MarketPriceRepository]) if the item is Bazaar-tradeable —
 *    preferring the top-of-book **buy order** price (`topBuyOrderPrice`, from
 *    `buy_summary`) over the `quick_status` instant-buy average, since that's
 *    what a patient buyer/forger actually pays (a real crafter places a buy
 *    order rather than sweeping the sell-offer book), only falling back to
 *    the instant-buy average when the order book is too thin to have a top
 *    buy order at all. Same field/naming convention already used by
 *    [dev.saibon.market.flip.BazaarMarginFlipFinder] and
 *    [dev.saibon.market.ui.BazaarMenuOverlay].
 * 2. Otherwise the AH fair price ([AuctionSalesHistoryRepository]) — this is
 *    what previously silently dropped every AH-only ingredient (any item
 *    with no Bazaar price just failed the whole craft-cost calc for
 *    anything that needed it).
 * 3. Clamped up to the item's NPC sell price as a floor wherever Hypixel
 *    publishes one — no rational seller lists below what an NPC would pay
 *    instantly, so a lower BZ/AH number there is almost always bad data
 *    (a manipulated listing or a stale/thin order book), not a real price.
 */
object IngredientPriceResolver {
    fun costOf(itemId: String): Double? {
        val bazaar = MarketPriceRepository.bazaarPrice(itemId)
            ?.let { it.topBuyOrderPrice.takeIf { p -> p > 0 } ?: it.buyPrice.takeIf { p -> p > 0 } }
        val resolved = bazaar ?: AuctionSalesHistoryRepository.saleReference(itemId)?.fairPrice?.takeIf { it > 0 }
        val npcFloor = DataRepository.item(itemId)?.npcSellPrice?.takeIf { it > 0 }

        return when {
            resolved != null && npcFloor != null -> maxOf(resolved, npcFloor)
            else -> resolved ?: npcFloor
        }
    }
}
