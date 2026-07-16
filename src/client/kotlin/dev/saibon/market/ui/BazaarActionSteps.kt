package dev.saibon.market.ui

import dev.saibon.data.model.SkyblockItem

/**
 * Concrete [ClickStep] sequences for the four Bazaar quick actions. Every
 * literal string/pattern here — category button text, action-label text,
 * screen title conventions — is historical/training knowledge of Hypixel's
 * Bazaar GUI, matched fuzzily (case-insensitive contains, not exact-string)
 * precisely because none of it is confirmed live from this sandbox.
 * TODO verify against a live server before relying on this, same convention
 * [MarketMenuOverlay] already uses for its own unverified regexes.
 *
 * **Deliberately reduced v1 scope**: the sequence stops once it has opened
 * the named action's own menu (e.g. "Buy Instantly"'s quantity screen) — it
 * never guesses which preset-quantity/suggested-price slot to click next.
 * That final, money-committing click stays a genuine manual click by the
 * player on the real screen. This trades a little convenience for a much
 * smaller blast radius if a category/product name guess is wrong: worst
 * case the sequence aborts (title/slot mismatch) or lands on the wrong
 * product's action menu, never on an actual purchase.
 */
object BazaarActionSteps {
    private val BAZAAR_MAIN_TITLE = Regex("^Bazaar$", RegexOption.IGNORE_CASE)

    fun forAction(action: BazaarAction, item: SkyblockItem, alreadyOnProductScreen: Boolean): List<ClickStep> = buildList {
        if (!alreadyOnProductScreen) {
            add(navigateToCategoryStep(item))
            add(navigateToProductStep(item))
        }
        add(openActionMenuStep(action))
    }

    private fun navigateToCategoryStep(item: SkyblockItem) = ClickStep(
        expectedTitlePattern = BAZAAR_MAIN_TITLE,
        description = "Open the \"${item.category}\" category"
    ) { slot -> slot.item.hoverName.string.contains(item.category, ignoreCase = true) }

    /** Category screen's title convention is unverified, so only the slot text is checked for this step. */
    private fun navigateToProductStep(item: SkyblockItem) = ClickStep(
        expectedTitlePattern = null,
        description = "Open \"${item.name}\""
    ) { slot -> slot.item.hoverName.string.contains(item.name, ignoreCase = true) }

    private fun openActionMenuStep(action: BazaarAction) = ClickStep(
        expectedTitlePattern = null,
        description = "Select \"${action.label}\" (pick your own quantity/price on the real screen from here)"
    ) { slot -> slot.item.hoverName.string.contains(action.label, ignoreCase = true) }
}
