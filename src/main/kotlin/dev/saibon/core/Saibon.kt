package dev.saibon.core

import dev.saibon.core.config.ConfigManager
import dev.saibon.core.event.EventBus
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Saibon : ModInitializer {
    const val MOD_ID = "saibon"

    val logger = LoggerFactory.getLogger("Saibon")
    val events = EventBus()

    lateinit var config: ConfigManager
        private set

    override fun onInitialize() {
        config = ConfigManager()
        config.load()

        logger.info(
            "Saibon core initialized (config schema v{})",
            config.data.schemaVersion
        )
    }
}
