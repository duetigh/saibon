package dev.saibon.market

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Auction House Overlay" settings section. Called once from `SaibonClient.onInitializeClient()`. */
object AuctionMenuSettings {
    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.DATA, "Auction House Overlay") {
                val config = Saibon.config.data.market

                toggle("Show browse-all-items panel on the real AH menu", config.ahOverlayPanelEnabled) {
                    config.ahOverlayPanelEnabled = it
                    Saibon.config.save()
                }
                label("Requires \"Auto-refresh lowest-BIN prices\" in Auction House Prices settings to have data.")

                toggle("Relayout the current AH page into a sorted NEU-style grid", config.ahRelayoutEnabled) {
                    config.ahRelayoutEnabled = it
                    Saibon.config.save()
                }
                label("Clicking a tile issues a real click on the real listing it represents. Off by default and unverified against a live server — test on a cheap listing first.")
                toggle("Require Confirm before a relayout click fires", config.ahRelayoutConfirmRequired) {
                    config.ahRelayoutConfirmRequired = it
                    Saibon.config.save()
                }
            }
        )
    }
}
