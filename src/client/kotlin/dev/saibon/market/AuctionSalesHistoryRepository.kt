package dev.saibon.market

import com.google.gson.Gson
import dev.saibon.core.Saibon
import dev.saibon.market.model.AuctionsEndedResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local reference-sale-price engine for the `/saibonah` flip finder
 * ([dev.saibon.market.ui.AuctionFlipScreen]). Polls Hypixel's public,
 * keyless `/v2/skyblock/auctions_ended` endpoint (a rolling ~1hr window of
 * recently sold auctions — shape confirmed live, see
 * [dev.saibon.market.model.AuctionsEndedResponse]) well under that window,
 * decodes each sale's item id via the existing [AuctionItemDecoder], and
 * keeps a bounded per-item ring buffer of recent sale prices. Exposes a
 * rolling **median** (robust to one-off troll/outlier listings) plus a
 * sample count, so callers can judge confidence rather than trusting a
 * single sale. This is a statistical item-id-level approximation — it does
 * not bucket by rarity/stars/enchants/reforge, unlike a per-listing
 * appraisal. In-memory only, same shape as [AuctionPriceRepository].
 */
object AuctionSalesHistoryRepository {
    private const val AUCTIONS_ENDED_URL = "https://api.hypixel.net/v2/skyblock/auctions_ended"
    private const val MAX_SEEN_AUCTION_IDS = 20_000

    private val gson = Gson()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Saibon-AuctionSalesHistoryRepository").apply { isDaemon = true }
    }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()

    private val initialized = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)
    private var scheduledRefresh: java.util.concurrent.ScheduledFuture<*>? = null

    /** Bounded FIFO de-dupe set: `auctions_ended`'s polling window overlaps between consecutive fetches. */
    private val seenAuctionIds: MutableSet<String> = Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>(1024, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
                size > MAX_SEEN_AUCTION_IDS
        }
    )

    private class SaleHistory {
        val prices = ArrayDeque<Long>()
    }

    private val history = ConcurrentHashMap<String, SaleHistory>()

    var lastRefreshed: Instant? = null
        private set
    val isRefreshing: Boolean get() = refreshing.get()

    data class SaleReference(val median: Double, val sampleCount: Int)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        rescheduleRefresh()
    }

    /** Cancels and re-schedules the periodic poll — called after the interval/toggle setting changes. */
    fun rescheduleRefresh() {
        scheduledRefresh?.cancel(false)
        val config = Saibon.config.data.market
        if (!config.salesHistoryAutoRefresh) return

        val interval = config.salesHistoryRefreshIntervalSeconds.toLong()
        refreshNow()
        scheduledRefresh = executor.scheduleWithFixedDelay({ refreshNow() }, interval, interval, TimeUnit.SECONDS)
    }

    fun refreshNow() {
        if (!Saibon.config.data.market.salesHistoryAutoRefresh) return
        if (!refreshing.compareAndSet(false, true)) return

        val request = HttpRequest.newBuilder(URI.create(AUCTIONS_ENDED_URL)).GET().build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response -> onResponse(response.body()) }
            .exceptionally { throwable ->
                Saibon.logger.warn("Saibon auction sales-history fetch failed", throwable)
                refreshing.set(false)
                null
            }
    }

    /** Rolling median + sample count for [itemId], or null if no recent sale has been observed. */
    fun saleReference(itemId: String): SaleReference? {
        val prices = history[itemId.uppercase()]?.prices ?: return null
        val snapshot = synchronized(prices) { prices.sorted() }
        if (snapshot.isEmpty()) return null
        val mid = snapshot.size / 2
        val median = if (snapshot.size % 2 == 0) (snapshot[mid - 1] + snapshot[mid]) / 2.0 else snapshot[mid].toDouble()
        return SaleReference(median, snapshot.size)
    }

    private fun onResponse(body: String) {
        runCatching { gson.fromJson(body, AuctionsEndedResponse::class.java) }
            .onSuccess { response ->
                if (response != null && response.success) applySales(response)
            }
            .onFailure {
                Saibon.logger.warn("Saibon auction sales-history response failed to parse, discarding", it)
            }
        lastRefreshed = Instant.now()
        refreshing.set(false)
    }

    private fun applySales(response: AuctionsEndedResponse) {
        val maxSamples = Saibon.config.data.market.salesHistoryMaxSamplesPerItem
        var newSamples = 0
        for (sale in response.auctions) {
            synchronized(seenAuctionIds) {
                if (!seenAuctionIds.add(sale.auction_id)) return@synchronized
            }
            val itemId = AuctionItemDecoder.extraAttributesId(sale.item_bytes)?.uppercase() ?: continue
            if (sale.price <= 0) continue

            val entry = history.computeIfAbsent(itemId) { SaleHistory() }
            synchronized(entry.prices) {
                entry.prices.addLast(sale.price)
                while (entry.prices.size > maxSamples) entry.prices.removeFirst()
            }
            newSamples++
        }
        Saibon.logger.info("Saibon sales-history poll: {} new sales, {} items tracked", newSamples, history.size)
    }
}
