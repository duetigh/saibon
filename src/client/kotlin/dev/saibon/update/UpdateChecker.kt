package dev.saibon.update

import com.google.gson.Gson
import dev.saibon.client.chat.SaibonChat
import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors

/**
 * Fetches `version.json` off-thread and, if it names a newer release than
 * what's running, hops back to the main thread (per [PLAN.md]'s "no blocking
 * I/O on render/tick thread" rule) to post the update chat prompt. Triggered
 * off [ClientPlayConnectionEvents.JOIN] (world/server join) rather than mod
 * init or the title screen, so a [net.minecraft.client.player.LocalPlayer]
 * always exists to receive the "Mod loaded" / update chat lines.
 */
object UpdateChecker {
    private const val MANIFEST_URL = "https://github.com/JamesWLyon/saibon/releases/latest/download/version.json"
    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Saibon-UpdateChecker").apply { isDaemon = true }
    }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()

    var latestManifest: VersionManifest? = null
        private set

    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            Minecraft.getInstance().player?.sendSystemMessage(SaibonChat.message("Mod loaded."))
            checkNow()
        }
    }

    /** [force] lets a manual "Check for updates now" button bypass the auto-check toggle without changing what that toggle gates on join. */
    fun checkNow(force: Boolean = false) {
        if (!force && !Saibon.config.data.update.autoCheck) return

        val request = HttpRequest.newBuilder(URI.create(MANIFEST_URL)).GET().build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> gson.fromJson(response.body(), VersionManifest::class.java) }
            .thenAccept { manifest -> Minecraft.getInstance().execute { onManifest(manifest) } }
            .exceptionally { throwable ->
                Saibon.logger.warn("Saibon update check failed", throwable)
                null
            }
    }

    private fun onManifest(manifest: VersionManifest) {
        latestManifest = manifest
        val current = SemVer.parse(currentVersion()) ?: return
        val latest = SemVer.parse(manifest.latestVersion) ?: return
        if (latest <= current) return
        if (Saibon.config.data.update.dismissedVersion == manifest.latestVersion) return

        UpdatePrompt.send(manifest)
    }

    fun currentVersion(): String =
        FabricLoader.getInstance().getModContainer(Saibon.MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("0.0.0")
}
