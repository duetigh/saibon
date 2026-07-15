package dev.saibon.update.installer

import dev.saibon.core.Saibon
import dev.saibon.update.UpdateChecker
import dev.saibon.update.VersionManifest
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * Downloads a release jar, verifies it, then hands off to a detached
 * [HelperMain] process to swap it into place after the game exits — Fabric
 * Loader locks every jar in `mods/` for the whole session, so Saibon can't
 * overwrite its own running jar itself. See `docs/planning/PLAN.md` for why.
 */
object UpdateInstaller {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "Saibon-Updater").apply { isDaemon = true } }
    private val httpClient: HttpClient = HttpClient.newBuilder().executor(executor).build()

    fun install(manifest: VersionManifest) {
        val request = HttpRequest.newBuilder(URI.create(manifest.downloadUrl)).GET().build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenAccept { response -> onDownloaded(manifest, response.body()) }
            .exceptionally { throwable ->
                Saibon.logger.error("Saibon update download failed", throwable)
                notifyPlayer("Saibon update download failed — check your connection and try again.")
                null
            }
    }

    private fun onDownloaded(manifest: VersionManifest, bytes: ByteArray) {
        if (!sha256Matches(bytes, manifest.sha256)) {
            Saibon.logger.warn("Saibon update download failed SHA-256 verification, aborting install")
            notifyPlayer("Saibon update failed verification — the download was not installed.")
            return
        }

        runCatching { stageAndSpawnHelper(manifest, bytes) }
            .onFailure {
                Saibon.logger.error("Saibon update install failed", it)
                notifyPlayer("Saibon update install failed — see the log for details.")
            }
    }

    private fun sha256Matches(bytes: ByteArray, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex.equals(expected, ignoreCase = true)
    }

    private fun stageAndSpawnHelper(manifest: VersionManifest, newJarBytes: ByteArray) {
        val stagingDir = FabricLoader.getInstance().gameDir.resolve("saibon-staging")
        Files.createDirectories(stagingDir)
        purgeOldStaging(stagingDir)

        val container = FabricLoader.getInstance().getModContainer(Saibon.MOD_ID).orElse(null)
            ?: error("Saibon's own mod container was not found")
        val runningJar = container.origin.paths.firstOrNull()
            ?: error("Saibon's own running jar path was not found")

        val stagedJar = stagingDir.resolve("saibon-${manifest.latestVersion}.jar")
        Files.write(stagedJar, newJarBytes)

        val helperCopy = stagingDir.resolve("helper-${UpdateChecker.currentVersion()}.jar")
        Files.copy(runningJar, helperCopy, StandardCopyOption.REPLACE_EXISTING)

        val javaBin = Path.of(
            System.getProperty("java.home"),
            "bin",
            if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
        )
        val logFile = stagingDir.resolve("helper.log")

        ProcessBuilder(
            javaBin.toString(),
            "-cp", helperCopy.toString(),
            "dev.saibon.update.installer.HelperMain",
            ProcessHandle.current().pid().toString(),
            stagedJar.toString(),
            runningJar.toString(),
            logFile.toString()
        ).start()

        notifyPlayer("Saibon v${manifest.latestVersion} downloaded — it will finish installing after you close Minecraft.")
    }

    private fun purgeOldStaging(stagingDir: Path) {
        if (!stagingDir.exists()) return
        val cutoff = Instant.now().minus(1, ChronoUnit.DAYS)
        Files.list(stagingDir).use { entries ->
            entries.filter { it.name != "helper.log" }
                .filter { Files.getLastModifiedTime(it).toInstant().isBefore(cutoff) }
                .forEach { runCatching { Files.delete(it) } }
        }
    }

    private fun notifyPlayer(text: String) {
        Minecraft.getInstance().execute {
            Minecraft.getInstance().player?.sendSystemMessage(Component.literal(text))
        }
    }
}
