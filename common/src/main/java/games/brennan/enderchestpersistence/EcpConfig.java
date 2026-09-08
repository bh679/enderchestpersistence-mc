package games.brennan.enderchestpersistence;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * The mod's single config file, {@code <config>/enderchestpersistence.properties}.
 *
 * <p>Deliberately a plain properties file rather than a loader config API: this mod ships for
 * NeoForge, Forge <em>and</em> Fabric, and {@code ModConfigSpec} exists on only two of them. One
 * hand-rolled reader in common code behaves identically everywhere and keeps the loader modules as
 * thin as they are today.</p>
 */
public final class EcpConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String FILE_NAME = "enderchestpersistence.properties";
    static final String KEY_STORE_LOCATION = "store-location";

    /** Used when the file is missing, unreadable, or holds a value we don't recognise. */
    public static final StoreMode DEFAULT_MODE = StoreMode.OUTSIDE;

    private EcpConfig() {}

    /**
     * Read the configured store mode, writing a commented default file if none exists.
     *
     * <p>Never throws and never returns null. Every failure path — missing file, I/O error, garbage
     * value — resolves to {@link #DEFAULT_MODE} with a log line saying why, because a config problem
     * must not be the reason a player's Ender Chest fails to load.</p>
     */
    public static StoreMode readStoreMode(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            writeDefault(file);
            return DEFAULT_MODE;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            LOGGER.error("[EnderChestPersistence] could not read {} ({}) — using '{}'",
                    file, e.getMessage(), DEFAULT_MODE.configValue());
            return DEFAULT_MODE;
        }

        String raw = properties.getProperty(KEY_STORE_LOCATION);
        Optional<StoreMode> parsed = StoreMode.parse(raw);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        if (raw == null || raw.isBlank()) {
            LOGGER.warn("[EnderChestPersistence] {} has no '{}' — using '{}'",
                    FILE_NAME, KEY_STORE_LOCATION, DEFAULT_MODE.configValue());
        } else {
            LOGGER.warn("[EnderChestPersistence] unrecognised {}='{}' in {} — expected inside/outside/off,"
                            + " using '{}'",
                    KEY_STORE_LOCATION, raw.trim(), FILE_NAME, DEFAULT_MODE.configValue());
        }
        return DEFAULT_MODE;
    }

    /** Write the commented default config. Best-effort: a failure here only costs the player the comments. */
    private static void writeDefault(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultFileContents(), StandardCharsets.UTF_8);
            LOGGER.info("[EnderChestPersistence] wrote default config {}", file);
        } catch (IOException e) {
            LOGGER.warn("[EnderChestPersistence] could not write default config {}: {}", file, e.getMessage());
        }
    }

    /** The default file, comments included. Package-private so the tests assert it parses back. */
    static String defaultFileContents() {
        return """
            # Ender Chest Persistence

            # Where your Ender Chest contents are stored. In every mode the store lives outside
            # any individual world save, so the chest survives starting a new world.
            #
            #   outside  (default)  The application-data folder shared by every instance on this
            #                       computer. Survives changing launcher, rebuilding an instance, or
            #                       reinstalling the modpack -- moving CurseForge -> Prism keeps your
            #                       chest. Dedicated servers ignore this and use 'inside', so two
            #                       unrelated servers on one host never share player stashes.
            #   inside              This instance's config folder only, as builds before 0.3.0 did.
            #                       Keeps separate instances' chests separate. Note that anything
            #                       which copies only your worlds -- a launcher migration, a saves
            #                       backup -- will NOT carry the chest.
            #   off                 No persistence: the Ender Chest behaves like vanilla, per world.
            #                       Warning: mods that isolate chests through this mod (for example
            #                       Dungeon Train's Free Play lock and its per-difficulty stash) stop
            #                       isolating them when this is off.
            #
            # Switching from 'inside' to 'outside' copies your existing chest across on the next
            # login and leaves the original in place, so nothing is lost either way.

            store-location=outside
            """;
    }
}
