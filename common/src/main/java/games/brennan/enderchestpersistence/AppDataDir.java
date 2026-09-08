package games.brennan.enderchestpersistence;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * Resolves the per-user application-data directory that backs {@link StoreMode#OUTSIDE}, following
 * each platform's own convention rather than dropping a dotfolder in the user's home on all three.
 *
 * <table>
 *   <caption>Resolution order</caption>
 *   <tr><td>Windows</td><td>{@code %APPDATA%}, else {@code <home>/AppData/Roaming}</td></tr>
 *   <tr><td>macOS</td><td>{@code <home>/Library/Application Support}</td></tr>
 *   <tr><td>other</td><td>{@code $XDG_DATA_HOME}, else {@code <home>/.local/share}</td></tr>
 * </table>
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
        return resolve(System.getProperty("os.name", ""), System::getenv, Path.of(System.getProperty("user.home", ".")));
    }

    /**
     * @param osName the {@code os.name} system property
     * @param env    environment lookup, returning null for unset variables
     * @param home   the user's home directory
     * @return the platform's app-data root (the {@code enderchestpersistence} subdirectory is added
     *         by {@link StoreLocation}, not here)
     */
    static Path resolve(String osName, Function<String, String> env, Path home) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return pathOrNull(env.apply("APPDATA"), home.resolve("AppData").resolve("Roaming"));
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return home.resolve("Library").resolve("Application Support");
        }
        return pathOrNull(env.apply("XDG_DATA_HOME"), home.resolve(".local").resolve("share"));
    }

    /** {@code value} as a path when it is set and non-blank, else {@code fallback}. */
    private static Path pathOrNull(String value, Path fallback) {
        return value == null || value.isBlank() ? fallback : Path.of(value.trim());
    }
}
