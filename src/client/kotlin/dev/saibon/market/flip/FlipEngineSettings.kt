package dev.saibon.market.flip

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Flip Finder" (`/saibonflips`) settings section — one enable toggle per [FlipFinder] strategy plus the shared scan interval and alert thresholds. Distinct from `FlipFinderSettings`, which still governs the Auction House finder's narrower sales-history engine specifically. */
object FlipEngineSettings {
    private val SCAN_INTERVALS = listOf(15, 30, 60, 120, 300)

    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.DATA, "Flip Finder (/saibonflips)") {
                val config = Saibon.config.data.flip

                label("Research/alerting only — never buys, bids, or lists anything. Manually click the real listing yourself.")
                toggle("Auction House flips", { config.auctionFlipsEnabled }) { config.auctionFlipsEnabled = it; Saibon.config.save() }
                toggle("Bazaar margin flips", { config.bazaarMarginFlipsEnabled }) { config.bazaarMarginFlipsEnabled = it; Saibon.config.save() }
                toggle("NPC flips", { config.npcFlipsEnabled }) { config.npcFlipsEnabled = it; Saibon.config.save() }
                toggle("Craft flips", { config.craftFlipsEnabled }) { config.craftFlipsEnabled = it; Saibon.config.save() }
                dropdown("Scan interval", SCAN_INTERVALS, { config.scanIntervalSeconds }, { "${it}s" }) {
                    config.scanIntervalSeconds = it
                    Saibon.config.save()
                    FlipEngine.rescheduleScan()
                }
                label("Alerts (flip-alert HUD toast)")
                toggle("Show alert toast for high-confidence flips", { config.alertEnabled }) { config.alertEnabled = it; Saibon.config.save() }
                slider("Minimum profit to alert", 0f, 1_000_000f, { config.alertMinProfit.toFloat() }, { "%,.0f coins".format(it) }) {
                    config.alertMinProfit = it.toDouble()
                    Saibon.config.save()
                }
                slider("Minimum margin % to alert", 0f, 100f, { config.alertMinMarginPercent.toFloat() }, { "%.0f%%".format(it) }) {
                    config.alertMinMarginPercent = it.toDouble()
                    Saibon.config.save()
                }
                toggle("Post Auction House flips to chat (item, price, margin, Buy now link)", { config.chatNotifyEnabled }) {
                    config.chatNotifyEnabled = it
                    Saibon.config.save()
                }
            }
        )
    }
}
