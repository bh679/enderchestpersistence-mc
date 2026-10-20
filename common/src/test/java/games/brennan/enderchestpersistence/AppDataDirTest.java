package games.brennan.enderchestpersistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
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

    private static List<Path> legacy(String osName, Map<String, String> env) {
        return AppDataDir.legacyCandidates(osName, env::get, HOME);
    }

    private static final String PROFILE = "C:\\Users\\p";
    private static final Path ROAMING = Path.of(PROFILE, "AppData", "Roaming");

    @Test
    void windowsResolvesFromTheUserProfile() {
        assertEquals(ROAMING, resolve("Windows 11", Map.of("USERPROFILE", PROFILE, "APPDATA", ROAMING.toString())));
    }

    @Test
    void windowsIgnoresALauncherRedirectedAppData() {
        // PCL starts the game with APPDATA pointed into its game folder; the chest must not follow it.
        assertEquals(ROAMING, resolve("Windows 11",
                Map.of("USERPROFILE", PROFILE, "APPDATA", "D:\\Minecraft\\.minecraft")));
    }

    @Test
    void windowsFallsBackToHomeWhenUserProfileUnset() {
        assertEquals(HOME.resolve("AppData").resolve("Roaming"), resolve("Windows 10", Map.of()));
        assertEquals(HOME.resolve("AppData").resolve("Roaming"),
                resolve("Windows 10", Map.of("APPDATA", "D:\\Minecraft\\.minecraft")));
    }

    @Test
    void aRedirectedAppDataIsTheLegacyCandidate() {
        assertEquals(List.of(Path.of("D:\\Minecraft\\.minecraft")), legacy("Windows 11",
                Map.of("USERPROFILE", PROFILE, "APPDATA", "D:\\Minecraft\\.minecraft")));
    }

    @Test
    void anOrdinaryWindowsSetupHasNoLegacyCandidates() {
        // APPDATA already names the resolved directory, so there is nowhere else an old build wrote.
        assertEquals(List.of(), legacy("Windows 11", Map.of("USERPROFILE", PROFILE, "APPDATA", ROAMING.toString())));
    }

    @Test
    void theHomeDerivedPathIsTheCandidateOnlyWhenAppDataWasUnset() {
        // Without APPDATA an old build fell back to user.home; with it set, home was never used.
        assertEquals(List.of(HOME.resolve("AppData").resolve("Roaming")),
                legacy("Windows 11", Map.of("USERPROFILE", PROFILE)));
        assertEquals(List.of(Path.of("D:\\Minecraft\\.minecraft")),
                legacy("Windows 11", Map.of("USERPROFILE", PROFILE, "APPDATA", "D:\\Minecraft\\.minecraft")));
    }

    @Test
    void otherPlatformsHaveNoLegacyCandidates() {
        assertEquals(List.of(), legacy("Mac OS X", Map.of()));
        assertEquals(List.of(), legacy("Linux", Map.of("XDG_DATA_HOME", "/x")));
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
