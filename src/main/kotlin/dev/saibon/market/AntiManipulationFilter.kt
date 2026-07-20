package dev.saibon.market

/**
 * Seller/buyer-identity-based sale-sample screening, run once per
 * [FairPriceCalculator.compute] call ahead of its own price-magnitude-only
 * IQR outlier strip — an adaptation of Coflnet SkySniper's
 * `ApplyAntiMarketManipulation` (the reverse-engineering doc's §3.3) to this
 * mod's much smaller per-item buckets (tens of local samples, not the
 * hundreds-plus SkySniper aggregates across every player on the server).
 *
 * Two of SkySniper's passes translate directly without shrinking a thin
 * bucket too aggressively. Its "keep exactly one sample per seller, drop the
 * rest" dedup pass is deliberately NOT ported: on a 10-30 sample bucket that
 * would gut a legitimately thin market instead of just trimming a
 * manipulator's excess, so a proportional weight-halving (see
 * [dampenDominantSeller]) stands in for it here too.
 *
 * A no-op for any sample whose [SaleSample.sellerHash] is `0` (identity
 * unknown) — true for every sample sourced from the bundled `fair_prices`
 * snapshot (identity isn't published in that dataset), from
 * [ModifierValueModel]'s synthetic per-modifier delta entries, or recorded
 * before this mod started capturing `seller`/`buyer` from `auctions_ended`.
 */
object AntiManipulationFilter {
    fun apply(samples: List<SaleSample>): List<SaleSample> {
        // Too few samples to distinguish a real actor pattern from coincidence — and every
        // rule below needs at least a couple of matches to mean anything.
        if (samples.size < 3) return samples

        // Never trust an inbound weight (e.g. deserialized from disk) — always start this
        // pass from a clean 1.0 and let the rules below set it fresh every time.
        val normalized = samples.map { if (it.weight == 1.0) it else it.copy(weight = 1.0) }
        val washTradesDropped = dropWashTrades(normalized)
        val underlistingsDropped = dropUnderlistings(washTradesDropped)
        return dampenDominantSeller(underlistingsDropped)
    }

    /**
     * A specific (seller, buyer) pair transacting more than once looks like
     * two accounts trading back and forth rather than one seller with many
     * different customers — every sample involving that seller is excluded
     * entirely (SkySniper's own treatment, not just damped).
     */
    private fun dropWashTrades(samples: List<SaleSample>): List<SaleSample> {
        val identified = samples.filter { it.sellerHash != 0 && it.buyerHash != 0 }
        if (identified.size < 2) return samples

        val pairCounts = HashMap<Long, Int>()
        for (s in identified) {
            val key = pairKey(s.sellerHash, s.buyerHash)
            pairCounts[key] = (pairCounts[key] ?: 0) + 1
        }
        val washSellers = identified.filter { (pairCounts[pairKey(it.sellerHash, it.buyerHash)] ?: 0) > 1 }
            .map { it.sellerHash }.toSet()
        if (washSellers.isEmpty()) return samples

        val kept = samples.filterNot { it.sellerHash in washSellers }
        return kept.ifEmpty { samples } // a heuristic should never empty the whole bucket
    }

    /** Order-independent pairing key for a (sellerHash, buyerHash) pair, so seller-then-buyer and buyer-then-seller collide to the same bucket. */
    private fun pairKey(a: Int, b: Int): Long {
        val lo = minOf(a, b).toLong() and 0xFFFFFFFFL
        val hi = maxOf(a, b).toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    /**
     * A seller who also shows up as a buyer elsewhere in this same bucket,
     * at a price at-or-below what the rest of the bucket would otherwise
     * average — a self-trade underlisting a plain copy of the item to fake
     * a low "sold" price. Guarded (`avgExcludingHit < hit.price` skips the
     * removal) so it only ever drops a below-average hit, never a
     * legitimately high self-purchase.
     */
    private fun dropUnderlistings(samples: List<SaleSample>): List<SaleSample> {
        val sellerIds = samples.mapNotNull { it.sellerHash.takeIf { h -> h != 0 } }.toSet()
        val buyerIds = samples.mapNotNull { it.buyerHash.takeIf { h -> h != 0 } }.toSet()
        val selfTraders = sellerIds.intersect(buyerIds)
        if (selfTraders.isEmpty()) return samples

        val toDrop = ArrayList<SaleSample>()
        for (hit in samples) {
            if (hit.sellerHash !in selfTraders) continue
            val rest = samples.filter { it !== hit }
            if (rest.isEmpty()) continue
            val avgExcludingHit = rest.map { it.price }.average()
            if (avgExcludingHit < hit.price) continue // hit is already on the pricier side — not a suspicious underlisting
            toDrop += hit
        }
        if (toDrop.isEmpty()) return samples
        val kept = samples.filterNot { candidate -> toDrop.any { it === candidate } }
        return kept.ifEmpty { samples }
    }

    /**
     * If one seller accounts for at least a third of the top-priced half of
     * the bucket (or at least 3 samples, whichever is larger) — i.e. one
     * account is disproportionately responsible for the *expensive* end of
     * the range — that seller's samples are half-weighted rather than
     * dropped, damping a suspected self-inflated price run without
     * discarding data a thin bucket can't spare.
     */
    private fun dampenDominantSeller(samples: List<SaleSample>): List<SaleSample> {
        val topHalf = samples.sortedByDescending { it.price }.take((samples.size + 1) / 2)
        val topHalfWithSeller = topHalf.filter { it.sellerHash != 0 }
        if (topHalfWithSeller.size < 3) return samples

        val bySeller = topHalfWithSeller.groupingBy { it.sellerHash }.eachCount()
        val threshold = maxOf(topHalfWithSeller.size / 3, 3)
        val dominant = bySeller.filterValues { it >= threshold }.keys
        if (dominant.isEmpty()) return samples

        return samples.map { if (it.sellerHash in dominant) it.copy(weight = it.weight * 0.5) else it }
    }
}
