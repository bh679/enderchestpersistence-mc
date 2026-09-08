package games.brennan.enderchestpersistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The resolver decides where a player's Ender Chest lives on platforms this repo's tests will never
 * run on, so every branch is exercised here with the OS name and environment injected.
 */
class AppDataDirTest {

    private static final Path HOME = Path.of("/home/player");

    private static Path resolve(String osName, Map<String, String> env) {
        return AppDataDir.resolve(osName, env::get, HOME);
    }

    @Test
    void windowsPrefersAppData() {
        assertEquals(Path.of("C:\\Users\\p\\AppData\\Roaming"),
                resolve("Windows 11", Map.of("APPDATA", "C:\\Users\\p\\AppData\\Roaming")));
    }

    @Test
    void windowsFallsBackToHomeWhenAppDataUnset() {
        assertEquals(HOME.resolve("AppData").resolve("Roaming"), resolve("Windows 10", Map.of()));
    }

    @Test
    void macUsesApplicationSupport() {
        assertEquals(HOME.resolve("Library").resolve("Application Support"),
                resolve("Mac OS X", Map.of()));
    }

    @Test
    void linuxPrefersXdgDataHome() {
        assertEquals(Path.of("/home/player/.share"),
                resolve("Linux", Map.of("XDG_DATA_HOME", "/home/player/.share")));
    }

    @Test
    void linuxFallsBackToLocalShare() {
        assertEquals(HOME.resolve(".local").resolve("share"), resolve("Linux", Map.of()));
    }

    @Test
    void blankEnvironmentValuesAreTreatedAsUnset() {
        // An exported-but-empty XDG_DATA_HOME would otherwise resolve the store to the filesystem root.
        assertEquals(HOME.resolve(".local").resolve("share"),
                resolve("Linux", Map.of("XDG_DATA_HOME", "   ")));
    }

    @Test
    void unknownOsIsTreatedAsUnixLike() {
        assertEquals(HOME.resolve(".local").resolve("share"), resolve("SomeFutureOS", Map.of()));
        assertEquals(HOME.resolve(".local").resolve("share"), resolve(null, Map.of()));
    }
}
