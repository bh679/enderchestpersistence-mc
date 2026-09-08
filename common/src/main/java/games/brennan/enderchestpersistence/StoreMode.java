package games.brennan.enderchestpersistence;

import java.util.Locale;
import java.util.Optional;

/**
 * Where the Ender Chest store is kept — the one setting this mod exposes.
 *
 * <p>The distinction that matters to players is <em>instance</em>, not <em>world</em>. The store has
 * always lived outside any individual world save (that is the whole point: Dungeon Train starts a new
 * world on every death and the stash has to survive it), but until now it also lived inside the
 * launcher instance, at {@code <instance>/config/enderchestpersistence/}. That made it invisible to a
 * launcher migration: someone moving CurseForge &rarr; Prism carries {@code saves/} across, leaves
 * {@code config/} behind, and their chest is gone with it.</p>
 *
 * @see StoreLocation for how each mode resolves to a directory
 */
public enum StoreMode {

    /** {@code <instance>/config/enderchestpersistence/} — the pre-0.3.0 behaviour. */
    INSIDE("inside"),

    /**
     * The OS application-data directory, shared by every instance on the machine. The default, so a
     * chest survives changing launcher, rebuilding an instance, or reinstalling the modpack.
     */
    OUTSIDE("outside"),

    /** No persistence at all: the Ender Chest reverts to vanilla, per-world behaviour. */
    OFF("off");

    private final String configValue;

    StoreMode(String configValue) {
        this.configValue = configValue;
    }

    /** The value written to and read from the config file. */
    public String configValue() {
        return configValue;
    }

    /**
     * Parse a config value, case- and whitespace-insensitively.
     *
     * @return the mode, or empty if {@code raw} is null, blank, or not a known mode — the caller
     *         decides what to do about it, because "unset" and "misspelled" deserve different logs
     */
    public static Optional<StoreMode> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (StoreMode mode : values()) {
            if (mode.configValue.equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
