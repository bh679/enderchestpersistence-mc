package games.brennan.enderchestpersistence;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The launcher-switch case over real files: a chest a pre-0.7.0 build left in a launcher-redirected
 * {@code APPDATA} folder is found, copied or merged into the live store, and then left alone.
 */
class LegacyCarryOverTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @TempDir
    Path pclStore;      // what PCL's redirected APPDATA made the old build use
    @TempDir
    Path liveStore;     // %USERPROFILE%\AppData\Roaming\enderchestpersistence

    private Path live() {
        return liveStore.resolve(PLAYER + ".dat");
    }

    private Path legacy() {
        return pclStore.resolve(PLAYER + ".dat");
    }

    private static CompoundTag slot(String key, int stacks) {
        return StoreMergeTest.root(key, stacks);
    }

    private static int stacksIn(Path file, String key) {
        return StoreFile.read(file).tag().getList(key, 10).size();
    }

    private LegacyCarryOver.Result run() {
        return LegacyCarryOver.run(PLAYER, live(), List.of(pclStore));
    }

    @Test
    void copiesTheChestWhenTheLiveStoreHasNone() throws IOException {
        StoreFile.write(legacy(), slot("survival", 6));
        Files.writeString(StoreFile.bak(legacy()), "backup bytes");

        LegacyCarryOver.Result result = run();

        assertEquals(LegacyCarryOver.Kind.COPIED, result.kind());
        assertEquals(pclStore, result.source());
        assertEquals(6, stacksIn(live(), "survival"));
        assertTrue(Files.isRegularFile(StoreFile.bak(live())), "the backup travels with the chest");
        assertTrue(Files.isRegularFile(legacy()), "the original is never modified");
        assertTrue(Files.isRegularFile(LegacyCarryOver.marker(legacy())));
    }

    @Test
    void mergesASlotTheLiveChestLacks() throws IOException {
        StoreFile.write(legacy(), slot("creative", 9));
        StoreFile.write(live(), slot("survival", 15));

        LegacyCarryOver.Result result = run();

        assertEquals(LegacyCarryOver.Kind.MERGED, result.kind());
        assertEquals(15, stacksIn(live(), "survival"));
        assertEquals(9, stacksIn(live(), "creative"));
    }

    @Test
    void aSameSlotConflictKeepsTheLiveChestAndReportsIt() throws IOException {
        // LitniR: the PCL chest holds 6 survival stacks, the Modrinth one 15 gathered since.
        StoreFile.write(legacy(), slot("survival", 6));
        StoreFile.write(live(), slot("survival", 15));

        LegacyCarryOver.Result result = run();

        assertEquals(LegacyCarryOver.Kind.CONFLICT, result.kind());
        assertEquals(List.of("survival"), result.conflicts());
        assertEquals(15, stacksIn(live(), "survival"));
        assertEquals(6, stacksIn(legacy(), "survival"), "the original is never modified");
    }

    @Test
    void aChestOnceLookedAtIsNotReportedAgain() throws IOException {
        StoreFile.write(legacy(), slot("survival", 6));
        StoreFile.write(live(), slot("survival", 15));

        assertEquals(LegacyCarryOver.Kind.CONFLICT, run().kind());
        assertEquals(LegacyCarryOver.Kind.NONE, run().kind(), "the marker stops the nag on the next login");
    }

    @Test
    void aChestWrittenAgainAfterTheMarkerIsLookedAtAfresh() throws IOException {
        StoreFile.write(legacy(), slot("survival", 6));
        assertEquals(LegacyCarryOver.Kind.COPIED, run().kind());

        // An older build, still running from the other launcher, saves a new slot into the old file.
        StoreFile.write(legacy(), slot("adventure", 2));
        Files.setLastModifiedTime(legacy(),
                FileTime.fromMillis(Files.getLastModifiedTime(LegacyCarryOver.marker(legacy())).toMillis() + 5_000));

        assertEquals(LegacyCarryOver.Kind.MERGED, run().kind());
        assertEquals(2, stacksIn(live(), "adventure"));
    }

    @Test
    void anIdenticalChestIsMarkedAndNotReported() throws IOException {
        StoreFile.write(legacy(), slot("survival", 6));
        StoreFile.write(live(), slot("survival", 6));

        assertEquals(LegacyCarryOver.Kind.NONE, run().kind());
        assertTrue(Files.isRegularFile(LegacyCarryOver.marker(legacy())), "remembered, so it is not re-read every login");
    }

    @Test
    void doesNothingWithoutACandidate() {
        assertEquals(LegacyCarryOver.Kind.NONE, run().kind());
        assertFalse(Files.exists(live()));
        assertEquals(LegacyCarryOver.Kind.NONE, LegacyCarryOver.run(PLAYER, live(), List.of()).kind());
    }
}
