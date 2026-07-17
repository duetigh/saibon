package dev.saibon.market

import com.google.gson.Gson
import dev.saibon.core.Saibon
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * UUID -> current username lookups for Auction House sellers, via Mojang's
 * session-server profile endpoint. This is the one deliberate exception to
 * Saibon's "Hypixel API + Saibon's GitHub repo only" outbound-call rule
 * (PLAN.md non-negotiables): Hypixel's keyless `/v2/skyblock/auctions` feed
 * only exposes the seller's UUID, never a name, and resolving it through
 * Hypixel itself needs a per-player endpoint that requires an API key this
 * mod doesn't ask users for. Read-only, in-memory cache, never sends
 * anything about the local player — only looks up UUIDs already seen in
 * public auction listings.
 */
object PlayerNameResolver {
    private data class ProfileResponse(val id: String = "", val name: String = "")

    private val gson = Gson()
    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "Saibon-PlayerNameResolver").apply { isDaemon = true }
    }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()
    private val cache = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<String?>>()

    /** Resolves [uuid] (dashed or undashed) to its current username, from cache if already known. */
    fun resolve(uuid: String): CompletableFuture<String?> {
        val normalized = uuid.replace("-", "")
        cache[normalized]?.let { return CompletableFuture.completedFuture(it) }
        return inFlight.computeIfAbsent(normalized) { fetch(normalized) }
    }

    private fun fetch(uuid: String): CompletableFuture<String?> {
        val request = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$uuid")).GET().build()
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() != 200) return@thenApply null
                val profile = gson.fromJson(response.body(), ProfileResponse::class.java)
                profile.name.takeIf { it.isNotEmpty() }?.also { cache[uuid] = it }
            }
            .exceptionally { throwable ->
                Saibon.logger.warn("Saibon player name lookup failed for {}", uuid, throwable)
                null
            }
            .whenComplete { _, _ -> inFlight.remove(uuid) }
    }
}
