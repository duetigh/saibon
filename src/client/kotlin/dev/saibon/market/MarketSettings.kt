package dev.saibon.market

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Market" settings sections. Called once from `SaibonClient.onInitializeClient()`. */
object MarketSettings {
    private val REFRESH_INTERVALS = listOf(30, 60, 120, 300)
    private val AH_REFRESH_INTERVALS = listOf(60, 120, 300, 600, 900, 1800)

    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.DATA, "Market Prices") {
                val config = Saibon.config.data.market

                toggle("Auto-refresh Bazaar prices", { config.autoRefresh }) {
                    config.autoRefresh = it
                    Saibon.config.save()
                    MarketPriceRepository.rescheduleRefresh()
                }
                dropdown(
                    "Refresh interval",
                    REFRESH_INTERVALS,
                    { config.refreshIntervalSeconds },
                    { seconds -> "${seconds}s" }
                ) {
                    config.refreshIntervalSeconds = it
                    Saibon.config.save()
                    MarketPriceRepository.rescheduleRefresh()
                }
                button("Refresh prices now") {
                    MarketPriceRepository.refreshNow()
                }
                slider(
                    "Minimum flip margin",
                    0f, 50f, { config.flipMinMarginPercent.toFloat() },
                    { "${it.toInt()}%" }
                ) {
                    config.flipMinMarginPercent = it.toDouble()
                    Saibon.config.save()
                }
            }
        )
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.DATA, "Auction House Prices") {
                val config = Saibon.config.data.market

                toggle(
                    "Auto-refresh lowest-BIN prices (heavier: pages through every active auction)",
                    { config.ahAutoRefresh }
                ) {
                    config.ahAutoRefresh = it
                    Saibon.config.save()
                    AuctionPriceRepository.rescheduleRefresh()
                }
                dropdown(
                    "Refresh interval",
                    AH_REFRESH_INTERVALS,
                    { config.ahRefreshIntervalSeconds },
                    { seconds -> "${seconds / 60}m" }
                ) {
                    config.ahRefreshIntervalSeconds = it
                    Saibon.config.save()
                    AuctionPriceRepository.rescheduleRefresh()
                }
                button("Refresh AH prices now") {
                    AuctionPriceRepository.refreshNow()
                }
                slider(
                    "Overpay warning threshold",
                    1.1f, 3.0f, { config.overpayWarningThreshold.toFloat() },
                    { "%.1fx".format(it) }
                ) {
                    config.overpayWarningThreshold = it.toDouble()
                    Saibon.config.save()
                }
                toggle("Highlight AH/Bazaar search matches in the real menu", { config.menuOverlayEnabled }) {
                    config.menuOverlayEnabled = it
                    Saibon.config.save()
                }
            }
        )
    }
}
