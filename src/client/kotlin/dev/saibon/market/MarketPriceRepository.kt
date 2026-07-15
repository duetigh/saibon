package dev.saibon.market

import com.google.gson.Gson
import dev.saibon.core.Saibon
import dev.saibon.market.model.BazaarPrice
import dev.saibon.market.model.BazaarResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live Bazaar price feed (NEU_FEATURE_PARITY.md #2). Unlike [dev.saibon.data.DataRepository]'s
 * versioned datasets (slow-changing item/recipe metadata, re-checked every few hours), Bazaar
 * prices change roughly every minute, so this polls Hypixel's public, keyless Bazaar endpoint
 * directly on its own short interval instead of going through the data-repo manifest pipeline —
 * per PLAN.md, calling Hypixel's public API for data the player is actively looking at is allowed.
 * In-memory only: nothing here is persisted to disk, prices are just refetched on next launch.
 */
object MarketPriceRepository {
    private const val BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar"

    private val gson = Gson()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Saibon-MarketPriceRepository").apply { isDaemon = true }
    }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()

    private val initialized = AtomicBoolean(false)
    private var scheduledRefresh: java.util.concurrent.ScheduledFuture<*>? = null
    private var bazaarPrices: Map<String, BazaarPrice> = emptyMap()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return

        refreshNow()
        rescheduleRefresh()
    }

    /** Cancels and re-schedules the periodic refresh — called after the interval setting changes. */
    fun rescheduleRefresh() {
        scheduledRefresh?.cancel(false)
        val config = Saibon.config.data.market
        if (!config.autoRefresh) return

        val interval = config.refreshIntervalSeconds.toLong()
        scheduledRefresh = executor.scheduleWithFixedDelay({ refreshNow() }, interval, interval, TimeUnit.SECONDS)
    }

    fun bazaarPrice(itemId: String): BazaarPrice? = bazaarPrices[itemId.uppercase()]

    fun refreshNow() {
        if (!Saibon.config.data.market.autoRefresh) return

        val request = HttpRequest.newBuilder(URI.create(BAZAAR_URL)).GET().build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response -> onResponse(response.body()) }
            .exceptionally { throwable ->
                Saibon.logger.warn("Saibon bazaar price fetch failed", throwable)
                null
            }
    }

    private fun onResponse(body: String) {
        runCatching { gson.fromJson(body, BazaarResponse::class.java) }
            .onSuccess { response ->
                if (response == null || !response.success) return@onSuccess
                bazaarPrices = response.products.mapKeys { it.key.uppercase() }
                    .mapValues { it.value.quick_status }
            }
            .onFailure {
                Saibon.logger.warn("Saibon bazaar price response failed to parse, discarding", it)
            }
    }
}
