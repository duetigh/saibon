package dev.saibon.market.model

/**
 * One atomic, individually-priceable upgrade an auction item carries — e.g.
 * `ItemModifier("ench", "sharpness7")` or `ItemModifier("gem", "COMBAT_0:PERFECT")`.
 * Produced by [dev.saibon.market.AuctionItemDecoder]'s `itemModifiers()`, a
 * decomposition *independent* of that same decoder's `modifierSignature`
 * string (which stays a single joined-and-comma-collapsed key for exact-match
 * bucketing) — this list exists so [dev.saibon.market.ModifierValueModel] can
 * price each upgrade on its own instead of requiring an identical full combo
 * to have sold before.
 */
data class ItemModifier(val kind: String, val key: String) {
    /** Stable lookup key for the pooled cross-item delta tables (`AuctionSalesHistoryRepository.modifierDeltaHistory`, `DataRepository.modifierValueSnapshot`). */
    val poolKey: String get() = "$kind:$key"
}
