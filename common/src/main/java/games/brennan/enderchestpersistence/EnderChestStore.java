package games.brennan.enderchestpersistence;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-player, per-game-mode Ender Chest contents that persist outside any
 * individual world save, as {@code <uuid>.dat} under the directory
 * {@link StoreLocation} resolves — the machine's application-data folder by
 * default, or this instance's config folder. See {@link StoreMode}.
 *
 * <p>Separate inventories are maintained per {@link GameType}: survival,
 * creative, adventure, and spectator each have their own 27-slot snapshot.
 * Switching game modes triggers an immediate swap via {@link #swapGameMode},
 * so the live {@link PlayerEnderChestContainer} always reflects the current
 * mode.</p>
 *
 * <p>An external mod can override the slot a player maps to — independent of
 * game mode — via {@link #registerSlotProvider} (passive) and
 * {@link #refreshSlot} (an immediate mid-session swap). This lets a host mod
 * lock a "cheated"/Free-Play run onto a separate slot so it never touches the
 * player's legit chest.</p>
 *
 * <p>Every entry point is a no-op under {@link StoreMode#OFF}, which leaves the Ender Chest to
 * vanilla. That includes the slot swaps, so a host mod's chest isolation stops isolating — the
 * config file says as much where a player will read it.</p>
 */
public final class EnderChestStore {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** In-memory cache: UUID → root tag (null = nothing ever saved). */
    private static final Map<UUID, CompoundTag> CACHE = new ConcurrentHashMap<>();

    /**
     * UUID → the slot key currently materialized in the player's live ender
     * chest. Set on {@link #restore}, updated on every swap. Lets the store
     * snapshot the live contents back to the right slot even when a provider
     * has overridden the slot away from the player's actual game mode.
     */
    private static final Map<UUID, String> APPLIED = new ConcurrentHashMap<>();

    /** External slot-key overrides, consulted in registration order. */
    private static final List<SlotKeyProvider> SLOT_PROVIDERS = new CopyOnWriteArrayList<>();

    private EnderChestStore() {}

    /**
     * Lets another mod override which slot a player's ender chest maps to,
     * independent of their game mode — e.g. lock a "cheated"/Free-Play run onto
     * a disposable slot so it can't read or write the player's legit chest.
     * Register via {@link #registerSlotProvider}; trigger an immediate live swap
     * with {@link #refreshSlot} when the verdict changes mid-session.
     */
    @FunctionalInterface
    public interface SlotKeyProvider {
        /**
         * @param player     the online player whose ender-chest slot is resolving
         * @param defaultKey the key in effect so far (the game-mode key, or the
         *                   running result of earlier providers)
         * @return an override slot key, or {@code null} to defer (keep {@code defaultKey})
         */
        String overrideSlotKey(ServerPlayer player, String defaultKey);
    }

    /** Register a slot-key override provider. Thread-safe. */
    public static void registerSlotProvider(SlotKeyProvider provider) {
        SLOT_PROVIDERS.add(provider);
    }

    /** The store file for {@code uuid}, in whichever directory the configured mode resolved to. */
    public static Path file(UUID uuid) {
        return StoreLocation.dir().resolve(fileName(uuid));
    }

    /** The same file as it would sit in this instance's config folder, whether or not that is in use. */
    private static Path instanceFile(UUID uuid) {
        return StoreLocation.instanceDir().resolve(fileName(uuid));
    }

    private static String fileName(UUID uuid) {
        return uuid + ".dat";
    }

    // ---- Public API ----

    /**
     * Snapshot {@code player}'s Ender Chest for their current game mode into
     * the cache and write it to disk. Other game-mode slots are preserved.
     * Called on player logout.
     */
    public static void save(ServerPlayer player) {
        if (!StoreLocation.enabled()) return;
        UUID uuid = player.getUUID();
        // Write to the slot the live chest actually represents (APPLIED), which may
        // differ from the game-mode slot when a provider has locked the player.
        String key = APPLIED.getOrDefault(uuid, currentKey(player));
        ListTag items = player.getEnderChestInventory().createTag(player.registryAccess());
        updateCache(uuid, key, items);
        LOGGER.debug("[EnderChestPersistence] saved {} item stack(s) for {} ({})",
                items.size(), player.getName().getString(), key);
    }

    /**
     * Apply the stored Ender Chest contents for the player's current game mode.
     * No-op if nothing has been stored for this UUID and game mode.
     * Called on every player login.
     */
    public static void restore(ServerPlayer player) {
        if (!StoreLocation.enabled()) return;
        UUID uuid = player.getUUID();
        String key = currentKey(player);
        CompoundTag root = CACHE.computeIfAbsent(uuid, EnderChestStore::loadFromDisk);
        // Record the slot now in effect even when nothing is stored for it (the
        // freshly-constructed live chest is empty, i.e. it already represents this slot).
        APPLIED.put(uuid, key);
        if (root == null || !root.contains(key, Tag.TAG_LIST)) return;
        ListTag items = root.getList(key, Tag.TAG_COMPOUND);
        player.getEnderChestInventory().fromTag(items, player.registryAccess());
        if (!items.isEmpty()) {
            LOGGER.info("[EnderChestPersistence] restored {} item stack(s) for {} ({})",
                    items.size(), player.getName().getString(), key);
        }
    }

    /**
     * Save the current game mode's Ender Chest, then load the new game mode's
     * Ender Chest into the player's live {@link PlayerEnderChestContainer}.
     * The old mode is still active at call time; {@code newMode} is the mode
     * the player is switching to.
     */
    public static void swapGameMode(ServerPlayer player, GameType newMode) {
        if (!StoreLocation.enabled()) return;
        UUID uuid = player.getUUID();
        String oldKey = APPLIED.getOrDefault(uuid, currentMode(player));
        String newKey = resolveKey(player, newMode.getSerializedName());
        if (oldKey.equals(newKey)) {       // e.g. a locked run stays on its slot
            APPLIED.put(uuid, newKey);
            return;
        }
        applySlot(player, oldKey, newKey);
        LOGGER.info("[EnderChestPersistence] swapped ender chest {} → {} for {}",
                oldKey, newKey, player.getName().getString());
    }

    /**
     * Re-evaluate the slot providers for an online player and, if the effective
     * slot has changed since it was last materialized, snapshot the live chest
     * back to its current slot and load the new slot into the live container.
     * Idempotent — a no-op when the slot is unchanged.
     *
     * <p>Call this when a provider's verdict flips mid-session (e.g. a run
     * becomes locked while the player is logged in), so the live chest is swapped
     * immediately rather than only on the next login / game-mode change.</p>
     */
    public static void refreshSlot(ServerPlayer player) {
        if (!StoreLocation.enabled()) return;
        UUID uuid = player.getUUID();
        String oldKey = APPLIED.getOrDefault(uuid, currentMode(player));
        String newKey = currentKey(player);
        if (oldKey.equals(newKey)) {
            APPLIED.put(uuid, newKey);
            return;
        }
        applySlot(player, oldKey, newKey);
        LOGGER.info("[EnderChestPersistence] locked ender chest {} → {} for {}",
                oldKey, newKey, player.getName().getString());
    }

    /**
     * Snapshot the live chest into {@code oldKey}, then load {@code newKey} into
     * the live container (clearing it if nothing is stored for {@code newKey}).
     * Updates {@link #APPLIED} to {@code newKey}.
     */
    private static void applySlot(ServerPlayer player, String oldKey, String newKey) {
        UUID uuid = player.getUUID();
        ListTag oldItems = player.getEnderChestInventory().createTag(player.registryAccess());
        CompoundTag root = CACHE.compute(uuid, (k, existing) -> {
            CompoundTag r = existing != null ? existing : loadFromDisk(k);
            if (r == null) r = new CompoundTag();
            r.put(oldKey, oldItems);
            return r;
        });

        PlayerEnderChestContainer enderChest = player.getEnderChestInventory();
        if (root.contains(newKey, Tag.TAG_LIST)) {
            enderChest.fromTag(root.getList(newKey, Tag.TAG_COMPOUND), player.registryAccess());
        } else {
            enderChest.clearContent();
        }
        APPLIED.put(uuid, newKey);
    }

    /** Write the cached entry for {@code uuid} to disk. No-op if not in cache. */
    public static void flush(UUID uuid) {
        if (!StoreLocation.enabled()) return;
        CompoundTag tag = CACHE.get(uuid);
        if (tag == null) return;
        saveToDisk(uuid, tag);
    }

    /** Drop the cached entry. Used on logout after {@link #flush}. */
    public static void evict(UUID uuid) {
        CACHE.remove(uuid);
        APPLIED.remove(uuid);
    }

    /** Flush every cached player. Called on server stop. */
    public static void flushAll() {
        if (!StoreLocation.enabled()) return;
        Map<UUID, CompoundTag> snapshot = new HashMap<>(CACHE);
        for (var entry : snapshot.entrySet()) {
            saveToDisk(entry.getKey(), entry.getValue());
        }
    }

    // ---- Internals ----

    private static String currentMode(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer().getSerializedName();
    }

    /** The game-mode slot key after applying any registered provider overrides. */
    private static String currentKey(ServerPlayer player) {
        return resolveKey(player, currentMode(player));
    }

    /** Fold the registered providers over {@code defaultKey}; non-null overrides win. */
    private static String resolveKey(ServerPlayer player, String defaultKey) {
        String key = defaultKey;
        for (SlotKeyProvider provider : SLOT_PROVIDERS) {
            String override = provider.overrideSlotKey(player, key);
            if (override != null) key = override;
        }
        return key;
    }

    private static void updateCache(UUID uuid, String modeKey, ListTag items) {
        CACHE.compute(uuid, (k, existing) -> {
            CompoundTag root = existing != null ? existing : loadFromDisk(k);
            if (root == null) root = new CompoundTag();
            root.put(modeKey, items);
            return root;
        });
    }

    /**
     * On the first read after the store moved out of the instance, carry the old file across rather
     * than reporting an empty chest — precisely the failure {@link StoreMode#OUTSIDE} exists to
     * prevent. No-op in every other mode, so neither {@code inside} nor {@code off} can move a
     * player's data unasked.
     *
     * @return true if a file was carried across
     */
    static boolean seedFromInstanceIfNeeded(UUID uuid) {
        if (StoreLocation.mode() != StoreMode.OUTSIDE) return false;
        return StoreSeeder.seedIfMissing(file(uuid), instanceFile(uuid));
    }

    private static CompoundTag loadFromDisk(UUID uuid) {
        seedFromInstanceIfNeeded(uuid);
        Path path = file(uuid);
        if (!Files.isRegularFile(path)) return null;
        try {
            return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            LOGGER.warn("[EnderChestPersistence] I/O error reading {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static synchronized void saveToDisk(UUID uuid, CompoundTag tag) {
        Path path = file(uuid);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            LOGGER.error("[EnderChestPersistence] failed to create dir {}: {}", path.getParent(), e.getMessage());
            return;
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(tag, tmp);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                LOGGER.error("[EnderChestPersistence] rename {} -> {} failed: {}", tmp, path, e2.getMessage());
            }
        }
    }
}
