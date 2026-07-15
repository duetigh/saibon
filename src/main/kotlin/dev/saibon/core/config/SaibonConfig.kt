package dev.saibon.core.config

import dev.saibon.data.DataConfig
import dev.saibon.itemlist.ItemListConfig
import dev.saibon.market.MarketConfig
import dev.saibon.search.SearchConfig
import dev.saibon.update.UpdateConfig

/**
 * Root config document persisted to config/saibon/config.json.
 * [schemaVersion] exists from day one so later stages can migrate old files
 * instead of resetting them. Nested per-feature sections round-trip through
 * Gson's reflection the same way a flat field does; missing sections in an
 * old config file just fall back to their data class defaults, so no
 * migration code is needed for purely-additive schema bumps.
 */
data class SaibonConfig(
    var schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    var update: UpdateConfig = UpdateConfig(),
    var search: SearchConfig = SearchConfig(),
    var dataRepo: DataConfig = DataConfig(),
    var itemList: ItemListConfig = ItemListConfig(),
    var market: MarketConfig = MarketConfig()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 5
    }
}
