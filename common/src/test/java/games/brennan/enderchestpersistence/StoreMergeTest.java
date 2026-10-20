package games.brennan.enderchestpersistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The merge must never lose what the live chest holds; everything else is what it may add. */
class StoreMergeTest {

    static ListTag stacks(int n) {
        ListTag list = new ListTag();
        for (int i = 0; i < n; i++) {
            CompoundTag stack = new CompoundTag();
            stack.putByte("Slot", (byte) i);
            list.add(stack);
        }
        return list;
    }

    static CompoundTag root(String key, int n) {
        CompoundTag tag = new CompoundTag();
        tag.put(key, stacks(n));
        return tag;
    }

    @Test
    void fillsASlotTheLiveChestLacks() {
        CompoundTag live = root("survival", 3);
        StoreMerge.Result result = StoreMerge.merge(live, root("creative", 5));

        assertEquals(List.of("creative"), result.mergedSlots());
        assertFalse(result.hasConflicts());
        assertEquals(5, live.getList("creative", 10).size());
        assertEquals(3, live.getList("survival", 10).size(), "the live slot is untouched");
    }

    @Test
    void fillsASlotTheLiveChestHoldsEmpty() {
        CompoundTag live = root("survival", 0);
        StoreMerge.Result result = StoreMerge.merge(live, root("survival", 6));

        assertEquals(List.of("survival"), result.mergedSlots());
        assertEquals(6, live.getList("survival", 10).size());
    }

    @Test
    void aSlotWithItemsOnBothSidesIsAConflictAndKeepsTheLiveItems() {
        CompoundTag live = root("survival", 15);
        StoreMerge.Result result = StoreMerge.merge(live, root("survival", 6));

        assertEquals(List.of("survival"), result.conflicts());
        assertFalse(result.changedAnything());
        assertEquals(15, live.getList("survival", 10).size());
    }

    @Test
    void identicalSlotsAreNeitherMergedNorAConflict() {
        CompoundTag live = root("survival", 4);
        StoreMerge.Result result = StoreMerge.merge(live, root("survival", 4));

        assertFalse(result.changedAnything());
        assertFalse(result.hasConflicts());
    }

    @Test
    void anEmptySourceSlotChangesNothing() {
        CompoundTag live = root("survival", 4);
        assertFalse(StoreMerge.merge(live, root("survival", 0)).changedAnything());
        assertFalse(StoreMerge.merge(new CompoundTag(), root("survival", 0)).changedAnything());
    }

    @Test
    void sidecarCompoundsGainOnlyTheChildrenTheLiveFileLacks() {
        CompoundTag live = new CompoundTag();
        CompoundTag liveSidecar = new CompoundTag();
        liveSidecar.putString("pack-a", "live contents");
        live.put(BackpackContents.KEY, liveSidecar);

        CompoundTag source = new CompoundTag();
        CompoundTag sourceSidecar = new CompoundTag();
        sourceSidecar.putString("pack-a", "stale contents");
        sourceSidecar.putString("pack-b", "carried contents");
        source.put(BackpackContents.KEY, sourceSidecar);

        StoreMerge.Result result = StoreMerge.merge(live, source);

        assertTrue(result.changedAnything());
        CompoundTag merged = live.getCompound(BackpackContents.KEY);
        assertEquals("live contents", merged.getString("pack-a"));
        assertEquals("carried contents", merged.getString("pack-b"));
    }
}
