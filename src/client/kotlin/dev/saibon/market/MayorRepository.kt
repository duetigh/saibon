package dev.saibon.market

import com.google.gson.Gson
import dev.saibon.core.Saibon
import dev.saibon.market.model.ElectionResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Current SkyBlock mayor, polled from Hypixel's public, keyless
 * `/v2/resources/skyblock/election` endpoint — same "public API for data the
 * player is actively affected by" allowance PLAN.md already covers for
 * [MarketPriceRepository]. Unlike Bazaar prices, a mayor term runs for real
 * hours, not minutes, so this polls on a much longer interval. The only
 * current consumer is [AuctionHouseTax.netOfTax]'s Derpy-mayor claim-tax
 * quadrupling ([isDerpyActive]) — detection is name-based
 * (`mayor.name == "Derpy"`) rather than keyed off `mayor.key`, since the
 * current mayor at the time this was built wasn't Derpy, so his specific
 * `key`/shape when active couldn't be verified live (see
 * [dev.saibon.market.model.ElectionResponse]'s own doc comment). In-memory
 * only, same as [MarketPriceRepository].
 */
object MayorRepository {
    private const val ELECTION_URL = "https://api.hypixel.net/v2/resources/skyblock/election"

    /** A SkyBlock mayor term runs ~2 real hours, so this is responsive without polling meaningfully harder than the "changes a few times a month" cadence actually requires. */
    private const val POLL_INTERVAL_SECONDS = 1800L

    private val gson = Gson()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Saibon-MayorRepository").apply { isDaemon = true }
    }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var currentMayorName: String? = null

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        refreshNow()
        executor.scheduleWithFixedDelay({ refreshNow() }, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    /** True iff the currently elected mayor is Derpy — see [AuctionHouseTax.netOfTax]'s `derpyActive` parameter. */
    fun isDerpyActive(): Boolean = currentMayorName?.equals("Derpy", ignoreCase = true) == true

    fun refreshNow() {
        val request = HttpRequest.newBuilder(URI.create(ELECTION_URL)).GET().build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response -> onResponse(response.body()) }
            .exceptionally { throwable ->
                Saibon.logger.warn("Saibon mayor/election fetch failed", throwable)
                null
            }
    }

    private fun onResponse(body: String) {
        runCatching { gson.fromJson(body, ElectionResponse::class.java) }
            .onSuccess { response ->
                if (response != null && response.success) currentMayorName = response.mayor?.name
            }
            .onFailure {
                Saibon.logger.warn("Saibon mayor/election response failed to parse, discarding", it)
            }
    }
}
