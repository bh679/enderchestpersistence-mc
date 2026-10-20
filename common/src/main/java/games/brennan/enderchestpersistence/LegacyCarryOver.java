package games.brennan.enderchestpersistence;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;

/**
 * Picks up a player's chest from a store directory an older build may have written
 * ({@link StoreLocation#legacyDirs}) the first time this build sees it.
 *
 * <p>The case this exists for: a launcher that redirects {@code APPDATA} (PCL does) made builds
 * before 0.7.0 keep the "machine-level" store inside that launcher's game folder. The player then
 * switched launcher and found an empty chest. Now the store resolves the same way everywhere, and
 * whatever the old build left behind is carried into it here.</p>
 *
 * <p>Rules, in order, per candidate directory holding {@code <uuid>.dat}:</p>
 * <ol>
 *   <li>If the live file is absent, copy the candidate (and its {@code .bak}) across: {@link Kind#COPIED}.</li>
 *   <li>Otherwise fold it in with {@link StoreMerge}. Anything moved: {@link Kind#MERGED}. A slot
 *       with items on both sides: {@link Kind#CONFLICT}, the live chest unchanged for that slot.</li>
 * </ol>
 *
 * <p>The candidate file itself is never modified. A marker ({@code <uuid>.dat.carried}) is written
 * beside it once it has been looked at, so the same file is not merged, or reported, on every login;
 * a candidate newer than its marker (an older build wrote to it again) is considered afresh.</p>
 */
public final class LegacyCarryOver {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String MARKER_SUFFIX = ".carried";

    private LegacyCarryOver() {}

    public enum Kind { NONE, COPIED, MERGED, CONFLICT }

    /** The outcome: {@code source} is the directory the chest came from, null for {@link Kind#NONE}. */
    public record Result(Kind kind, Path source, List<String> conflicts) {
        static final Result NONE = new Result(Kind.NONE, null, List.of());

        public boolean happened() {
            return kind != Kind.NONE;
        }
    }

    /**
     * Carry {@code uuid}'s chest from the first legacy directory that holds one into {@code destination}.
     *
     * @param destination the live file, {@code EnderChestStore.file(uuid)}
     * @param legacyDirs  directories to look in, in order; each may or may not exist
     */
    public static Result run(UUID uuid, Path destination, List<Path> legacyDirs) {
        if (destination == null || legacyDirs == null || legacyDirs.isEmpty()) return Result.NONE;
        String name = uuid + ".dat";
        for (Path dir : legacyDirs) {
            Path candidate = dir.resolve(name);
            if (candidate.equals(destination) || !Files.isRegularFile(candidate)) continue;
            if (alreadyLookedAt(candidate)) continue;
            Result result = carry(candidate, destination);
            if (result.happened()) {
                mark(candidate, destination);
                return result;
            }
        }
        return Result.NONE;
    }

    private static Result carry(Path candidate, Path destination) {
        if (!Files.exists(destination)) {
            if (!StoreSeeder.seedIfMissing(destination, candidate)) return Result.NONE;
            StoreSeeder.copyBackupAlongside(destination, candidate);
            return new Result(Kind.COPIED, candidate.getParent(), List.of());
        }
        StoreFile.Loaded live = StoreFile.read(destination);
        StoreFile.Loaded found = StoreFile.read(candidate);
        if (!live.hasTag() || !found.hasTag()) {
            // An unreadable live file is WriteGuard's problem; an unreadable candidate is not worth a message.
            return Result.NONE;
        }
        CompoundTag merged = live.tag().copy();
        StoreMerge.Result outcome = StoreMerge.merge(merged, found.tag());
        if (outcome.changedAnything() && !StoreFile.write(destination, merged)) {
            LOGGER.error("[EnderChestPersistence] could not write the chest merged from {} to {}",
                    candidate, destination);
            return Result.NONE;
        }
        if (outcome.hasConflicts()) {
            LOGGER.warn("[EnderChestPersistence] {} also holds a chest; slot(s) {} have items on both sides,"
                    + " so the live chest at {} kept its own. Merged the rest: {}.",
                    candidate, outcome.conflicts(), destination, outcome.mergedSlots());
            return new Result(Kind.CONFLICT, candidate.getParent(), outcome.conflicts());
        }
        if (outcome.changedAnything()) {
            LOGGER.info("[EnderChestPersistence] merged slot(s) {} from {} into {} — the original was left in place.",
                    outcome.mergedSlots(), candidate, destination);
            return new Result(Kind.MERGED, candidate.getParent(), List.of());
        }
        // Identical, or nothing the live chest lacked: remember that so it is not re-read every login.
        mark(candidate, destination);
        return Result.NONE;
    }

    static Path marker(Path candidate) {
        return candidate.resolveSibling(candidate.getFileName() + MARKER_SUFFIX);
    }

    /** True when a marker exists and is at least as new as the candidate it belongs to. */
    static boolean alreadyLookedAt(Path candidate) {
        Path marker = marker(candidate);
        if (!Files.isRegularFile(marker)) return false;
        try {
            FileTime candidateTime = Files.getLastModifiedTime(candidate);
            FileTime markerTime = Files.getLastModifiedTime(marker);
            return markerTime.compareTo(candidateTime) >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static void mark(Path candidate, Path destination) {
        try {
            Files.writeString(marker(candidate),
                    "Carried over to " + destination + System.lineSeparator()
                            + "This file only records that the chest beside it has been picked up by a newer"
                            + " build. Delete it to have that chest looked at again on the next login."
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[EnderChestPersistence] could not write {}: {} — the chest at {} will be checked"
                    + " again next login", marker(candidate), e.getMessage(), candidate);
        }
    }
}
