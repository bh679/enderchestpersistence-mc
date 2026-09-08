package games.brennan.enderchestpersistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreLocationTest {

    private static final Path CONFIG = Path.of("/instance/config");
    private static final Path APPDATA = Path.of("/home/player/.local/share");

    private static final Path INSTANCE_STORE = CONFIG.resolve(StoreLocation.DIR_NAME);
    private static final Path OUTSIDE_STORE = APPDATA.resolve(StoreLocation.DIR_NAME);

    private static final Predicate<Path> WRITABLE = path -> true;
    private static final Predicate<Path> UNWRITABLE = path -> false;

    private static StoreLocation.Resolution resolve(StoreMode mode, boolean dedicated, Predicate<Path> writable) {
        return StoreLocation.resolve(mode, dedicated, CONFIG, APPDATA, writable);
    }

    @Test
    void outsideResolvesToTheAppDataStore() {
        StoreLocation.Resolution resolution = resolve(StoreMode.OUTSIDE, false, WRITABLE);
        assertEquals(StoreMode.OUTSIDE, resolution.mode());
        assertEquals(OUTSIDE_STORE, resolution.dir());
    }

    @Test
    void insideResolvesToThePre030Path() {
        StoreLocation.Resolution resolution = resolve(StoreMode.INSIDE, false, WRITABLE);
        assertEquals(StoreMode.INSIDE, resolution.mode());
        assertEquals(INSTANCE_STORE, resolution.dir(),
            "'inside' must keep using the exact directory older builds wrote to");
    }

    @Test
    void theInstanceDirectoryIsAlwaysKnown() {
        // The seeder needs it even when it isn't the directory in use.
        for (StoreMode mode : StoreMode.values()) {
            assertEquals(INSTANCE_STORE, resolve(mode, false, WRITABLE).instanceDir(), mode.configValue());
        }
    }

    @Test
    void aDedicatedServerStaysInsideTheInstance() {
        // Otherwise every unrelated server on one host would share its players' stashes.
        StoreLocation.Resolution resolution = resolve(StoreMode.OUTSIDE, true, WRITABLE);
        assertEquals(StoreMode.INSIDE, resolution.mode());
        assertEquals(INSTANCE_STORE, resolution.dir());
    }

    @Test
    void offResolvesToNoDirectory() {
        StoreLocation.Resolution resolution = resolve(StoreMode.OFF, false, WRITABLE);
        assertEquals(StoreMode.OFF, resolution.mode());
        assertNull(resolution.dir());
    }

    @Test
    void offIsRespectedOnADedicatedServerToo() {
        assertEquals(StoreMode.OFF, resolve(StoreMode.OFF, true, WRITABLE).mode());
    }

    @Test
    void offStillReportsTheInstanceDirectoryToCallers() {
        // A host mod's profile reset asks for the directory in order to delete a stash left behind by
        // a previous mode. Under 'off' there is no active store, but there may well be a stale file.
        StoreLocation.reset();
        assertEquals(INSTANCE_STORE, resolve(StoreMode.OFF, false, WRITABLE).instanceDir());
    }

    @Test
    void anUnwritableAppDataDirectoryFallsBackToTheInstance() {
        // A read-only or missing home directory must cost the player portability, never their chest.
        StoreLocation.Resolution resolution = resolve(StoreMode.OUTSIDE, false, UNWRITABLE);
        assertEquals(StoreMode.INSIDE, resolution.mode());
        assertEquals(INSTANCE_STORE, resolution.dir());
    }
}
