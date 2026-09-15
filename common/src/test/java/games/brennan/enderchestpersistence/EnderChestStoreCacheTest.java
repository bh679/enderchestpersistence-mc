package games.brennan.enderchestpersistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnderChestStore#putIfChanged} is what makes a quiet autosave free: it must report
 * "unchanged" for an identical snapshot and "changed" for anything else, without touching
 * the other slots.
 */
class EnderChestStoreCacheTest {

    private static ListTag stacks(int n, String id) {
        ListTag list = new ListTag();
        for (int i = 0; i < n; i++) {
            CompoundTag stack = new CompoundTag();
            stack.putByte("Slot", (byte) i);
            stack.putString("id", id);
            stack.putInt("count", 1);
            list.add(stack);
        }
        return list;
    }

    @Test
    void identicalSnapshotIsNotAChange() {
        CompoundTag root = new CompoundTag();
        root.put("survival", stacks(3, "minecraft:diamond"));
        assertFalse(EnderChestStore.putIfChanged(root, "survival", stacks(3, "minecraft:diamond")));
        assertEquals(3, root.getList("survival", Tag.TAG_COMPOUND).size());
    }

    @Test
    void differentContentsIsAChange() {
        CompoundTag root = new CompoundTag();
        root.put("survival", stacks(3, "minecraft:diamond"));
        assertTrue(EnderChestStore.putIfChanged(root, "survival", stacks(3, "minecraft:dirt")));
        assertEquals("minecraft:dirt",
                root.getList("survival", Tag.TAG_COMPOUND).getCompound(0).getString("id"));
    }

    @Test
    void newSlotIsAChangeAndLeavesOthersAlone() {
        CompoundTag root = new CompoundTag();
        root.put("survival", stacks(3, "minecraft:diamond"));
        assertTrue(EnderChestStore.putIfChanged(root, "creative", stacks(1, "minecraft:stone")));
        assertEquals(3, root.getList("survival", Tag.TAG_COMPOUND).size());
        assertEquals(1, root.getList("creative", Tag.TAG_COMPOUND).size());
    }

    @Test
    void emptyingTheChestIsAChange() {
        CompoundTag root = new CompoundTag();
        root.put("survival", stacks(3, "minecraft:diamond"));
        assertTrue(EnderChestStore.putIfChanged(root, "survival", new ListTag()));
        assertEquals(0, root.getList("survival", Tag.TAG_COMPOUND).size());
    }
}
