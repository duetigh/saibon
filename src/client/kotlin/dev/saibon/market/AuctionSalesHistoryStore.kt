package dev.saibon.market

import com.google.gson.Gson
import dev.saibon.core.Saibon
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk persistence for [AuctionSalesHistoryRepository]'s local sample
 * buckets — mirrors [dev.saibon.core.config.ConfigManager]'s save/load
 * pattern. Without this, every client restart threw away all local live
 * sales history and started refilling from empty; local live-refined data
 * (generally fresher/more targeted than the bundled server snapshot, see
 * [dev.saibon.data.DataRepository]) now survives across sessions.
 */
internal object AuctionSalesHistoryStore {
    private val gson = Gson()
    private val storeDir: Path = FabricLoader.getInstance().configDir.resolve("saibon/market")
    private val storeFile: Path = storeDir.resolve("sales-history.json")

    private data class StoreFile(
        val history: Map<String, List<SaleSample>> = emptyMap(),
        val signatureHistory: Map<String, List<SaleSample>> = emptyMap(),
        /** `"<modifierKind>:<modifierKey>"` -> delta samples, see `AuctionSalesHistoryRepository.modifierDeltaHistory`. Absent on files written before this field existed — Gson defaults it to empty, no migration needed. */
        val modifierDeltaHistory: Map<String, List<SaleSample>> = emptyMap()
    )

    fun load(
        history: ConcurrentHashMap<String, AuctionSalesHistoryRepository.SaleHistory>,
        signatureHistory: ConcurrentHashMap<String, AuctionSalesHistoryRepository.SaleHistory>,
        modifierDeltaHistory: ConcurrentHashMap<String, AuctionSalesHistoryRepository.SaleHistory>
    ) {
        if (!Files.exists(storeFile)) return
        runCatching {
            Files.newBufferedReader(storeFile).use { reader ->
                gson.fromJson(reader, StoreFile::class.java) ?: StoreFile()
            }
        }.onSuccess { stored ->
            apply(stored.history, history)
            apply(stored.signatureHistory, signatureHistory)
            apply(stored.modifierDeltaHistory, modifierDeltaHistory)
        }.onFailure {
            Saibon.logger.warn("Failed to load Saibon sales-history cache, starting empty", it)
        }
    }

    private fun apply(
        stored: Map<String, List<SaleSample>>,
        target: ConcurrentHashMap<String, AuctionSalesHistoryRepository.SaleHistory>
    ) {
        stored.forEach { (key, samples) ->
            val entry = AuctionSalesHistoryRepository.SaleHistory()
            entry.samples.addAll(samples)
            target[key] = entry
        }
    }

    fun save(
        history: ConcurrentHashMap<String, AuctionSalesHistoryRepository.SaleHistory>,
        signatureHistory: ConcurrentHashMap<String, AuctionSalesHistoryRepository.SaleHistory>,
        modifierDeltaHistory: ConcurrentHashMap<String, AuctionSalesHistoryRepository.SaleHistory>
    ) {
        runCatching {
            Files.createDirectories(storeDir)
            val snapshot = StoreFile(
                history = history.mapValues { (_, v) -> synchronized(v.samples) { v.samples.toList() } },
                signatureHistory = signatureHistory.mapValues { (_, v) -> synchronized(v.samples) { v.samples.toList() } },
                modifierDeltaHistory = modifierDeltaHistory.mapValues { (_, v) -> synchronized(v.samples) { v.samples.toList() } }
            )
            Files.newBufferedWriter(storeFile).use { writer -> gson.toJson(snapshot, writer) }
        }.onFailure {
            Saibon.logger.warn("Failed to persist Saibon sales-history cache", it)
        }
    }
}
