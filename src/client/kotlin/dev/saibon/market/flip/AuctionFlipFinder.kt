package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
import dev.saibon.market.AuctionFlipRanking
import dev.saibon.market.AuctionHouseTax
import dev.saibon.market.AuctionPriceRepository
import dev.saibon.market.AuctionSalesHistoryRepository
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

    override fun scan(): List<FlipCandidate> {
        val marketConfig = Saibon.config.data.market
        val flipConfig = Saibon.config.data.flip
        val sellableItems = DataRepository.allItems().filter { !flipConfig.excludeSoulbound || !it.soulbound }

        val itemLevel = AuctionFlipRanking.bestFlips(
            sellableItems,
            lowestBinOf = { AuctionPriceRepository.lowestBin(it.id)?.lowestBin?.toDouble() },
            fairPriceOf = { AuctionSalesHistoryRepository.saleReference(it.id) },
            taxRatePercent = marketConfig.ahTaxRatePercent,
            minSamples = marketConfig.salesHistoryMinSamples
        ).map { flip ->
            FlipCandidate(
                item = flip.item,
                modifierSignature = "",
                cost = flip.lowestBin,
                estimatedValue = flip.fairPrice.fairPrice,
                estimatedProfit = flip.estimatedProfit,
                marginPercent = flip.profitPercent,
                confidence = flip.fairPrice.confidence,
                volumePerWeek = flip.fairPrice.volumePerWeek,
                sourceFinder = name,
                reason = "fair price from ${flip.fairPrice.sampleCount} recent sales (median ${flip.fairPrice.median.toLong()})",
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
            val netValue = AuctionHouseTax.netOfTax(reference.fairPrice, marketConfig.ahTaxRatePercent)
            val profit = netValue - cost
            FlipCandidate(
                item = item,
                modifierSignature = signature,
                cost = cost,
                estimatedValue = reference.fairPrice,
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
                // `modifier.poolKey` only lines up with a real signatureHistory bucket for the
                // kinds AuctionItemDecoder.modifierSignature() itself tracks in that exact
                // "kind:key" shape (reforge/potato/stars/single-enchant) — recomb's signature
                // token is the bare string "recomb" (no ":applied"), and gem/enrich/scroll/
                // book/upgrade aren't in modifierSignature() at all, so those simply find no
                // bucket and fall through to pooledDelta below. Harmless (never a wrong match,
                // just a missed per-item optimization for those kinds) — see AuctionItemDecoder's
                // doc comment on why the two representations are intentionally decoupled.
                perItemDelta = { modifier ->
                    AuctionSalesHistoryRepository.saleReference(itemId, modifier.poolKey)
                        ?.takeIf { it.sampleCount >= flipConfig.modifierDeltaMinSamples }
                        ?.let { it.copy(fairPrice = it.fairPrice - plain.fairPrice) }
                },
                pooledDelta = { modifier -> AuctionSalesHistoryRepository.modifierDeltaReference(modifier.kind, modifier.key) }
            )
            if (estimate.pricedCount == 0) return@mapNotNull null

            val netValue = AuctionHouseTax.netOfTax(estimate.fairPrice, marketConfig.ahTaxRatePercent)
            val profit = netValue - cost
            FlipCandidate(
                item = item,
                modifierSignature = key.substringAfter('|'),
                cost = cost,
                estimatedValue = estimate.fairPrice,
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
