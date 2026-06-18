package games.brennan.enderchestpersistence;

import java.nio.file.Path;

/**
 * Loader-agnostic holder for the platform config directory path.
 * Each loader's entrypoint calls {@link #set(Path)} during mod initialization:
 * NeoForge and Forge use {@code FMLPaths.CONFIGDIR.get()};
 * Fabric uses {@code FabricLoader.getInstance().getConfigDir()}.
 */
public final class ConfigDir {

    private static volatile Path dir;

    private ConfigDir() {}

    public static void set(Path path) {
        dir = path;
    }

    public static Path get() {
        if (dir == null) {
            throw new IllegalStateException("[EnderChestPersistence] ConfigDir not set — loader entrypoint missing?");
        }
        return dir;
    }
}
