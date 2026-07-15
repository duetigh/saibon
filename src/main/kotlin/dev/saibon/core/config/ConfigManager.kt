package dev.saibon.core.config

import com.google.gson.GsonBuilder
import dev.saibon.core.Saibon
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads/saves [SaibonConfig] as pretty-printed JSON under config/saibon/.
 * No feature config exists yet; this only proves the round-trip mechanism.
 */
class ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir: Path = FabricLoader.getInstance().configDir.resolve("saibon")
    private val configFile: Path = configDir.resolve("config.json")

    var data: SaibonConfig = SaibonConfig()
        private set

    fun load() {
        if (!Files.exists(configFile)) {
            save()
            return
        }

        runCatching {
            Files.newBufferedReader(configFile).use { reader ->
                gson.fromJson(reader, SaibonConfig::class.java) ?: SaibonConfig()
            }
        }.onSuccess {
            data = it
        }.onFailure {
            Saibon.logger.warn("Failed to read saibon config, resetting to defaults", it)
            data = SaibonConfig()
            save()
        }
    }

    fun save() {
        Files.createDirectories(configDir)
        Files.newBufferedWriter(configFile).use { writer ->
            gson.toJson(data, writer)
        }
    }
}
