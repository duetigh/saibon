package dev.saibon.market

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * One confirmed AH sale, as recorded by
 * [dev.saibon.market.AuctionSalesHistoryRepository] (or the server-side
 * aggregator).
 *
 * @param sellerHash seller UUID's `hashCode()`, or `0` when identity wasn't
 *   captured for this sample (the bundled server snapshot doesn't publish
 *   it, nor do [ModifierValueModel]'s synthetic per-modifier delta entries)
 *   — [AntiManipulationFilter] treats `0` as "unknown, skip identity checks"
 *   rather than a real (collision-prone) hash bucket.
 * @param buyerHash same, for the winning bidder/BIN buyer.
 * @param weight always reset to `1.0` at the top of [AntiManipulationFilter.apply]
 *   before that pass applies its own damping — a value read back from disk
 *   (or passed in from elsewhere) is never trusted directly.
 */
data class SaleSample(
    val price: Long,
    val timestampMillis: Long,
    val sellerHash: Int = 0,
    val buyerHash: Int = 0,
    val weight: Double = 1.0
)

/**
 * A SKU's computed reference price plus the evidence behind it. [fairPrice] is
 * what [dev.saibon.market.AuctionFlipRanking]/[dev.saibon.market.flip.AuctionFlipFinder]
 * should compare a listing against; [median]/[weightedMean]/[stddev] are exposed
 * so the UI can show its work instead of a single opaque number.
 */
data class FairPriceResult(
    val fairPrice: Double,
    val median: Double,
    val weightedMean: Double,
    val sampleCount: Int,
    val volumePerWeek: Int,
    /** 0-100, see [FairPriceCalculator.compute] — not a raw sample count. */
    val confidence: Int,
    val stddev: Double
)

/**
 * Pure, parameter-injected fair-price statistics — no client dependency, same
 * shape as [AuctionFlipRanking]/[CraftFlipRanking]. Implements the pasted
 * Cofl-style spec's §5.5/§5.6 formulas:
 *
 * 1. IQR-outlier-strip the raw sample (kills mistake listings/wash trades
 *    before they skew anything).
 * 2. `fairPrice` blends an exponential-recency-decay weighted mean with the
 *    plain median, leaning on the median more for thin/volatile samples
 *    (`alpha` shrinks with sample size and with coefficient-of-variation).
 * 3. `confidence` (0-100) rewards sample size, recency of the most recent
 *    sale, and tight clustering. The spec's fourth term — "does craft-value
 *    agree with market-value" — is deliberately omitted here to keep this
 *    module free of any dependency on [CraftFlipRanking]; its 10 points are
 *    redistributed across the other three (45/35/20 instead of 40/30/20+10).
 */
object FairPriceCalculator {
    private const val HALF_LIFE_HOURS = 36.0
    private const val TARGET_SAMPLE_SIZE = 20
    private const val STALENESS_CUTOFF_HOURS = 72.0
    private const val WEEK_MILLIS = 7L * 24 * 3_600_000
    private val DECAY_LAMBDA = ln(2.0) / HALF_LIFE_HOURS

    fun compute(samples: List<SaleSample>, now: Long = System.currentTimeMillis()): FairPriceResult? {
        if (samples.isEmpty()) return null

        // Seller/buyer-identity-based screening (wash trades, self-trade underlistings,
        // a single seller dominating the expensive end of the range) runs first, ahead
        // of the price-magnitude-only IQR strip below — see AntiManipulationFilter.
        val screened = AntiManipulationFilter.apply(samples)
        val filtered = stripOutliers(screened)
        if (filtered.isEmpty()) return null

        val sortedPrices = filtered.map { it.price }.sorted()
        val medianValue = median(sortedPrices)
        val weightedMeanValue = weightedMean(filtered, now)
        val meanValue = sortedPrices.average()
        val sd = stddev(sortedPrices, meanValue)
        val cv = if (meanValue > 0) sd / meanValue else 0.0

        // No lower floor: a thin sample (few completed sales, the common case for
        // high-value/low-volume items like rare enchant books) leans on the median
        // instead of the recency-weighted mean, since 1-2 stale or inflated sales
        // shouldn't single-handedly set fairPrice the way they would at floor 0.5.
        val sizeFactor = (filtered.size.toDouble() / TARGET_SAMPLE_SIZE).coerceIn(0.0, 1.0)
        val cvFactor = (1.0 - cv).coerceIn(0.0, 1.0)
        val alpha = sizeFactor * cvFactor
        val fairPrice = alpha * weightedMeanValue + (1 - alpha) * medianValue

        val sampleTerm = (filtered.size.toDouble() / TARGET_SAMPLE_SIZE).coerceIn(0.0, 1.0) * 45.0
        val hoursSinceLastSale = (now - samples.maxOf { it.timestampMillis }).coerceAtLeast(0) / 3_600_000.0
        val recencyTerm = (1.0 - hoursSinceLastSale / STALENESS_CUTOFF_HOURS).coerceIn(0.0, 1.0) * 35.0
        val tightnessTerm = (1.0 - cv).coerceIn(0.0, 1.0) * 20.0
        val confidence = (sampleTerm + recencyTerm + tightnessTerm).toInt().coerceIn(0, 100)

        val volumePerWeek = filtered.count { now - it.timestampMillis <= WEEK_MILLIS }

        return FairPriceResult(
            fairPrice = fairPrice,
            median = medianValue,
            weightedMean = weightedMeanValue,
            sampleCount = filtered.size,
            volumePerWeek = volumePerWeek,
            confidence = confidence,
            stddev = sd
        )
    }

    /**
     * Drops samples outside `Q1 - 1.5*IQR .. Q3 + 1.5*IQR` once there are
     * enough samples (4+) for quartiles to be meaningful. Below that, a thin
     * market (3 samples is common for a rare high-value enchant book) falls
     * back to a looser median-ratio bound (`0.4x .. 2.5x` the median) —
     * still enough to catch a single wildly-mispriced BIN sale without
     * needing real quartiles. 1-2 samples can't distinguish "the outlier"
     * from "the real price" at all, so they pass through untouched.
     */
    private fun stripOutliers(samples: List<SaleSample>): List<SaleSample> {
        if (samples.size < 3) return samples
        val sortedPrices = samples.map { it.price }.sorted()
        val kept = if (samples.size < 4) {
            val med = median(sortedPrices)
            samples.filter { it.price.toDouble() in (med * 0.4)..(med * 2.5) }
        } else {
            val q1 = percentile(sortedPrices, 25.0)
            val q3 = percentile(sortedPrices, 75.0)
            val iqr = q3 - q1
            val lower = q1 - 1.5 * iqr
            val upper = q3 + 1.5 * iqr
            samples.filter { it.price.toDouble() in lower..upper }
        }
        return kept.ifEmpty { samples }
    }

    private fun percentile(sorted: List<Long>, pct: Double): Double {
        if (sorted.size == 1) return sorted[0].toDouble()
        val index = (pct / 100.0) * (sorted.size - 1)
        val lowerIndex = index.toInt()
        val upperIndex = (lowerIndex + 1).coerceAtMost(sorted.size - 1)
        val frac = index - lowerIndex
        return sorted[lowerIndex] + (sorted[upperIndex] - sorted[lowerIndex]) * frac
    }

    private fun median(sortedPrices: List<Long>): Double {
        val mid = sortedPrices.size / 2
        return if (sortedPrices.size % 2 == 0) {
            (sortedPrices[mid - 1] + sortedPrices[mid]) / 2.0
        } else {
            sortedPrices[mid].toDouble()
        }
    }

    private fun weightedMean(samples: List<SaleSample>, now: Long): Double {
        var weightedSum = 0.0
        var weightTotal = 0.0
        for (sample in samples) {
            val ageHours = (now - sample.timestampMillis).coerceAtLeast(0) / 3_600_000.0
            // sample.weight carries AntiManipulationFilter's dominant-seller damping (halved, not
            // dropped, so a thin bucket doesn't lose the sample entirely) into the term that's
            // most sensitive to a few repeated high-priced listings from one actor.
            val weight = exp(-DECAY_LAMBDA * ageHours) * sample.weight
            weightedSum += sample.price * weight
            weightTotal += weight
        }
        return if (weightTotal > 0) weightedSum / weightTotal else samples.map { it.price.toDouble() }.average()
    }

    private fun stddev(prices: List<Long>, mean: Double): Double {
        if (prices.size < 2) return 0.0
        val variance = prices.sumOf { (it - mean) * (it - mean) } / prices.size
        return sqrt(variance)
    }
}
