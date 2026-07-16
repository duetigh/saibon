package dev.saibon.market

import dev.saibon.core.Saibon
import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.settings.SettingsSection

/** Registers the "Bazaar Overlay" settings section. Called once from `SaibonClient.onInitializeClient()`. */
object BazaarMenuSettings {
    fun register() {
        SettingsRegistry.register(
            SettingsSection(SaibonCategory.DATA, "Bazaar Overlay") {
                val config = Saibon.config.data.market

                toggle("Show category/flip browse panel on the real Bazaar menu", config.bazaarOverlayEnabled) {
                    config.bazaarOverlayEnabled = it
                    Saibon.config.save()
                }
                slider(
                    "Buy Order -> NPC Sell min margin",
                    0f, 50f, config.buyOrderToNpcMinMarginPercent.toFloat(),
                    { "${it.toInt()}%" }
                ) {
                    config.buyOrderToNpcMinMarginPercent = it.toDouble()
                    Saibon.config.save()
                }
                slider(
                    "Craft flip min margin",
                    0f, 100f, config.craftFlipMinMarginPercent.toFloat(),
                    { "${it.toInt()}%" }
                ) {
                    config.craftFlipMinMarginPercent = it.toDouble()
                    Saibon.config.save()
                }

                label("Buy/sell/order/offer buttons below click the real Bazaar menu on your behalf.")
                toggle("Dry run (log what would be clicked, click nothing)", config.bazaarActionDryRun) {
                    config.bazaarActionDryRun = it
                    Saibon.config.save()
                }
                label("Keep dry run ON until you've watched a full pass against your real Bazaar session.")
                toggle("Confirm before the first click of every action", config.bazaarActionConfirmRequired) {
                    config.bazaarActionConfirmRequired = it
                    Saibon.config.save()
                }
                slider(
                    "Action timeout",
                    1f, 15f, (config.bazaarActionTimeoutMs / 1000f),
                    { "${it.toInt()}s" }
                ) {
                    config.bazaarActionTimeoutMs = (it * 1000).toInt()
                    Saibon.config.save()
                }
            }
        )
    }
}
