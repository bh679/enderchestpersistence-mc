package games.brennan.enderchestpersistence;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Sophisticated backpack stack holds only a UUID; its items live in the world. These tests walk
 * the reset scenario over plain NBT: capture from one world, restore into an empty one.
 */
class BackpackContentsTest {

    /** A world's backpack storage. */
    private static final class World implements BackpackContents.Storage {
        final Map<UUID, CompoundTag> map = new HashMap<>();

        @Override
        public CompoundTag get(UUID id) {
            return map.get(id);
        }

        @Override
        public void put(UUID id, CompoundTag contents) {
            map.put(id, contents);
        }
    }

    private static CompoundTag stack(String id) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", id);
        stack.putInt("count", 1);
        return stack;
    }

    private static CompoundTag backpack(UUID uuid) {
        CompoundTag stack = stack("sophisticatedbackpacks:backpack");
        CompoundTag components = new CompoundTag();
        components.put(BackpackContents.UUID_COMPONENT, new IntArrayTag(UUIDUtil.uuidToIntArray(uuid)));
        stack.put("components", components);
        return stack;
    }

    private static ListTag slots(CompoundTag... stacks) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            stacks[i].putByte("Slot", (byte) i);
            list.add(stacks[i]);
        }
        return list;
    }

    /** Backpack contents as SB would store them: an inventory list of stacks. */
    private static CompoundTag contents(CompoundTag... stacks) {
        CompoundTag inventory = new CompoundTag();
        inventory.put("Items", slots(stacks));
        CompoundTag tag = new CompoundTag();
        tag.put("inventory", inventory);
        return tag;
    }

    @Test
    void findsUuidsAtAnyDepth() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CompoundTag shulker = stack("minecraft:shulker_box");
        CompoundTag components = new CompoundTag();
        ListTag container = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("item", backpack(b));
        container.add(entry);
        components.put("minecraft:container", container);
        shulker.put("components", components);

        assertEquals(Set.of(a, b),
                BackpackContents.uuidsIn(slots(backpack(a), stack("minecraft:dirt"), shulker)));
    }

    @Test
    void contentsSurviveAWorldReset() {
        UUID id = UUID.randomUUID();
        World oldWorld = new World();
        oldWorld.put(id, contents(stack("minecraft:diamond")));
        ListTag items = slots(backpack(id));
        CompoundTag root = new CompoundTag();
        root.put("survival", items);

        assertTrue(BackpackContents.capture(root, items, oldWorld));

        World newWorld = new World();
        assertEquals(1, BackpackContents.inject(root, root.getList("survival", Tag.TAG_COMPOUND), newWorld));
        assertEquals(oldWorld.get(id), newWorld.get(id));
    }

    @Test
    void nestedBackpacksAreCarriedToo() {
        UUID outer = UUID.randomUUID();
        UUID inner = UUID.randomUUID();
        World oldWorld = new World();
        oldWorld.put(outer, contents(backpack(inner)));
        oldWorld.put(inner, contents(stack("minecraft:emerald")));
        ListTag items = slots(backpack(outer));
        CompoundTag root = new CompoundTag();
        root.put("survival", items);
        BackpackContents.capture(root, items, oldWorld);

        World newWorld = new World();
        assertEquals(2, BackpackContents.inject(root, items, newWorld));
        assertEquals(oldWorld.get(inner), newWorld.get(inner));
    }

    @Test
    void theWorldCopyIsNeverOverwritten() {
        UUID id = UUID.randomUUID();
        ListTag items = slots(backpack(id));
        CompoundTag root = new CompoundTag();
        root.put("survival", items);
        World before = new World();
        before.put(id, contents(stack("minecraft:diamond")));
        BackpackContents.capture(root, items, before);

        World world = new World();
        CompoundTag live = contents(stack("minecraft:dirt"));
        world.put(id, live);
        assertEquals(0, BackpackContents.inject(root, items, world));
        assertEquals(live, world.get(id));
    }

    @Test
    void unchangedContentsAreNotAChange() {
        UUID id = UUID.randomUUID();
        World world = new World();
        world.put(id, contents(stack("minecraft:diamond")));
        ListTag items = slots(backpack(id));
        CompoundTag root = new CompoundTag();
        root.put("survival", items);
        assertTrue(BackpackContents.capture(root, items, world));
        assertFalse(BackpackContents.capture(root, items, world));

        world.put(id, contents(stack("minecraft:dirt")));
        assertTrue(BackpackContents.capture(root, items, world), "a backpack's items changed, its stack did not");
    }

    @Test
    void unknownToTheWorldKeepsTheStoredCopy() {
        UUID id = UUID.randomUUID();
        World world = new World();
        world.put(id, contents(stack("minecraft:diamond")));
        ListTag items = slots(backpack(id));
        CompoundTag root = new CompoundTag();
        root.put("survival", items);
        BackpackContents.capture(root, items, world);

        assertFalse(BackpackContents.capture(root, items, BackpackContents.NONE));
        assertEquals(world.get(id), root.getCompound(BackpackContents.KEY).getCompound(id.toString()));
    }

    @Test
    void backpacksInOtherSlotsAreKeptAndRemovedOnesPruned() {
        UUID creative = UUID.randomUUID();
        UUID survival = UUID.randomUUID();
        World world = new World();
        world.put(creative, contents(stack("minecraft:stone")));
        world.put(survival, contents(stack("minecraft:diamond")));
        CompoundTag root = new CompoundTag();
        ListTag creativeItems = slots(backpack(creative));
        root.put("creative", creativeItems);
        BackpackContents.capture(root, creativeItems, world);

        ListTag survivalItems = slots(backpack(survival));
        root.put("survival", survivalItems);
        World onlySurvival = new World();
        onlySurvival.put(survival, world.get(survival));
        BackpackContents.capture(root, survivalItems, onlySurvival);
        CompoundTag sidecar = root.getCompound(BackpackContents.KEY);
        assertTrue(sidecar.contains(creative.toString()), "another slot's backpack survives");
        assertTrue(sidecar.contains(survival.toString()));

        ListTag emptied = new ListTag();
        root.put("survival", emptied);
        assertTrue(BackpackContents.capture(root, emptied, world));
        assertFalse(root.getCompound(BackpackContents.KEY).contains(survival.toString()));

        root.put("creative", new ListTag());
        BackpackContents.capture(root, new ListTag(), world);
        assertFalse(root.contains(BackpackContents.KEY), "no backpacks left, no side compound");
    }

    @Test
    void sideCompoundIsNotCountedAsItems() {
        UUID id = UUID.randomUUID();
        World world = new World();
        world.put(id, contents(stack("minecraft:diamond"), stack("minecraft:emerald")));
        ListTag items = slots(backpack(id));
        CompoundTag root = new CompoundTag();
        root.put("survival", items);
        BackpackContents.capture(root, items, world);
        assertEquals(1, StoreFile.itemCount(root));
    }

    @Test
    void noBackpacksLeavesRootUntouched() {
        ListTag items = slots(stack("minecraft:dirt"));
        CompoundTag root = new CompoundTag();
        root.put("survival", items);
        assertFalse(BackpackContents.capture(root, items, new World()));
        assertFalse(root.contains(BackpackContents.KEY));
        assertEquals(0, BackpackContents.inject(root, items, new World()));
        assertNull(new World().get(UUID.randomUUID()));
    }
}
