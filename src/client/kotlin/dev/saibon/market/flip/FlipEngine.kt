package dev.saibon.market.flip

import dev.saibon.core.Saibon
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background scheduler running every enabled [FlipFinder] on a shared
 * interval (spec §4.4 — all four current finders are pure math over
 * already-cached repository data, not new I/O per scan, so a single shared
 * interval is enough for v1 rather than one scheduler per finder), publishing
 * results to [latestCandidates] for `FlipScreen`/`/saibonflips` to render and
 * notifying [onNewCandidate] listeners (the flip-alert HUD toast) only for
 * candidates that weren't present in the previous scan.
 */
object FlipEngine {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Saibon-FlipEngine").apply { isDaemon = true }
    }
    private val initialized = AtomicBoolean(false)
    private var scheduledScan: ScheduledFuture<*>? = null

    private val finders: List<Pair<FlipFinder, () -> Boolean>> = listOf(
        AuctionFlipFinder to { Saibon.config.data.flip.auctionFlipsEnabled },
        BazaarMarginFlipFinder to { Saibon.config.data.flip.bazaarMarginFlipsEnabled },
        NpcFlipFinder to { Saibon.config.data.flip.npcFlipsEnabled },
        CraftFlipFinder to { Saibon.config.data.flip.craftFlipsEnabled }
    )

    @Volatile
    private var latest: List<FlipCandidate> = emptyList()

    @Volatile
    var lastScanned: Instant? = null
        private set

    private val newCandidateListeners = CopyOnWriteArrayList<(FlipCandidate) -> Unit>()

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        rescheduleScan()
    }

    /** Cancels and re-schedules the periodic scan — call after `FlipConfig.scanIntervalSeconds` changes. */
    fun rescheduleScan() {
        scheduledScan?.cancel(false)
        val interval = Saibon.config.data.flip.scanIntervalSeconds.toLong().coerceAtLeast(5)
        scheduledScan = executor.scheduleWithFixedDelay({ scanNow() }, 0, interval, TimeUnit.SECONDS)
    }

    fun scanNow() {
        val results = finders
            .filter { (_, enabled) -> enabled() }
            .flatMap { (finder, _) ->
                runCatching { finder.scan() }.getOrElse {
                    Saibon.logger.warn("Saibon flip finder '{}' failed to scan, skipping this cycle", finder.name, it)
                    emptyList()
                }
            }
            .sortedByDescending { it.estimatedProfit }

        val previousKeys = latest.mapTo(mutableSetOf()) { candidateKey(it) }
        val fresh = results.filter { candidateKey(it) !in previousKeys }

        latest = results
        lastScanned = Instant.now()
        fresh.forEach { candidate -> newCandidateListeners.forEach { it(candidate) } }
    }

    fun latestCandidates(): List<FlipCandidate> = latest

    /** Registers a listener called once per genuinely new candidate (not present in the prior scan) — used by the flip-alert HUD toast. */
    fun onNewCandidate(listener: (FlipCandidate) -> Unit) {
        newCandidateListeners += listener
    }

    private fun candidateKey(candidate: FlipCandidate) =
        "${candidate.sourceFinder}|${candidate.item.id}|${candidate.modifierSignature}"
}
