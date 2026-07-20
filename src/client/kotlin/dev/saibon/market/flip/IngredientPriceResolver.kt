package dev.saibon.market.flip

import dev.saibon.data.DataRepository
import dev.saibon.data.model.RecipeType
import dev.saibon.market.AuctionBuyOrderRepository
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
 * 2. Otherwise the AH's own buy-order book ([AuctionBuyOrderRepository]) —
 *    the AH-side equivalent of step 1's Bazaar buy order, when a cached one
 *    exists. Hypixel exposes no public API for this (unlike Bazaar), so
 *    coverage is only ever as good as what the player has actually browsed
 *    in-game; a cache miss falls through to step 3 exactly as before.
 * 3. Otherwise the AH fair price ([AuctionSalesHistoryRepository]) — a
 *    statistical estimate from recently *completed* (insta-buy) sales. This
 *    is what previously silently dropped every AH-only ingredient (any item
 *    with no Bazaar price just failed the whole craft-cost calc for
 *    anything that needed it), and remains the fallback for items with
 *    neither a Bazaar price nor a browsed buy-order cache.
 * 4. Clamped up to the item's NPC sell price as a floor wherever Hypixel
 *    publishes one — no rational seller lists below what an NPC would pay
 *    instantly, so a lower BZ/AH number there is almost always bad data
 *    (a manipulated listing or a stale/thin order book), not a real price.
 */
object IngredientPriceResolver {
    fun costOf(itemId: String): Double? {
        val bazaar = MarketPriceRepository.bazaarPrice(itemId)
            ?.let { it.topBuyOrderPrice.takeIf { p -> p > 0 } ?: it.buyPrice.takeIf { p -> p > 0 } }
        val ahBuyOrder = AuctionBuyOrderRepository.cachedTopBuyOrder(itemId)
        val resolved = bazaar ?: ahBuyOrder ?: AuctionSalesHistoryRepository.saleReference(itemId)?.fairPrice?.takeIf { it > 0 }
        val npcFloor = DataRepository.item(itemId)?.npcSellPrice?.takeIf { it > 0 }

        return when {
            resolved != null && npcFloor != null -> maxOf(resolved, npcFloor)
            else -> resolved ?: npcFloor
        }
    }

    /** NPC-shop restock cap Hypixel enforces on a single purchase — Coflnet `RealisticCraft`'s `DefaultNpcStock` (doc §1.1). */
    private const val NPC_STOCK_CAPACITY = 640.0

    /** A supply source available at [unitPrice] for up to [capacity] units before the price would realistically move. */
    private data class Tranche(val unitPrice: Double, val capacity: Double)

    /**
     * Total cost to acquire [quantity] units of [itemId] — Coflnet
     * `RealisticCraft`'s "smart buyer" (doc §1.1): builds every realistic
     * supply source as a capacity-limited tranche (NPC shop capped at its
     * real ~640-unit restock, then the live Bazaar sell-offer order book
     * walked cheapest-first) and consumes them in price order, instead of
     * [costOf]'s single flat unit price assumed to hold at any quantity.
     * Used by [dev.saibon.market.CraftFlipRanking] so a recipe needing
     * thousands of a thin ingredient (e.g. Obsidian deep in an armor/drill
     * chain) correctly stops looking artificially cheap once cheap supply
     * runs out, rather than multiplying one optimistic unit price by an
     * arbitrary quantity.
     */
    fun costOfQuantity(itemId: String, quantity: Double): Double? {
        if (quantity <= 0.0) return 0.0
        val tranches = buildTranches(itemId)
        if (tranches.isEmpty()) return null

        var remaining = quantity
        var cost = 0.0
        for (tranche in tranches.sortedBy { it.unitPrice }) {
            if (remaining <= 0.0) break
            val take = minOf(remaining, tranche.capacity)
            cost += take * tranche.unitPrice
            remaining -= take
        }
        if (remaining > 0.0) {
            // Demand exceeds every known supply source. Rather than silently pricing the
            // shortfall at the cheapest tranche (making a huge ask look artificially cheap),
            // price it at double the worst known tranche — a smaller stand-in for Coflnet's
            // own 5x "unmet demand" penalty (doc §1.1), scaled down since our tranche model
            // here is itself already an approximation, not a full live order-book/volume feed.
            val worstPrice = tranches.maxOf { it.unitPrice }
            cost += remaining * worstPrice * UNMET_DEMAND_PENALTY
        }
        return cost
    }

    private const val UNMET_DEMAND_PENALTY = 2.0

    private fun buildTranches(itemId: String): List<Tranche> {
        val tranches = mutableListOf<Tranche>()

        DataRepository.recipesFor(itemId).firstOrNull { it.type == RecipeType.NPC }?.npcCost?.let { npcCost ->
            if (npcCost > 0) tranches += Tranche(npcCost, NPC_STOCK_CAPACITY)
        }

        val bazaar = MarketPriceRepository.bazaarPrice(itemId)
        val sellOffers = bazaar?.sellOffers.orEmpty().filter { it.pricePerUnit > 0 && it.amount > 0 }
        if (sellOffers.isNotEmpty()) {
            // Cap the sell-offer book's *combined* capacity at ~1 hour of this item's real
            // daily sell-side volume (Coflnet RealisticCraft doc §1.1) — otherwise a thin
            // ingredient's full order-book depth looks buyable all at once, when in reality
            // only a fraction of it would realistically be available to one buyer per hour.
            // 0/missing sellMovingWeek (data not yet populated) falls back to uncapped, i.e.
            // today's behavior.
            val hourlyCap = bazaar?.sellMovingWeek?.takeIf { it > 0 }?.let { it / 7.0 / 24.0 }
            var remainingCap = hourlyCap
            for (offer in sellOffers) {
                val capacity = if (remainingCap != null) minOf(offer.amount.toDouble(), remainingCap) else offer.amount.toDouble()
                if (capacity <= 0.0) break
                tranches += Tranche(offer.pricePerUnit, capacity)
                if (remainingCap != null) remainingCap -= capacity
            }
        } else if (bazaar != null) {
            // Order book wasn't populated (older cached snapshot, or a product with no standing
            // offers right now) — fall back to a single uncapped tranche at whatever unit price
            // costOf would have used, same coverage as before tranches existed.
            val fallbackUnit = bazaar.topBuyOrderPrice.takeIf { it > 0 } ?: bazaar.buyPrice.takeIf { it > 0 }
            if (fallbackUnit != null) tranches += Tranche(fallbackUnit, Double.MAX_VALUE)
        }

        if (tranches.isEmpty()) {
            // Not on the Bazaar at all — AH-only ingredient, uncapped (no order-book depth to walk).
            val ahUnit = AuctionBuyOrderRepository.cachedTopBuyOrder(itemId)
                ?: AuctionSalesHistoryRepository.saleReference(itemId)?.fairPrice?.takeIf { it > 0 }
            if (ahUnit != null) tranches += Tranche(ahUnit, Double.MAX_VALUE)
        }

        return tranches
    }
}
