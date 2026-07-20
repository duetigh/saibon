package dev.saibon.market.flip

import dev.saibon.data.model.SkyblockItem

/**
 * One ranked flip suggestion, produced by a [FlipFinder]. Mirrors the pasted
 * "NEU-Reforged" spec's §4.4 `FlipCandidate` shape: enough for `FlipScreen` to
 * render any finder's output in one shared table, and enough for the player
 * to judge *why* it's listed (`reason`) rather than trusting an opaque score.
 * Display/alert only, same as the rest of `dev.saibon.market` — nothing here
 * buys, bids, or lists anything.
 */
data class FlipCandidate(
    val item: SkyblockItem,
    /** e.g. `"SHARPNESS_7,HOT_POTATO_10"` for an auction flip, or `""` when a finder has no per-listing modifiers (Bazaar/craft/NPC flips are id-only). */
    val modifierSignature: String,
    val cost: Double,
    val estimatedValue: Double,
    val estimatedProfit: Double,
    val marginPercent: Double,
    /** 0-100 confidence score where the finder has one (see [dev.saibon.market.FairPriceCalculator]) — otherwise a flat 100 for finders with no statistical basis (Bazaar/NPC/craft flips price off a live quote, not a sample). */
    val confidence: Int,
    /** Sales/week for the reference price backing this candidate, or null for finders with no sales-history basis. Shown next to [confidence] so the player can judge liquidity, not just price accuracy. */
    val volumePerWeek: Int? = null,
    /** [dev.saibon.market.CraftFlip.profitPerHour] passthrough — only set for `Craft Flip` candidates backed by a `RecipeType.FORGE` recipe with a known duration, null everywhere else. */
    val profitPerHour: Double? = null,
    val sourceFinder: String,
    val reason: String,
    /** The specific listing [cost]/[estimatedValue] were computed from, for a `/viewauction <uuid>` command — only set by finders backed by one real, non-fungible auction listing (`AuctionFlipFinder`). Bazaar/craft/NPC flips have no single listing to point at, so this stays null there. */
    val auctionUuid: String? = null
)

/** One flip-detection strategy. Implementations are pure/parameter-injected where the existing ranking objects already are (`AuctionFlipRanking`, `BazaarFlipRanking`, `CraftFlipRanking`) — `scan()` itself is the only place allowed to read live client-side repositories. */
fun interface FlipFinder {
    val name: String get() = this::class.simpleName ?: "FlipFinder"
    fun scan(): List<FlipCandidate>
}
