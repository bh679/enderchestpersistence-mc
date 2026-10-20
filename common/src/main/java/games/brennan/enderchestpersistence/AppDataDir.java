package games.brennan.enderchestpersistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Resolves the per-user application-data directory that backs {@link StoreMode#OUTSIDE}, following
 * each platform's own convention rather than dropping a dotfolder in the user's home on all three.
 *
 * <table>
 *   <caption>Resolution order</caption>
 *   <tr><td>Windows</td><td>{@code %USERPROFILE%\AppData\Roaming}, else {@code <home>/AppData/Roaming}</td></tr>
 *   <tr><td>macOS</td><td>{@code <home>/Library/Application Support}</td></tr>
 *   <tr><td>other</td><td>{@code $XDG_DATA_HOME}, else {@code <home>/.local/share}</td></tr>
 * </table>
 *
 * <p><b>Why not {@code %APPDATA%} on Windows?</b> Builds before 0.7.0 used it, and it is what the
 * folder is for. But launchers that isolate a game folder (PCL, for one) start the game with
 * {@code APPDATA} pointed at that folder, so the "machine-level" store silently became a
 * per-launcher store: a player who moved from PCL to the Modrinth app found an empty chest, with
 * the real one sitting in {@code D:\...\.minecraft\enderchestpersistence}. {@code USERPROFILE} is
 * not something launchers redirect, and for everyone else it resolves to the same directory
 * {@code APPDATA} did. The directory an older build may have used is still reported by
 * {@link #legacyCandidates}, so {@link LegacyCarryOver} can pick the chest up from there.</p>
 *
 * <p>Every input is a parameter — OS name, environment lookup, home directory — so the resolution for
 * all three platforms is unit-testable on whichever one happens to be running the tests. That matters
 * here more than usual: this code decides where someone's Ender Chest lives, and it ships to players
 * on platforms the developer never runs.</p>
 */
public final class AppDataDir {

    private AppDataDir() {}

    /** The app-data root for the current machine. */
    public static Path resolve() {
        return resolve(System.getProperty("os.name", ""), System::getenv, home());
    }

    /** App-data roots an older build may have resolved on this machine, minus the current one. */
    public static List<Path> legacyCandidates() {
        return legacyCandidates(System.getProperty("os.name", ""), System::getenv, home());
    }

    private static Path home() {
        return Path.of(System.getProperty("user.home", "."));
    }

    /**
     * @param osName the {@code os.name} system property
     * @param env    environment lookup, returning null for unset variables
     * @param home   the user's home directory
     * @return the platform's app-data root (the {@code enderchestpersistence} subdirectory is added
     *         by {@link StoreLocation}, not here)
     */
    static Path resolve(String osName, Function<String, String> env, Path home) {
        if (isWindows(osName)) {
            Path profile = pathOrNull(env.apply("USERPROFILE"), home);
            return profile.resolve("AppData").resolve("Roaming");
        }
        if (isMac(osName)) {
            return home.resolve("Library").resolve("Application Support");
        }
        return pathOrNull(env.apply("XDG_DATA_HOME"), home.resolve(".local").resolve("share"));
    }

    /**
     * The app-data roots a build before 0.7.0 would have resolved with the same inputs, in the order
     * worth searching, without the one {@link #resolve} answers now. Empty on macOS and Linux, whose
     * resolution has not changed.
     */
    static List<Path> legacyCandidates(String osName, Function<String, String> env, Path home) {
        List<Path> candidates = new ArrayList<>();
        if (isWindows(osName)) {
            Path current = resolve(osName, env, home);
            // Exactly what a pre-0.7.0 build resolved: APPDATA when set, else <home>/AppData/Roaming.
            Path before = pathOrNull(env.apply("APPDATA"), home.resolve("AppData").resolve("Roaming"));
            if (!sameDir(before, current)) {
                candidates.add(before);
            }
        }
        return List.copyOf(candidates);
    }

    private static boolean isWindows(String osName) {
        return normalize(osName).contains("win");
    }

    private static boolean isMac(String osName) {
        String os = normalize(osName);
        return os.contains("mac") || os.contains("darwin");
    }

    private static String normalize(String osName) {
        return osName == null ? "" : osName.toLowerCase(Locale.ROOT);
    }

    private static boolean sameDir(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    /** {@code value} as a path when it is set and non-blank, else {@code fallback}. */
    private static Path pathOrNull(String value, Path fallback) {
        return value == null || value.isBlank() ? fallback : Path.of(value.trim());
    }
}
