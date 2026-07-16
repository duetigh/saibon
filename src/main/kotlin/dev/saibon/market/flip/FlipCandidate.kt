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
    /** Sample count or other confidence signal a finder used to compute [estimatedValue] — shown next to `reason`, not a 0-1 score, so the player sees the actual evidence. */
    val confidence: Int,
    val sourceFinder: String,
    val reason: String,
    /** The specific listing [cost]/[estimatedValue] were computed from, for a `/viewauction <uuid>` copy-command button — only set by finders backed by one real, non-fungible auction listing (`AuctionFlipFinder`). Bazaar/craft/NPC flips have no single listing to point at, so this stays null there. */
    val auctionUuid: String? = null
)

/** One flip-detection strategy. Implementations are pure/parameter-injected where the existing ranking objects already are (`AuctionFlipRanking`, `BazaarFlipRanking`, `CraftFlipRanking`) — `scan()` itself is the only place allowed to read live client-side repositories. */
fun interface FlipFinder {
    val name: String get() = this::class.simpleName ?: "FlipFinder"
    fun scan(): List<FlipCandidate>
}
