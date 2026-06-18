package games.brennan.enderchestpersistence.fabric;

import games.brennan.enderchestpersistence.ConfigDir;
import games.brennan.enderchestpersistence.EnderChestStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entrypoint. Sets the config directory and registers server-side
 * lifecycle events.
 *
 * <p>Game-mode changes are handled by {@code GameModeChangeMixin} because
 * Fabric API 0.103.x (1.21.1) does not expose a native game-mode-change
 * event.</p>
 */
public final class EnderChestPersistenceFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigDir.set(FabricLoader.getInstance().getConfigDir());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            EnderChestStore.restore(handler.player)
        );

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.player;
            var uuid = player.getUUID();
            EnderChestStore.save(player);
            EnderChestStore.flush(uuid);
            EnderChestStore.evict(uuid);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
            EnderChestStore.flushAll()
        );
    }
}
