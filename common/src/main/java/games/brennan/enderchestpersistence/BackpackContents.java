package games.brennan.enderchestpersistence;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Carries the contents of storage items that keep their inventory <i>in the world</i> rather than
 * on the stack — Sophisticated Backpacks — alongside the Ender Chest file.
 *
 * <p>A Sophisticated backpack stack holds only a UUID ({@code sophisticatedcore:storage_uuid}); its
 * items live in the world's {@code data/sophisticatedbackpacks.dat}. Persisting the stack alone
 * therefore brought an <i>empty</i> backpack into the next world. So every time a slot is saved,
 * the contents of each backpack in it (and of backpacks nested inside those) are copied into a
 * side compound under {@link #KEY} in the root tag, and on restore they are handed back to the new
 * world for any backpack that world does not already know.</p>
 *
 * <p>The world copy wins whenever it exists: it is what the backpack's open container is editing,
 * and overwriting it would roll back or duplicate items. Entries no slot references any more are
 * dropped on the next save, so the file does not grow with every backpack ever stashed.</p>
 *
 * <p>Pure NBT logic against a {@link Storage}; the live world is reached through
 * {@link SophisticatedBackpacksBridge}, so every branch here is unit-testable.</p>
 */
public final class BackpackContents {

    /** Root-tag key of the side compound: backpack UUID string → contents compound. */
    static final String KEY = "sophisticatedbackpacks";

    /** The data component holding a Sophisticated storage item's UUID. */
    static final String UUID_COMPONENT = "sophisticatedcore:storage_uuid";

    private BackpackContents() {}

    /** Where backpack contents live in the running world. */
    public interface Storage {
        /** @return the contents stored for {@code id}, or null if this world has none */
        CompoundTag get(UUID id);

        /** Give the world {@code contents} for {@code id}. */
        void put(UUID id, CompoundTag contents);
    }

    /** No backpack mod loaded: nothing to read, nowhere to write. */
    public static final Storage NONE = new Storage() {
        @Override
        public CompoundTag get(UUID id) {
            return null;
        }

        @Override
        public void put(UUID id, CompoundTag contents) {}
    };

    /**
     * Refresh {@code root}'s side compound from {@code world} for every backpack in
     * {@code liveItems}, then drop entries no slot in {@code root} references. Call after the slot
     * list has been put into {@code root}. A backpack the world has no contents for keeps whatever
     * the file already held — that is the copy that is about to be restored.
     *
     * @return true if {@code root} was modified
     */
    public static boolean capture(CompoundTag root, ListTag liveItems, Storage world) {
        CompoundTag sidecar = root.getCompound(KEY);
        boolean changed = false;
        Deque<UUID> queue = new ArrayDeque<>(uuidsIn(liveItems));
        Set<UUID> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            if (!seen.add(id)) continue;
            String key = id.toString();
            CompoundTag fromWorld = world.get(id);
            if (fromWorld != null && !fromWorld.isEmpty() && !fromWorld.equals(sidecar.get(key))) {
                sidecar.put(key, fromWorld.copy());
                changed = true;
            }
            if (sidecar.contains(key, Tag.TAG_COMPOUND)) {
                queue.addAll(uuidsIn(sidecar.getCompound(key)));
            }
        }
        changed |= prune(root, sidecar);
        if (sidecar.isEmpty()) {
            root.remove(KEY);
        } else {
            root.put(KEY, sidecar);
        }
        return changed;
    }

    /**
     * Hand the world the stored contents of every backpack in {@code items} (and nested inside
     * them) that it has no contents for. Call before the items are loaded into the live chest.
     *
     * @return how many backpacks were given contents
     */
    public static int inject(CompoundTag root, ListTag items, Storage world) {
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) return 0;
        CompoundTag sidecar = root.getCompound(KEY);
        int injected = 0;
        Deque<UUID> queue = new ArrayDeque<>(uuidsIn(items));
        Set<UUID> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            if (!seen.add(id)) continue;
            String key = id.toString();
            if (!sidecar.contains(key, Tag.TAG_COMPOUND)) continue;
            CompoundTag stored = sidecar.getCompound(key);
            CompoundTag inWorld = world.get(id);
            if (inWorld == null || inWorld.isEmpty()) {
                world.put(id, stored.copy());
                injected++;
            }
            queue.addAll(uuidsIn(stored));
        }
        return injected;
    }

    /** Remove side entries not reachable from any slot list in {@code root}. */
    private static boolean prune(CompoundTag root, CompoundTag sidecar) {
        if (sidecar.isEmpty()) return false;
        Deque<UUID> queue = new ArrayDeque<>();
        for (String key : root.getAllKeys()) {
            if (root.contains(key, Tag.TAG_LIST)) queue.addAll(uuidsIn(root.get(key)));
        }
        Set<String> reachable = new HashSet<>();
        while (!queue.isEmpty()) {
            String key = queue.poll().toString();
            if (!reachable.add(key)) continue;
            if (sidecar.contains(key, Tag.TAG_COMPOUND)) queue.addAll(uuidsIn(sidecar.getCompound(key)));
        }
        boolean changed = false;
        for (String key : Set.copyOf(sidecar.getAllKeys())) {
            if (!reachable.contains(key)) {
                sidecar.remove(key);
                changed = true;
            }
        }
        return changed;
    }

    /** Every storage UUID anywhere under {@code tag} — including inside shulker boxes and bundles. */
    static Set<UUID> uuidsIn(Tag tag) {
        Set<UUID> out = new LinkedHashSet<>();
        collect(tag, out);
        return out;
    }

    private static void collect(Tag tag, Set<UUID> out) {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (UUID_COMPONENT.equals(key) && child instanceof IntArrayTag array
                        && array.getAsIntArray().length == 4) {
                    out.add(UUIDUtil.uuidFromIntArray(array.getAsIntArray()));
                } else {
                    collect(child, out);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) collect(child, out);
        }
    }
}
