package games.brennan.enderchestpersistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the bug this feature was written for, end to end through the production call path
 * ({@link StoreLocation#init} &rarr; {@link EnderChestStore#file} &rarr;
 * {@link EnderChestStore#seedFromInstanceIfNeeded}) rather than testing each piece in isolation.
 *
 * <p>The report: a player moved their instance from the CurseForge launcher to Prism, restored their
 * worlds, and found their Ender Chest empty — because the store lived inside the old instance and only
 * {@code saves/} came across.</p>
 *
 * <p>Contents here stand in for the gzipped NBT of a real stash; nothing in the path being tested
 * inspects them, and using plain text keeps the test free of a Minecraft bootstrap.</p>
 */
class StoreMigrationTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String STASH = "27 slots of hard-won loot";

    @TempDir
    Path oldInstance;
    @TempDir
    Path newInstance;
    @TempDir
    Path appData;

    @BeforeEach
    @AfterEach
    void clearResolvedState() {
        StoreLocation.reset();
    }

    /** The file a pre-0.3.0 install would have written, inside {@code instanceConfig}. */
    private Path writeLegacyStash(Path instanceConfig, String contents) throws IOException {
        Path file = instanceConfig.resolve(StoreLocation.DIR_NAME).resolve(PLAYER + ".dat");
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    private Path appDataStash() {
        return appData.resolve(StoreLocation.DIR_NAME).resolve(PLAYER + ".dat");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void updatingToOutsideCarriesAnExistingChestAcross() throws IOException {
        Path legacy = writeLegacyStash(oldInstance, STASH);

        StoreLocation.init(StoreMode.OUTSIDE, false, oldInstance, appData);

        assertEquals(appDataStash(), EnderChestStore.file(PLAYER),
            "the store should now resolve to the machine-level location");
        assertTrue(EnderChestStore.seedFromInstanceIfNeeded(PLAYER), "the existing chest should be carried across");
        assertEquals(STASH, read(EnderChestStore.file(PLAYER)), "the chest should arrive intact");
        assertTrue(Files.isRegularFile(legacy),
            "the instance copy must survive, so switching back to 'inside' still finds a chest");
    }

    @Test
    void theChestSurvivesMovingToACompletelyNewInstance() throws IOException {
        // 1. The player's existing install, updated to 0.3.0: the chest moves to the machine store.
        writeLegacyStash(oldInstance, STASH);
        StoreLocation.init(StoreMode.OUTSIDE, false, oldInstance, appData);
        EnderChestStore.seedFromInstanceIfNeeded(PLAYER);

        // 2. CurseForge -> Prism. A brand new instance: empty config dir, worlds restored separately.
        StoreLocation.reset();
        StoreLocation.init(StoreMode.OUTSIDE, false, newInstance, appData);

        assertEquals(appDataStash(), EnderChestStore.file(PLAYER));
        assertTrue(Files.isRegularFile(EnderChestStore.file(PLAYER)),
            "this is the reported bug: a new instance must still find the chest");
        assertEquals(STASH, read(EnderChestStore.file(PLAYER)));
    }

    @Test
    void aStaleInstanceCopyNeverOverwritesTheLiveChest() throws IOException {
        // The old instance is still on disk with an outdated chest; the machine store is current.
        writeLegacyStash(oldInstance, "stale chest from months ago");
        Files.createDirectories(appDataStash().getParent());
        Files.writeString(appDataStash(), STASH, StandardCharsets.UTF_8);

        StoreLocation.init(StoreMode.OUTSIDE, false, oldInstance, appData);

        assertFalse(EnderChestStore.seedFromInstanceIfNeeded(PLAYER), "nothing should be carried across");
        assertEquals(STASH, read(EnderChestStore.file(PLAYER)));
    }

    @Test
    void insideModeReadsTheInstanceStoreAndSeedsNothing() throws IOException {
        Path legacy = writeLegacyStash(oldInstance, STASH);

        StoreLocation.init(StoreMode.INSIDE, false, oldInstance, appData);

        assertEquals(legacy, EnderChestStore.file(PLAYER), "'inside' must keep using the pre-0.3.0 path");
        assertFalse(EnderChestStore.seedFromInstanceIfNeeded(PLAYER));
        assertFalse(Files.exists(appDataStash()), "'inside' must not copy anything out of the instance");
    }

    @Test
    void offSeedsNothingButStillPointsAtALeftoverFile() throws IOException {
        Path legacy = writeLegacyStash(oldInstance, STASH);

        StoreLocation.init(StoreMode.OFF, false, oldInstance, appData);

        assertFalse(EnderChestStore.seedFromInstanceIfNeeded(PLAYER), "'off' must not move a player's data");
        assertFalse(Files.exists(appDataStash()));
        // A host mod's profile reset asks for this path in order to delete a stale stash.
        assertEquals(legacy, EnderChestStore.file(PLAYER));
    }

    @Test
    void aDedicatedServerKeepsUsingItsOwnInstanceAndSeedsNothing() throws IOException {
        Path legacy = writeLegacyStash(oldInstance, STASH);

        StoreLocation.init(StoreMode.OUTSIDE, true, oldInstance, appData);

        assertEquals(legacy, EnderChestStore.file(PLAYER),
            "a machine-level store would be shared by every unrelated server on the host");
        assertFalse(EnderChestStore.seedFromInstanceIfNeeded(PLAYER));
        assertFalse(Files.exists(appDataStash()));
    }
}
