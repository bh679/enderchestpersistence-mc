package games.brennan.enderchestpersistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The safety net around one player's file, driven over real files: the {@code .bak} that only ever
 * holds a version with items in it, and the read path that refuses to lose an unreadable stash.
 *
 * <p>Slot lists hold minimal compound tags standing in for item stacks; nothing here inspects them,
 * so no Minecraft bootstrap is needed.</p>
 */
class StoreFileTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("player.dat");
    }

    /** A root tag with the given number of stacks in each named slot. */
    private static CompoundTag root(int survival, int creative) {
        CompoundTag tag = new CompoundTag();
        tag.put("survival", stacks(survival));
        if (creative >= 0) tag.put("creative", stacks(creative));
        return tag;
    }

    private static ListTag stacks(int n) {
        ListTag list = new ListTag();
        for (int i = 0; i < n; i++) {
            CompoundTag stack = new CompoundTag();
            stack.putByte("Slot", (byte) i);
            list.add(stack);
        }
        return list;
    }

    private static void corrupt(Path path) throws IOException {
        Files.write(path, new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00, 0x01});
    }

    private List<Path> corruptFiles() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().contains(StoreFile.CORRUPT_SUFFIX)).toList();
        }
    }

    // ---- itemCount ----

    @Test
    void itemCountSumsEverySlotList() {
        assertEquals(0, StoreFile.itemCount(null));
        assertEquals(0, StoreFile.itemCount(new CompoundTag()));
        assertEquals(0, StoreFile.itemCount(root(0, 0)));
        assertEquals(7, StoreFile.itemCount(root(5, 2)));
    }

    // ---- read ----

    @Test
    void readAbsent() {
        StoreFile.Loaded loaded = StoreFile.read(file());
        assertEquals(StoreFile.Outcome.ABSENT, loaded.outcome());
        assertNull(loaded.tag());
        assertFalse(loaded.hasTag());
    }

    @Test
    void writeThenReadRoundTrips() {
        assertTrue(StoreFile.write(file(), root(3, 1)));
        StoreFile.Loaded loaded = StoreFile.read(file());
        assertEquals(StoreFile.Outcome.LOADED, loaded.outcome());
        assertEquals(4, StoreFile.itemCount(loaded.tag()));
        assertFalse(Files.exists(StoreFile.bak(file())), "nothing was replaced, so no .bak yet");
    }

    // ---- .bak rotation ----

    @Test
    void overwritingNonEmptyFileRotatesItIntoBak() throws IOException {
        StoreFile.write(file(), root(3, 0));
        byte[] fuller = Files.readAllBytes(file());

        StoreFile.write(file(), root(1, 0));

        assertArrayEquals(fuller, Files.readAllBytes(StoreFile.bak(file())), ".bak is the replaced version");
        assertEquals(1, StoreFile.itemCount(StoreFile.read(file()).tag()));
    }

    @Test
    void overwritingEmptyFileLeavesBakAlone() throws IOException {
        StoreFile.write(file(), root(3, 0));          // good
        StoreFile.write(file(), root(0, 0));          // loss: .bak = good
        byte[] good = Files.readAllBytes(StoreFile.bak(file()));

        StoreFile.write(file(), root(0, 0));          // another empty session
        StoreFile.write(file(), root(0, -1));         // and another

        assertArrayEquals(good, Files.readAllBytes(StoreFile.bak(file())),
                "empty versions never displace the last non-empty one");
        assertEquals(3, StoreFile.itemCount(StoreFile.read(StoreFile.bak(file())).tag()));
    }

    // ---- unreadable main file ----

    @Test
    void unreadableMainWithGoodBakSelfHeals() throws IOException {
        StoreFile.write(file(), root(3, 2));
        StoreFile.write(file(), root(3, 2));          // second write populates .bak
        byte[] bak = Files.readAllBytes(StoreFile.bak(file()));
        corrupt(file());

        StoreFile.Loaded loaded = StoreFile.read(file());

        assertEquals(StoreFile.Outcome.LOADED_FROM_BACKUP, loaded.outcome());
        assertEquals(5, StoreFile.itemCount(loaded.tag()));
        assertArrayEquals(bak, Files.readAllBytes(file()), "main file is the backup again");
        assertTrue(Files.exists(StoreFile.bak(file())), ".bak is kept");
        List<Path> setAside = corruptFiles();
        assertEquals(1, setAside.size(), "the bad file was set aside, not deleted");
        assertEquals(5, Files.size(setAside.get(0)));
    }

    @Test
    void unreadableMainWithoutBakIsLeftUntouched() throws IOException {
        corrupt(file());
        byte[] before = Files.readAllBytes(file());

        StoreFile.Loaded loaded = StoreFile.read(file());

        assertEquals(StoreFile.Outcome.UNREADABLE, loaded.outcome());
        assertNull(loaded.tag());
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertTrue(corruptFiles().isEmpty());
    }

    @Test
    void unreadableMainAndUnreadableBakIsLeftUntouched() throws IOException {
        corrupt(file());
        corrupt(StoreFile.bak(file()));
        byte[] before = Files.readAllBytes(file());

        StoreFile.Loaded loaded = StoreFile.read(file());

        assertEquals(StoreFile.Outcome.UNREADABLE, loaded.outcome());
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertTrue(corruptFiles().isEmpty());
    }

    @Test
    void writingOverAnUnreadableFileDoesNotClobberBak() throws IOException {
        StoreFile.write(file(), root(3, 0));
        StoreFile.write(file(), root(3, 0));          // .bak = 3 stacks
        corrupt(file());

        // The store never calls write on a blocked UUID, but StoreFile itself must still not
        // replace a good .bak with unreadable bytes if it ever is asked to.
        StoreFile.write(file(), root(0, 0));

        assertEquals(3, StoreFile.itemCount(StoreFile.read(StoreFile.bak(file())).tag()));
    }
}
