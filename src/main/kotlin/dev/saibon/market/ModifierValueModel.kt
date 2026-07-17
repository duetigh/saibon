package dev.saibon.market

import dev.saibon.market.model.ItemModifier
import kotlin.math.roundToInt

/**
 * A modifier-decomposition price estimate: [fairPrice] is [plainFairPrice]
 * plus the sum of every modifier's priced value-add, see
 * [ModifierValueModel.estimate]. [pricedCount]/[missingModifiers] tell the
 * caller (and, via `reason`, the player) how complete the estimate actually
 * is — a listing with gemstones nobody has priced yet still gets *a* number,
 * just a lower [confidence] and a shorter [pricedCount].
 */
data class ModifierEstimate(
    val fairPrice: Double,
    val confidence: Int,
    val pricedCount: Int,
    val missingModifiers: List<ItemModifier>
)

/**
 * Coflnet-style additive fallback for when an item's exact full modifier
 * combo has never sold (or hasn't sold enough times to trust, see
 * `FlipConfig.modifierMatchMinSamples`): instead of falling all the way back
 * to a modifier-blind plain price, estimate `plainFairPrice + Σ(each
 * modifier's own learned value-add)`. Pure/parameter-injected, same shape as
 * [FairPriceCalculator]/[AuctionFlipRanking] — no client dependency, callers
 * supply the lookups.
 */
object ModifierValueModel {
    /**
     * [FairPriceCalculator] results fed into this model for individual
     * modifiers are computed over *delta* samples (`salePrice - plainPrice`,
     * see `AuctionSalesHistoryRepository.modifierDeltaHistory`), which can
     * have a zero or negative mean — a case [FairPriceCalculator]'s
     * coefficient-of-variation term isn't designed for (it forces `cv = 0`
     * when `meanValue <= 0`, silently maxing out that bucket's own tightness
     * term). Capping a delta term's contribution to this model's blended
     * confidence avoids treating that inflated number as ground truth,
     * without touching [FairPriceCalculator] itself (which is correct for
     * its original always-positive-price use case).
     */
    private const val DELTA_TERM_CONFIDENCE_CAP = 70

    /**
     * @param plain the item's own modifier-blind fair price (`AuctionSalesHistoryRepository.saleReference(itemId)`).
     * @param modifiers the listing's atomic upgrades (`AuctionPrice.modifiers`/`DecodedAuctionItem.modifiers`).
     * @param perItemDelta this exact item's own learned value-add for one modifier, already
     *   converted to a delta (`saleReference(itemId, modifier.poolKey).fairPrice - plain.fairPrice`)
     *   by the caller — preferred over [pooledDelta] when available since it's item-specific.
     * @param pooledDelta the cross-item pooled value-add for one modifier (`AuctionSalesHistoryRepository.modifierDeltaReference`),
     *   already delta-shaped by construction — the cold-start fallback when [perItemDelta] has nothing.
     */
    fun estimate(
        plain: FairPriceResult,
        modifiers: List<ItemModifier>,
        perItemDelta: (ItemModifier) -> FairPriceResult?,
        pooledDelta: (ItemModifier) -> FairPriceResult?
    ): ModifierEstimate {
        if (modifiers.isEmpty()) return ModifierEstimate(plain.fairPrice, plain.confidence, 0, emptyList())

        var totalDelta = 0.0
        val termConfidences = mutableListOf<Int>()
        val missing = mutableListOf<ItemModifier>()

        for (modifier in modifiers) {
            val term = perItemDelta(modifier) ?: pooledDelta(modifier)
            if (term == null) {
                missing += modifier
                continue
            }
            totalDelta += term.fairPrice
            termConfidences += term.confidence.coerceAtMost(DELTA_TERM_CONFIDENCE_CAP)
        }

        val pricedCount = modifiers.size - missing.size
        if (pricedCount == 0) return ModifierEstimate(plain.fairPrice, 0, 0, missing)

        val averageTermConfidence = termConfidences.average()
        val completeness = pricedCount.toDouble() / modifiers.size
        val confidence = ((plain.confidence + averageTermConfidence) / 2.0 * completeness).roundToInt().coerceIn(0, 100)

        return ModifierEstimate(plain.fairPrice + totalDelta, confidence, pricedCount, missing)
    }
}
