package dev.saibon.update.installer;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Detached updater helper, spawned by {@code UpdateInstaller} after the user
 * clicks "Update Now". Deliberately plain Java with zero non-JDK imports: it
 * runs with {@code -cp <copy-of-the-mod-jar>} outside Fabric Loader, so
 * Kotlin's stdlib (supplied at runtime by fabric-language-kotlin, not bundled
 * in Saibon's thin jar) and Fabric/Minecraft classes are not on its
 * classpath. It waits for the launching Minecraft process to fully exit,
 * then moves the staged jar over the (by then unlocked) target jar.
 */
public final class HelperMain {

    private static final int MAX_ATTEMPTS = 20;
    private static final long RETRY_DELAY_MS = 500;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: HelperMain <parentPid> <stagedJar> <targetJar> <logFile>");
            return;
        }

        long parentPid = Long.parseLong(args[0]);
        Path stagedJar = Path.of(args[1]);
        Path targetJar = Path.of(args[2]);
        Path logFile = Path.of(args[3]);

        try (PrintWriter log = new PrintWriter(new FileWriter(logFile.toFile(), true))) {
            log.println(Instant.now() + " HelperMain waiting for parent pid " + parentPid + " to exit");

            ProcessHandle.of(parentPid).ifPresent(handle -> {
                try {
                    handle.onExit().join();
                } catch (Exception e) {
                    log.println(Instant.now() + " error waiting for parent exit: " + e);
                }
            });

            log.println(Instant.now() + " parent exited, installing " + stagedJar + " -> " + targetJar);

            boolean installed = false;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS && !installed; attempt++) {
                try {
                    Files.move(stagedJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
                    installed = true;
                    log.println(Instant.now() + " install succeeded on attempt " + attempt);
                } catch (Exception e) {
                    log.println(Instant.now() + " attempt " + attempt + " failed: " + e);
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }

            if (!installed) {
                log.println(Instant.now() + " install failed after " + MAX_ATTEMPTS + " attempts, giving up");
            }
        }
    }
}
