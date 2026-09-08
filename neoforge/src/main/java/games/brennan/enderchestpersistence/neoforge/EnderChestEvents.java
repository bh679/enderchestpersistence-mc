package games.brennan.enderchestpersistence.neoforge;

import games.brennan.enderchestpersistence.EnderChestStore;
import games.brennan.enderchestpersistence.StoreLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.UUID;

/**
 * NeoForge event hooks that drive the Ender Chest persistence lifecycle.
 * All four hooks delegate to the loader-agnostic {@link EnderChestStore}.
 */
@EventBusSubscriber(modid = EnderChestPersistenceNeoForge.MOD_ID)
public final class EnderChestEvents {

    private EnderChestEvents() {}

    /**
     * Resolve the store directory before anyone can log in — {@link StoreLocation} needs to know
     * whether this is a dedicated server, which only the running server can say.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        StoreLocation.init(event.getServer().isDedicatedServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EnderChestStore.restore(player);
    }

    /**
     * Runs at LOW priority so any higher-priority cancellations are respected
     * before the swap occurs.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EnderChestStore.swapGameMode(player, event.getNewGameMode());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        EnderChestStore.save(player);
        EnderChestStore.flush(uuid);
        EnderChestStore.evict(uuid);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EnderChestStore.flushAll();
    }
}
