package games.brennan.enderchestpersistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Folds a chest file found somewhere else ({@code source}) into the live one ({@code destination}),
 * slot by slot, without ever discarding anything the live chest holds.
 *
 * <ul>
 *   <li>A slot the live chest lacks, or holds empty, takes the source's contents.</li>
 *   <li>A slot with items on both sides is a <em>conflict</em>: the live chest keeps its items and
 *       the slot is reported, so the player can be told to reconcile by hand.</li>
 *   <li>Sidecar compounds (backpack contents, {@link BackpackContents#KEY}) gain any child the live
 *       file lacks, so a merged slot's backpacks arrive with their items.</li>
 * </ul>
 *
 * <p>Pure: it edits {@code destination} in memory and answers what it did. Reading and writing the
 * files, and deciding whether to run at all, is {@link LegacyCarryOver}'s job.</p>
 */
public final class StoreMerge {

    private StoreMerge() {}

    /** What a merge did. {@code conflicts} names the slots both sides had items in. */
    public record Result(List<String> mergedSlots, List<String> conflicts) {
        public boolean changedAnything() {
            return !mergedSlots.isEmpty();
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }
    }

    /** Merge {@code source} into {@code destination} (edited in place). Neither may be null. */
    public static Result merge(CompoundTag destination, CompoundTag source) {
        List<String> merged = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (String key : source.getAllKeys()) {
            if (source.contains(key, Tag.TAG_LIST)) {
                mergeSlot(destination, source, key, merged, conflicts);
            } else if (source.contains(key, Tag.TAG_COMPOUND)) {
                mergeSidecar(destination, source.getCompound(key), key, merged);
            }
        }
        return new Result(List.copyOf(merged), List.copyOf(conflicts));
    }

    private static void mergeSlot(CompoundTag destination, CompoundTag source, String key,
                                  List<String> merged, List<String> conflicts) {
        ListTag incoming = source.getList(key, Tag.TAG_COMPOUND);
        if (incoming.isEmpty()) return;
        boolean liveHasItems = destination.contains(key, Tag.TAG_LIST)
                && !destination.getList(key, Tag.TAG_COMPOUND).isEmpty();
        if (liveHasItems) {
            if (!incoming.equals(destination.get(key))) conflicts.add(key);
            return;
        }
        destination.put(key, incoming.copy());
        merged.add(key);
    }

    private static void mergeSidecar(CompoundTag destination, CompoundTag incoming, String key,
                                     List<String> merged) {
        CompoundTag live = destination.contains(key, Tag.TAG_COMPOUND)
                ? destination.getCompound(key) : new CompoundTag();
        boolean added = false;
        for (String child : incoming.getAllKeys()) {
            if (!live.contains(child)) {
                live.put(child, incoming.get(child).copy());
                added = true;
            }
        }
        if (added) {
            destination.put(key, live);
            merged.add(key);
        }
    }
}
