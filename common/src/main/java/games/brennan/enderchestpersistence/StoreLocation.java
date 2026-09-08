package games.brennan.enderchestpersistence;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Resolves, once per server start, which directory {@link EnderChestStore} reads and writes — and
 * remembers the instance-local directory alongside it, so a store that has moved outside the instance
 * can still find what an older build left behind (see {@link StoreSeeder}).
 *
 * <p>Mirrors the {@link ConfigDir} pattern: each loader's server-starting hook calls {@link #init},
 * and everything downstream reads the resolved values.</p>
 */
public final class StoreLocation {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The subdirectory name, under either root. */
    public static final String DIR_NAME = "enderchestpersistence";

    private static volatile Resolution resolution = null;

    private StoreLocation() {}

    /** The outcome of resolving the configured mode against this machine. */
    public record Resolution(StoreMode mode, Path dir, Path instanceDir) {}

    /**
     * Resolve the store for this server. Call once from the loader's server-starting hook, before any
     * player can log in.
     *
     * @param dedicatedServer whether this is a dedicated server, which always stores inside the
     *                        instance — a machine-level store would be shared by every unrelated
     *                        server on the host, which is never what an operator means
     */
    public static void init(boolean dedicatedServer) {
        StoreMode configured = EcpConfig.readStoreMode(ConfigDir.get());
        resolution = resolve(configured, dedicatedServer, ConfigDir.get(), AppDataDir.resolve(),
                StoreLocation::ensureWritable);

        if (configured == StoreMode.OFF) {
            LOGGER.warn("[EnderChestPersistence] store-location=off — Ender Chests are vanilla, per-world."
                    + " Mods relying on this mod's chest isolation will not isolate.");
        } else if (dedicatedServer && configured == StoreMode.OUTSIDE) {
            LOGGER.info("[EnderChestPersistence] dedicated server — storing inside the instance at {}"
                    + " (store-location=outside applies to clients only).", resolution.dir());
        } else {
            LOGGER.info("[EnderChestPersistence] store-location={} — storing at {}",
                    resolution.mode().configValue(), resolution.dir());
        }
    }

    /**
     * Pure resolution, given everything that varies. {@code ensureWritable} both creates the directory
     * and reports whether it can be used; when the outside directory can't be, the store falls back to
     * the instance rather than failing, because a read-only home directory must not cost a player
     * their chest.
     */
    static Resolution resolve(StoreMode configured, boolean dedicatedServer, Path configDir,
                              Path appDataRoot, Predicate<Path> ensureWritable) {
        Path instanceDir = configDir.resolve(DIR_NAME);
        if (configured == StoreMode.OFF) {
            return new Resolution(StoreMode.OFF, null, instanceDir);
        }
        if (configured == StoreMode.INSIDE || dedicatedServer) {
            return new Resolution(StoreMode.INSIDE, instanceDir, instanceDir);
        }
        Path outsideDir = appDataRoot.resolve(DIR_NAME);
        if (ensureWritable.test(outsideDir)) {
            return new Resolution(StoreMode.OUTSIDE, outsideDir, instanceDir);
        }
        LOGGER.error("[EnderChestPersistence] {} is not writable — falling back to store-location=inside."
                + " Your Ender Chest is safe, but it will not follow you to another instance.", outsideDir);
        return new Resolution(StoreMode.INSIDE, instanceDir, instanceDir);
    }

    private static boolean ensureWritable(Path dir) {
        try {
            Files.createDirectories(dir);
            return Files.isWritable(dir);
        } catch (IOException e) {
            LOGGER.error("[EnderChestPersistence] could not create {}: {}", dir, e.getMessage());
            return false;
        }
    }

    /** The resolved mode. {@link StoreMode#INSIDE} before {@link #init}, matching pre-0.3.0 behaviour. */
    public static StoreMode mode() {
        Resolution current = resolution;
        return current == null ? StoreMode.INSIDE : current.mode();
    }

    /** Whether anything is persisted at all. */
    public static boolean enabled() {
        return mode() != StoreMode.OFF;
    }

    /**
     * The directory holding {@code <uuid>.dat}.
     *
     * <p>Always answers with a path, never throws. Under {@link StoreMode#OFF} nothing is being
     * persisted, but a file left behind by a previous mode may still be sitting in the instance
     * directory — and that is the answer callers want. Dungeon Train's profile reset, for one, asks
     * this to find and delete a stale stash ({@code EnderChestResetBridge}); throwing here would take
     * its reset screen down instead of clearing the leftover file.</p>
     */
    public static Path dir() {
        Resolution current = resolution;
        if (current == null) {
            // A login before server-start would be a lifecycle bug, but answering with the historic
            // path is strictly better than throwing at a player.
            LOGGER.warn("[EnderChestPersistence] store location requested before init — assuming 'inside'.");
            return ConfigDir.get().resolve(DIR_NAME);
        }
        // Resolution.dir() is null only under OFF, where the instance directory is the right answer.
        return current.dir() != null ? current.dir() : current.instanceDir();
    }

    /** The instance-local directory, whether or not it is the one in use. */
    public static Path instanceDir() {
        Resolution current = resolution;
        return current == null ? ConfigDir.get().resolve(DIR_NAME) : current.instanceDir();
    }

    /** Test seam: drop the resolved state so each test resolves from scratch. */
    static void reset() {
        resolution = null;
    }
}
