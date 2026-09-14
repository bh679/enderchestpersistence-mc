package games.brennan.enderchestpersistence;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players whose stored Ender Chest file must not be written this session.
 *
 * <p>A UUID is blocked when its file <em>exists but could not be read</em> at login (see
 * {@link StoreFile.Outcome#UNREADABLE}). The live chest then never held the stored items, so a
 * logout write would replace a stash the player still owns with an empty one — the exact loss this
 * mod exists to prevent. Every write path checks here first; the block lifts on logout
 * ({@link EnderChestStore#evict}), so the next login gets a fresh read.</p>
 *
 * <p>Deliberately narrow: a player who empties their chest and logs out is <em>not</em> blocked —
 * refusing that write would hand the items back on the next login, a duplication glitch.</p>
 */
public final class WriteGuard {

    private static final Set<UUID> BLOCKED = ConcurrentHashMap.newKeySet();

    private WriteGuard() {}

    /** Refuse every write for {@code uuid} until {@link #clear}. */
    public static void block(UUID uuid) {
        if (uuid != null) BLOCKED.add(uuid);
    }

    /** Lift the block, if any. */
    public static void clear(UUID uuid) {
        if (uuid != null) BLOCKED.remove(uuid);
    }

    public static boolean isBlocked(UUID uuid) {
        return uuid != null && BLOCKED.contains(uuid);
    }

    /** Test seam. */
    static void reset() {
        BLOCKED.clear();
    }
}
