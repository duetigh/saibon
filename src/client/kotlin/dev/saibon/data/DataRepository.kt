package dev.saibon.data

import com.google.gson.Gson
import dev.saibon.core.Saibon
import dev.saibon.data.model.DataCacheMeta
import dev.saibon.data.model.DataManifest
import dev.saibon.data.model.DatasetEntry
import dev.saibon.data.model.Recipe
import dev.saibon.data.model.SkyblockItem
import net.fabricmc.loader.api.FabricLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Saibon's "data repo" (PLAN.md `saibon-data`, Stage 4): a small versioned
 * JSON manifest (`data/index.json` in the Saibon GitHub repo, fetched via
 * `raw.githubusercontent.com` off the `master` branch) lists named datasets,
 * each with a version number and a sha256. This is deliberately decoupled
 * from the jar-update cycle (`UpdateChecker`) so game data can refresh
 * without a restart.
 *
 * Adding a new dataset later (npc prices, dungeon answer keys, ...) is just
 * an entry in `index.json` plus a branch in [applyDataset] — no changes here.
 */
object DataRepository {
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/JamesWLyon/saibon/master/data/index.json"

    /** Initial fetch is delayed rather than run at mod-init time, per PLAN.md's
     * "defer non-critical startup work (e.g. the data-repo fetch) until after
     * the main menu renders" performance rule. */
    private val INITIAL_DELAY_SECONDS = 3L

    private val gson = Gson()
    private val cacheDir: Path = FabricLoader.getInstance().configDir.resolve("saibon/data")
    private val metaFile: Path = cacheDir.resolve("cache-meta.json")

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Saibon-DataRepository").apply { isDaemon = true }
    }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()

    private val initialized = AtomicBoolean(false)
    private var cachedVersions: Map<String, Int> = emptyMap()
    private var items: Map<String, SkyblockItem> = emptyMap()
    private var recipes: Map<String, List<Recipe>> = emptyMap()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return

        loadFromDisk()

        executor.schedule({ refreshNow() }, INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
        val config = Saibon.config.data.dataRepo
        if (config.autoRefresh) {
            executor.scheduleWithFixedDelay(
                { refreshNow() },
                config.refreshIntervalMinutes.toLong(),
                config.refreshIntervalMinutes.toLong(),
                TimeUnit.MINUTES
            )
        }
    }

    fun item(id: String): SkyblockItem? = items[id.uppercase()]

    fun allItems(): Collection<SkyblockItem> = items.values

    /** Every recipe that crafts [id] — some items (e.g. multiple forge stages) have more than one. */
    fun recipesFor(id: String): List<Recipe> = recipes[id.uppercase()] ?: emptyList()

    /** Every recipe in the repo, for "used in" reverse lookups. */
    fun allRecipes(): Collection<Recipe> = recipes.values.flatten()

    fun refreshNow() {
        if (!Saibon.config.data.dataRepo.autoRefresh) return

        val request = HttpRequest.newBuilder(URI.create(MANIFEST_URL)).GET().build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response -> onManifest(gson.fromJson(response.body(), DataManifest::class.java)) }
            .exceptionally { throwable ->
                Saibon.logger.warn("Saibon data manifest fetch failed", throwable)
                null
            }
    }

    private fun onManifest(manifest: DataManifest) {
        manifest.datasets.forEach { (name, entry) ->
            if (entry.version > (cachedVersions[name] ?: 0)) {
                downloadDataset(name, entry)
            }
        }
    }

    private fun downloadDataset(name: String, entry: DatasetEntry) {
        val request = HttpRequest.newBuilder(URI.create(entry.url)).GET().build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response -> onDataset(name, entry, response.body()) }
            .exceptionally { throwable ->
                Saibon.logger.warn("Saibon dataset '{}' download failed", name, throwable)
                null
            }
    }

    private fun onDataset(name: String, entry: DatasetEntry, body: String) {
        if (entry.sha256.isNotBlank() && !sha256Of(body).equals(entry.sha256, ignoreCase = true)) {
            Saibon.logger.warn("Saibon dataset '{}' failed checksum verification, discarding", name)
            return
        }

        runCatching { applyDataset(name, body) }
            .onFailure {
                Saibon.logger.warn("Saibon dataset '{}' failed to parse, discarding", name, it)
                return
            }

        cachedVersions = cachedVersions + (name to entry.version)
        persistToDisk(name, body)
    }

    private fun applyDataset(name: String, body: String) {
        when (name) {
            "items" -> items = gson.fromJson(body, Array<SkyblockItem>::class.java).associateBy { it.id.uppercase() }
            "recipes" -> recipes = gson.fromJson(body, Array<Recipe>::class.java).groupBy { it.itemId.uppercase() }
            else -> Saibon.logger.warn("Saibon data manifest referenced unknown dataset '{}', ignoring", name)
        }
    }

    private fun loadFromDisk() {
        if (!Files.exists(metaFile)) return

        runCatching {
            val meta = Files.newBufferedReader(metaFile).use { reader ->
                gson.fromJson(reader, DataCacheMeta::class.java) ?: DataCacheMeta()
            }
            cachedVersions = meta.versions
            cachedVersions.keys.forEach { name ->
                val file = cacheDir.resolve("$name.json")
                if (Files.exists(file)) {
                    applyDataset(name, Files.readString(file))
                }
            }
        }.onFailure {
            Saibon.logger.warn("Failed to load cached Saibon data, will re-fetch", it)
        }
    }

    private fun persistToDisk(name: String, body: String) {
        runCatching {
            Files.createDirectories(cacheDir)
            Files.writeString(cacheDir.resolve("$name.json"), body)
            Files.newBufferedWriter(metaFile).use { writer ->
                gson.toJson(DataCacheMeta(cachedVersions), writer)
            }
        }.onFailure {
            Saibon.logger.warn("Failed to persist Saibon dataset '{}' to disk", name, it)
        }
    }

    private fun sha256Of(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
