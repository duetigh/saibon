package dev.saibon.market

import com.google.gson.Gson
import dev.saibon.core.Saibon
import dev.saibon.data.DataRepository
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
 * Local reference-sale-price engine for the Auction House flip finder
 * ([dev.saibon.market.flip.AuctionFlipFinder], part of `/saibonflips`). Polls
 * Hypixel's public,
 * keyless `/v2/skyblock/auctions_ended` endpoint (a rolling ~1hr window of
 * recently sold auctions — shape confirmed live, see
 * [dev.saibon.market.model.AuctionsEndedResponse]) well under that window,
 * decodes each sale's item id via the existing [AuctionItemDecoder], and
 * keeps a bounded per-item ring buffer of timestamped recent sale prices.
 * [saleReference] runs those through [FairPriceCalculator] for a weighted
 * fair price, a real 0-100 confidence score, and a sales/week volume figure
 * — not just a flat median + raw count. Buckets are seeded from the
 * server-published `fair_prices` dataset ([dev.saibon.data.DataRepository])
 * so a fresh install has a usable reference price before its own local poll
 * has gathered anything; once a bucket has enough local samples of its own,
 * local data wins (see [saleReference]). Persisted to disk via
 * [AuctionSalesHistoryStore] so a client restart doesn't discard local
 * live-refined history.
 */
object AuctionSalesHistoryRepository {
    private const val AUCTIONS_ENDED_URL = "https://api.hypixel.net/v2/skyblock/auctions_ended"
    private const val MAX_SEEN_AUCTION_IDS = 20_000

    /** Local samples for a SKU are trusted over the bundled server snapshot once a bucket reaches this many. Below it, the snapshot (built from far more real time than any single client can observe on day one) is more reliable. */
    private const val LOCAL_TRUST_THRESHOLD = 5

    /** Bounded by both count and age (see [record]) — large enough to derive a real sales/week figure, not just a short price window. */
    private const val MAX_SAMPLE_AGE_MILLIS = 14L * 24 * 3_600_000

    private val gson = Gson()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Saibon-AuctionSalesHistoryRepository").apply { isDaemon = true }
    }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()

    private val initialized = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)
    private var scheduledRefresh: java.util.concurrent.ScheduledFuture<*>? = null
    private var scheduledPersist: java.util.concurrent.ScheduledFuture<*>? = null

    /** Bounded FIFO de-dupe set: `auctions_ended`'s polling window overlaps between consecutive fetches. */
    private val seenAuctionIds: MutableSet<String> = Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>(1024, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
                size > MAX_SEEN_AUCTION_IDS
        }
    )

    internal class SaleHistory {
        val samples = ArrayDeque<SaleSample>()
    }

    /** Item-id-only bucket — always populated, the pre-existing behavior every caller that omits a modifier signature still gets. */
    private val history = ConcurrentHashMap<String, SaleHistory>()

    /** `"<itemId>|<modifierSignature>"` bucket — only populated for sales that actually carry a non-empty signature (enchants/hot-potato/recomb/stars/reforge). Lets [saleReference] give a modifier-aware price for common upgraded gear while item-id-only items keep working exactly as before. */
    private val signatureHistory = ConcurrentHashMap<String, SaleHistory>()

    var lastRefreshed: Instant? = null
        private set
    val isRefreshing: Boolean get() = refreshing.get()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        AuctionSalesHistoryStore.load(history, signatureHistory)
        rescheduleRefresh()
        scheduledPersist = executor.scheduleWithFixedDelay(
            { AuctionSalesHistoryStore.save(history, signatureHistory) }, 5, 5, TimeUnit.MINUTES
        )
        Runtime.getRuntime().addShutdownHook(Thread { AuctionSalesHistoryStore.save(history, signatureHistory) })
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

    /**
     * Fair-price reference for [itemId] (see [FairPriceCalculator]), or null
     * if neither local history nor the bundled server snapshot has anything
     * for it. When [modifierSignature] is non-empty, prefers the exact
     * item+modifier bucket once it has at least
     * `FlipConfig.modifierMatchMinSamples` *local* samples (the spec's "fast
     * dictionary lookup" strategy), then the item-id-only local bucket once
     * it clears [LOCAL_TRUST_THRESHOLD], then falls back to
     * [dev.saibon.data.DataRepository]'s `fair_prices` snapshot for either
     * key — so a fresh install still gets a real reference price on day one
     * instead of nothing while local history is thin, and a rare enchant
     * combo still gets *a* reference price instead of none.
     */
    fun saleReference(itemId: String, modifierSignature: String = ""): FairPriceResult? {
        val id = itemId.uppercase()
        if (modifierSignature.isNotEmpty()) {
            val signatureKey = "$id|$modifierSignature"
            val exact = fairPriceOf(signatureHistory[signatureKey])
            val minSamples = Saibon.config.data.flip.modifierMatchMinSamples
            if (exact != null && exact.sampleCount >= minSamples) return exact
            DataRepository.fairPriceSnapshot(signatureKey)?.let { return it }
        }
        val local = fairPriceOf(history[id])
        if (local != null && local.sampleCount >= LOCAL_TRUST_THRESHOLD) return local
        return DataRepository.fairPriceSnapshot(id) ?: local
    }

    /** Every observed `"<itemId>|<modifierSignature>"` bucket's current fair price, for [dev.saibon.market.flip.AuctionFlipFinder] to cross-reference against `AuctionPriceRepository`'s signature lowest-bin buckets. Local-only — the bundled snapshot's signature entries are consulted per-key via [saleReference], not enumerated here. */
    fun allSignatureReferences(): Map<String, FairPriceResult> =
        signatureHistory.keys.mapNotNull { key -> fairPriceOf(signatureHistory[key])?.let { key to it } }.toMap()

    private fun fairPriceOf(sales: SaleHistory?): FairPriceResult? {
        val samples = sales?.samples ?: return null
        val snapshot = synchronized(samples) { samples.toList() }
        return FairPriceCalculator.compute(snapshot)
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
            val decoded = AuctionItemDecoder.decode(sale.item_bytes) ?: continue
            if (sale.price <= 0) continue
            val itemId = decoded.itemId.uppercase()
            val timestampMillis = if (sale.timestamp > 0) sale.timestamp else System.currentTimeMillis()

            record(history, itemId, sale.price, timestampMillis, maxSamples)
            if (decoded.modifierSignature.isNotEmpty()) {
                record(signatureHistory, "$itemId|${decoded.modifierSignature}", sale.price, timestampMillis, maxSamples)
            }
            newSamples++
        }
        Saibon.logger.info("Saibon sales-history poll: {} new sales, {} items tracked", newSamples, history.size)
    }

    private fun record(bucket: ConcurrentHashMap<String, SaleHistory>, key: String, price: Long, timestampMillis: Long, maxSamples: Int) {
        val entry = bucket.computeIfAbsent(key) { SaleHistory() }
        synchronized(entry.samples) {
            entry.samples.addLast(SaleSample(price, timestampMillis))
            val cutoff = System.currentTimeMillis() - MAX_SAMPLE_AGE_MILLIS
            while (entry.samples.isNotEmpty() && entry.samples.first().timestampMillis < cutoff) entry.samples.removeFirst()
            while (entry.samples.size > maxSamples) entry.samples.removeFirst()
        }
    }
}
