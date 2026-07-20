package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.market.AuctionFlipRanking
import dev.saibon.market.AuctionHouseTax
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.AuctionSalesHistoryRepository
import dev.saibon.market.CraftFlipRanking
import dev.saibon.market.InstasellPricing
import dev.saibon.market.MayorRepository
import dev.saibon.market.ModifierValueModel

/**
 * Wraps the pre-existing item-id-level [AuctionFlipRanking] (math unchanged)
 * for the common case, and adds two further candidate streams per observed
 * modifier-signature bucket: an exact-match tier for combos with enough
 * identical past sales (the spec's "matching modifiers" ask, §4.1.1), and a
 * [ModifierValueModel] tier for everything else that carries at least one
 * priceable modifier — a rare-but-real combo no longer gets excluded from
 * pricing entirely, it just gets a lower-confidence calculated estimate
 * instead of a direct match.
 */
object AuctionFlipFinder : FlipFinder {
    override val name = "Auction House"

    /**
     * A *plain* item's recursive craft cost (Bazaar buy-order prices, walked
     * through sub-recipes/forge fees via [CraftFlipRanking]) is a hard
     * ceiling on what its AH sales history should say it's worth — nobody
     * rationally pays an AH seller more than they'd pay to just make one.
     * This only applies to the item-level ("plain") tier below: a modified
     * copy (gems/scrolls/enchants — the `signatureLevel`/`estimatedLevel`
     * tiers) is legitimately worth more than the base craft cost, so it's
     * never capped. Returns null when the item has no recipe or the recipe
     * can't be fully priced, in which case the raw sales-based estimate is
     * used unchanged, same as before this cap existed.
     */
    private fun craftCostCeiling(itemId: String): Double? {
        if (DataRepository.recipesFor(itemId).isEmpty()) return null
        return CraftFlipRanking.craftCostOf(
            itemId,
            recipesOf = DataRepository::recipesFor,
            marketCostOf = { id -> IngredientPriceResolver.costOf(id) },
            marketCostOfQuantity = IngredientPriceResolver::costOfQuantity
        )
    }

    override fun scan(): List<FlipCandidate> {
        val marketConfig = Saibon.config.data.market
        val flipConfig = Saibon.config.data.flip
        val sellableItems = DataRepository.allItems().filter { !flipConfig.excludeSoulbound || !it.soulbound }
        val derpyActive = MayorRepository.isDerpyActive()

        val itemLevel = AuctionFlipRanking.bestFlips(
            sellableItems,
            lowestBinOf = { AuctionPriceRepository.lowestBin(it.id)?.lowestBin?.toDouble() },
            fairPriceOf = { AuctionSalesHistoryRepository.saleReference(it.id) },
            taxRatePercent = marketConfig.ahTaxRatePercent,
            minSamples = marketConfig.salesHistoryMinSamples
        ).map { flip ->
            val ceiling = craftCostCeiling(flip.item.id)
            val rawFairPrice = flip.fairPrice.fairPrice
            val cappedFairPrice = if (ceiling != null) minOf(rawFairPrice, ceiling) else rawFairPrice
            // Liquidity-scaled "sell it promptly" target, not the raw statistical fair price
            // (doc §3.8) — a thin/illiquid item's estimated profit shouldn't assume a
            // full-price instant resale.
            val instasellTarget = InstasellPricing.instasellTarget(cappedFairPrice, flip.fairPrice.volumePerWeek)
            val netValue = AuctionHouseTax.netOfTax(instasellTarget, marketConfig.ahTaxRatePercent, derpyActive)
            val profit = netValue - flip.lowestBin
            FlipCandidate(
                item = flip.item,
                modifierSignature = "",
                cost = flip.lowestBin,
                estimatedValue = instasellTarget,
                estimatedProfit = profit,
                marginPercent = profit / flip.lowestBin * 100.0,
                confidence = flip.fairPrice.confidence,
                volumePerWeek = flip.fairPrice.volumePerWeek,
                sourceFinder = name,
                reason = if (cappedFairPrice < rawFairPrice)
                    "capped at craft cost (${ceiling!!.toLong()}) — sales history alone suggested ${rawFairPrice.toLong()} from only ${flip.fairPrice.sampleCount} sales"
                else
                    "fair price from ${flip.fairPrice.sampleCount} recent sales (median ${flip.fairPrice.median.toLong()}), instasell target ${instasellTarget.toLong()}",
                auctionUuid = AuctionPriceRepository.lowestBin(flip.item.id)?.lowestBinUuid?.takeIf { it.isNotEmpty() },
                sellerUuid = AuctionPriceRepository.lowestBin(flip.item.id)?.lowestBinSeller?.takeIf { it.isNotEmpty() }
            )
        }

        val itemsById = sellableItems.associateBy { it.id.uppercase() }
        val exactMatchedKeys = mutableSetOf<String>()
        val signatureLevel = AuctionSalesHistoryRepository.allSignatureReferences().mapNotNull { (key, reference) ->
            if (reference.sampleCount < flipConfig.modifierMatchMinSamples) return@mapNotNull null
            val lowestBin = AuctionPriceRepository.allSignatureLowestBins()[key] ?: return@mapNotNull null
            val cost = lowestBin.lowestBin.toDouble()
            if (cost <= 0) return@mapNotNull null

            val itemId = key.substringBefore('|')
            val signature = key.substringAfter('|')
            val item = itemsById[itemId] ?: return@mapNotNull null // also excludes soulbound items, filtered out of sellableItems above

            exactMatchedKeys += key
            val instasellTarget = InstasellPricing.instasellTarget(reference.fairPrice, reference.volumePerWeek)
            val netValue = AuctionHouseTax.netOfTax(instasellTarget, marketConfig.ahTaxRatePercent, derpyActive)
            val profit = netValue - cost
            FlipCandidate(
                item = item,
                modifierSignature = signature,
                cost = cost,
                estimatedValue = instasellTarget,
                estimatedProfit = profit,
                marginPercent = profit / cost * 100.0,
                confidence = reference.confidence,
                volumePerWeek = reference.volumePerWeek,
                sourceFinder = name,
                reason = "fair price from ${reference.sampleCount} recent sales matching modifiers ($signature)",
                auctionUuid = lowestBin.lowestBinUuid.takeIf { it.isNotEmpty() },
                sellerUuid = lowestBin.lowestBinSeller.takeIf { it.isNotEmpty() }
            )
        }

        // Tier 2: no exact-signature match (or not enough samples to trust one) but the listing
        // carries at least one individually-priceable modifier — estimate instead of excluding.
        val estimatedLevel = AuctionPriceRepository.allSignatureLowestBins().mapNotNull { (key, lowestBin) ->
            if (key in exactMatchedKeys || lowestBin.modifiers.isEmpty()) return@mapNotNull null
            val cost = lowestBin.lowestBin.toDouble()
            if (cost <= 0) return@mapNotNull null

            val itemId = key.substringBefore('|')
            val item = itemsById[itemId] ?: return@mapNotNull null
            val plain = AuctionSalesHistoryRepository.saleReference(itemId) ?: return@mapNotNull null

            val estimate = ModifierValueModel.estimate(
                plain = plain,
                modifiers = lowestBin.modifiers,
                // `modifier.poolKey` lines up with a real signatureHistory bucket for every
                // modifier kind, since AuctionItemDecoder.modifierSignature() is now derived
                // from the same itemModifiers() list `poolKey` comes from. A single-modifier
                // listing's poolKey therefore always matches its own signature bucket key;
                // multi-modifier listings still commonly miss (their bucket key is the joined
                // combo, not one modifier's poolKey) and fall through to pooledDelta below.
                perItemDelta = { modifier ->
                    AuctionSalesHistoryRepository.saleReference(itemId, modifier.poolKey)
                        ?.takeIf { it.sampleCount >= flipConfig.modifierDeltaMinSamples }
                        ?.let { it.copy(fairPrice = it.fairPrice - plain.fairPrice) }
                },
                pooledDelta = { modifier -> AuctionSalesHistoryRepository.modifierDeltaReference(modifier.kind, modifier.key) }
            )
            if (estimate.pricedCount == 0) return@mapNotNull null

            val instasellTarget = InstasellPricing.instasellTarget(estimate.fairPrice, plain.volumePerWeek)
            val netValue = AuctionHouseTax.netOfTax(instasellTarget, marketConfig.ahTaxRatePercent, derpyActive)
            val profit = netValue - cost
            FlipCandidate(
                item = item,
                modifierSignature = key.substringAfter('|'),
                cost = cost,
                estimatedValue = instasellTarget,
                estimatedProfit = profit,
                marginPercent = profit / cost * 100.0,
                confidence = estimate.confidence,
                volumePerWeek = plain.volumePerWeek,
                sourceFinder = name,
                reason = "estimated: base price + ${estimate.pricedCount}/${lowestBin.modifiers.size} modifiers priced",
                auctionUuid = lowestBin.lowestBinUuid.takeIf { it.isNotEmpty() },
                sellerUuid = lowestBin.lowestBinSeller.takeIf { it.isNotEmpty() }
            )
        }

        return (itemLevel + signatureLevel + estimatedLevel).sortedByDescending { it.estimatedProfit }
    }
}
