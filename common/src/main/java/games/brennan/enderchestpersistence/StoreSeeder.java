package games.brennan.enderchestpersistence;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Carries an existing instance-local chest into the machine-level store the first time a player logs
 * in after the store moves outside the instance.
 *
 * <p>Without this, shipping {@link StoreMode#OUTSIDE} as the default would inflict on every existing
 * player exactly the bug it was written to fix: the store would start looking somewhere new, find
 * nothing there, and hand back an empty chest.</p>
 *
 * <p>Two rules make it safe. It only ever seeds a destination that <em>does not exist</em>, so it can
 * never overwrite a live stash with a stale one. And it <em>copies</em>, leaving the instance-local
 * file untouched, so switching back to {@code inside} still finds the chest and a mistake here is
 * recoverable by hand.</p>
 */
public final class StoreSeeder {

    private static final Logger LOGGER = LogUtils.getLogger();

    private StoreSeeder() {}

    /**
     * Copy {@code source} to {@code destination} if, and only if, the destination is absent and the
     * source is a readable file.
     *
     * @return true if a copy was made
     */
    public static boolean seedIfMissing(Path destination, Path source) {
        if (destination == null || source == null || destination.equals(source)) {
            return false;
        }
        if (Files.exists(destination) || !Files.isRegularFile(source)) {
            return false;
        }
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            LOGGER.error("[EnderChestPersistence] could not carry {} over to {}: {}",
                    source, destination, e.getMessage());
            return false;
        }
        LOGGER.info("[EnderChestPersistence] carried your existing Ender Chest from {} to {}"
                + " — the original was left in place.", source, destination);
        return true;
    }
}
