package games.brennan.enderchestpersistence;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Reads and writes one player's {@code <uuid>.dat}, with the safety net around it.
 *
 * <p>Two guarantees, both zero-config:</p>
 * <ul>
 *   <li><b>{@code <uuid>.dat.bak} always holds the last version that contained items.</b> It is
 *       rotated only when the file being replaced has at least one item in any slot, so a loss
 *       followed by any number of empty sessions can never overwrite the good copy. Restore by hand:
 *       close the game, rename {@code .dat.bak} to {@code .dat}.</li>
 *   <li><b>An unreadable file is never written over.</b> If the main file cannot be parsed, the
 *       {@code .bak} is loaded instead and the bad file is set aside as
 *       {@code <uuid>.dat.corrupt-<timestamp>}. If there is no usable {@code .bak} either, the read
 *       reports {@link Outcome#UNREADABLE} and the caller blocks writes for the session
 *       ({@link WriteGuard}).</li>
 * </ul>
 *
 * <p>Pure file logic — no player, no server — so every branch runs under a {@code @TempDir}.</p>
 */
public final class StoreFile {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String BAK_SUFFIX = ".bak";
    static final String CORRUPT_SUFFIX = ".corrupt-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Reads that fail with an I/O error are retried this many more times before giving up. */
    static final int READ_RETRIES = 2;
    /** Pause between retries — long enough for a Windows sharing violation to clear. */
    static final long RETRY_PAUSE_MS = 75;

    private StoreFile() {}

    public enum Outcome {
        /** No file — a new player, or nothing ever saved. */
        ABSENT,
        LOADED,
        /** The main file was unreadable; the {@code .bak} was loaded and the main file set aside. */
        LOADED_FROM_BACKUP,
        /** The main file is unreadable and no usable backup exists. It has been left untouched. */
        UNREADABLE
    }

    /** {@code tag} is null for {@link Outcome#ABSENT} and {@link Outcome#UNREADABLE}. */
    public record Loaded(Outcome outcome, CompoundTag tag) {
        public boolean hasTag() {
            return tag != null;
        }
    }

    public static Path bak(Path file) {
        return file.resolveSibling(file.getFileName() + BAK_SUFFIX);
    }

    /** Read {@code file}, falling back to its backup. Never throws. */
    public static Loaded read(Path file) {
        if (!Files.isRegularFile(file)) {
            return new Loaded(Outcome.ABSENT, null);
        }
        CompoundTag tag = readWithRetries(file);
        if (tag != null) {
            return new Loaded(Outcome.LOADED, tag);
        }
        return recoverFromBackup(file);
    }

    private static Loaded recoverFromBackup(Path file) {
        Path bak = bak(file);
        CompoundTag fromBak = Files.isRegularFile(bak) ? readWithRetries(bak) : null;
        if (fromBak == null) {
            LOGGER.error("[EnderChestPersistence] {} is unreadable and there is no usable backup at {}"
                    + " — leaving it untouched; nothing will be written over it this session.", file, bak);
            return new Loaded(Outcome.UNREADABLE, null);
        }
        Path corrupt = file.resolveSibling(file.getFileName() + CORRUPT_SUFFIX + LocalDateTime.now().format(STAMP));
        try {
            Files.move(file, corrupt);
            Files.copy(bak, file, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            LOGGER.error("[EnderChestPersistence] {} is unreadable and could not be replaced by its backup"
                    + " ({}) — leaving it untouched; nothing will be written over it this session.",
                    file, e.getMessage());
            return new Loaded(Outcome.UNREADABLE, null);
        }
        LOGGER.error("[EnderChestPersistence] {} was unreadable — restored {} item stack(s) from {}."
                + " The bad file was kept as {}.", file, itemCount(fromBak), bak, corrupt.getFileName());
        return new Loaded(Outcome.LOADED_FROM_BACKUP, fromBak);
    }

    private static CompoundTag readWithRetries(Path path) {
        IOException last = null;
        for (int attempt = 0; attempt <= READ_RETRIES; attempt++) {
            try {
                return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                last = e;
                if (attempt < READ_RETRIES) pause();
            }
        }
        LOGGER.warn("[EnderChestPersistence] I/O error reading {} after {} attempts: {}",
                path, READ_RETRIES + 1, last == null ? "?" : last.getMessage());
        return null;
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Write {@code tag} to {@code file} atomically, first rotating the existing file into
     * {@code .bak} when it holds at least one item.
     *
     * @return true if the file was written
     */
    public static boolean write(Path file, CompoundTag tag) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOGGER.error("[EnderChestPersistence] failed to create dir {}: {}", file.getParent(), e.getMessage());
            return false;
        }
        rotateBackup(file);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(tag, tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException e2) {
                LOGGER.error("[EnderChestPersistence] rename {} -> {} failed: {}", tmp, file, e2.getMessage());
                return false;
            }
        }
    }

    /** Copy {@code file} over {@code .bak} if it currently holds items. Best-effort. */
    private static void rotateBackup(Path file) {
        if (!Files.isRegularFile(file)) return;
        CompoundTag existing;
        try {
            existing = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            // Unreadable on the way out: keep whatever .bak we have rather than replacing it with junk.
            return;
        }
        if (itemCount(existing) == 0) return;
        try {
            Files.copy(file, bak(file), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            LOGGER.warn("[EnderChestPersistence] could not update backup {}: {}", bak(file), e.getMessage());
        }
    }

    /** Total item stacks across every slot list in {@code root}. */
    public static int itemCount(CompoundTag root) {
        if (root == null) return 0;
        int count = 0;
        for (String key : root.getAllKeys()) {
            if (root.contains(key, Tag.TAG_LIST)) {
                count += root.getList(key, Tag.TAG_COMPOUND).size();
            }
        }
        return count;
    }
}
