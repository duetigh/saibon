package dev.saibon.market

import com.google.gson.Gson
import dev.saibon.core.Saibon
import dev.saibon.market.model.AuctionPrice
import dev.saibon.market.model.AuctionsPageResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lowest-BIN Auction House price feed (NEU_FEATURE_PARITY.md #2's "heavier"
 * item — needs a full auction-page sweep, not a single endpoint hit like
 * [MarketPriceRepository]'s Bazaar feed). Sweeps every page of Hypixel's
 * public, keyless `/v2/skyblock/auctions` endpoint, decodes each active BIN
 * listing's item id via [AuctionItemDecoder], and keeps the minimum
 * `starting_bid` seen per item id. In-memory only, same shape as
 * [MarketPriceRepository]. Default off (see [dev.saibon.market.MarketConfig])
 * since a full sweep is tens of megabytes of JSON — opt-in, long interval.
 */
object AuctionPriceRepository {
    private const val AUCTIONS_URL = "https://api.hypixel.net/v2/skyblock/auctions"
    private const val PAGE_BATCH_SIZE = 8

    private val gson = Gson()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Saibon-AuctionPriceRepository").apply { isDaemon = true }
    }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()

    private val initialized = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)
    private var scheduledRefresh: java.util.concurrent.ScheduledFuture<*>? = null
    private var lowestBins: Map<String, AuctionPrice> = emptyMap()

    var lastRefreshed: Instant? = null
        private set
    val isRefreshing: Boolean get() = refreshing.get()

    private class Accumulator {
        var lowestBin: Long = Long.MAX_VALUE
        var count: Int = 0
    }

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        rescheduleRefresh()
    }

    /** Cancels and re-schedules the periodic sweep — called after the interval/toggle setting changes. */
    fun rescheduleRefresh() {
        scheduledRefresh?.cancel(false)
        val config = Saibon.config.data.market
        if (!config.ahAutoRefresh) return

        val interval = config.ahRefreshIntervalSeconds.toLong()
        refreshNow()
        scheduledRefresh = executor.scheduleWithFixedDelay({ refreshNow() }, interval, interval, TimeUnit.SECONDS)
    }

    fun lowestBin(itemId: String): AuctionPrice? = lowestBins[itemId.uppercase()]

    fun refreshNow() {
        if (!Saibon.config.data.market.ahAutoRefresh) return
        if (!refreshing.compareAndSet(false, true)) return

        val accumulated = ConcurrentHashMap<String, Accumulator>()
        fetchPage(0)
            .thenCompose { first ->
                applyPage(first, accumulated)
                if (first.totalPages <= 1) {
                    CompletableFuture.completedFuture(Unit)
                } else {
                    fetchRemainingPages(first.totalPages, accumulated)
                }
            }
            .thenAccept { publish(accumulated) }
            .exceptionally { throwable ->
                Saibon.logger.warn("Saibon auction price sweep failed", throwable)
                refreshing.set(false)
                null
            }
    }

    private fun fetchRemainingPages(totalPages: Int, accumulated: ConcurrentHashMap<String, Accumulator>): CompletableFuture<Unit> {
        var chain = CompletableFuture.completedFuture(Unit)
        for (batch in (1 until totalPages).chunked(PAGE_BATCH_SIZE)) {
            chain = chain.thenCompose {
                val futures = batch.map { page ->
                    fetchPage(page)
                        .thenAccept { applyPage(it, accumulated) }
                        .exceptionally { throwable ->
                            Saibon.logger.warn("Saibon auction page {} fetch failed, skipping", page, throwable)
                            null
                        }
                }
                CompletableFuture.allOf(*futures.toTypedArray()).thenApply { }
            }
        }
        return chain
    }

    private fun fetchPage(page: Int): CompletableFuture<AuctionsPageResponse> {
        val request = HttpRequest.newBuilder(URI.create("$AUCTIONS_URL?page=$page")).GET().build()
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> gson.fromJson(response.body(), AuctionsPageResponse::class.java) }
    }

    private fun applyPage(page: AuctionsPageResponse, accumulated: ConcurrentHashMap<String, Accumulator>) {
        for (auction in page.auctions) {
            if (!auction.bin || auction.claimed) continue
            val itemId = AuctionItemDecoder.extraAttributesId(auction.item_bytes)?.uppercase() ?: continue
            val acc = accumulated.computeIfAbsent(itemId) { Accumulator() }
            synchronized(acc) {
                acc.count++
                if (auction.starting_bid < acc.lowestBin) acc.lowestBin = auction.starting_bid
            }
        }
    }

    private fun publish(accumulated: ConcurrentHashMap<String, Accumulator>) {
        lowestBins = accumulated.mapValues { (_, acc) -> AuctionPrice(acc.lowestBin, acc.count) }
        lastRefreshed = Instant.now()
        refreshing.set(false)
        Saibon.logger.info("Saibon auction price sweep complete: {} items", lowestBins.size)
    }
}
