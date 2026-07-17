package dev.saibon.market

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Auction Flip Finder" settings section, governing the sales-history engine behind `/saibonflips`'s Auction House finder. Called once from `SaibonClient.onInitializeClient()`. */
object FlipFinderSettings {
    private val REFRESH_INTERVALS = listOf(180, 300, 600, 900)
    private val MIN_SAMPLE_OPTIONS = listOf(1, 3, 5, 10)

    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.DATA, "Auction Flip Finder") {
                val config = Saibon.config.data.market

                label("Estimates use item-id-level median sale price; ignores enchants/reforges/stars/pets.")
                toggle("Track recent sales (powers /saibonflips' Auction House finder)", { config.salesHistoryAutoRefresh }) {
                    config.salesHistoryAutoRefresh = it
                    Saibon.config.save()
                    AuctionSalesHistoryRepository.rescheduleRefresh()
                }
                dropdown(
                    "Refresh interval",
                    REFRESH_INTERVALS,
                    { config.salesHistoryRefreshIntervalSeconds },
                    { seconds -> "${seconds / 60}m" }
                ) {
                    config.salesHistoryRefreshIntervalSeconds = it
                    Saibon.config.save()
                    AuctionSalesHistoryRepository.rescheduleRefresh()
                }
                button("Refresh sales history now") {
                    AuctionSalesHistoryRepository.refreshNow()
                }
                dropdown(
                    "Minimum sales before ranking an item",
                    MIN_SAMPLE_OPTIONS,
                    { config.salesHistoryMinSamples },
                    { "$it" }
                ) {
                    config.salesHistoryMinSamples = it
                    Saibon.config.save()
                }
                slider(
                    "Estimated AH tax rate",
                    0f, 5f, { config.ahTaxRatePercent.toFloat() },
                    { "%.1f%%".format(it) }
                ) {
                    config.ahTaxRatePercent = it.toDouble()
                    Saibon.config.save()
                }
            }
        )
    }
}
