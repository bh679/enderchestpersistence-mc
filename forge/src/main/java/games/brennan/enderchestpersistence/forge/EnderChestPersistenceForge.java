package games.brennan.enderchestpersistence.forge;

import games.brennan.enderchestpersistence.ConfigDir;
import games.brennan.enderchestpersistence.EnderChestStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.UUID;

@Mod("enderchestpersistence")
public final class EnderChestPersistenceForge {

    public EnderChestPersistenceForge(IEventBus modBus) {
        ConfigDir.set(FMLPaths.CONFIGDIR.get());

        MinecraftForge.EVENT_BUS.addListener(EnderChestPersistenceForge::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, EnderChestPersistenceForge::onPlayerChangeGameMode);
        MinecraftForge.EVENT_BUS.addListener(EnderChestPersistenceForge::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(EnderChestPersistenceForge::onServerStopping);
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EnderChestStore.restore(player);
    }

    private static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        EnderChestStore.swapGameMode(player, event.getNewGameMode());
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        EnderChestStore.save(player);
        EnderChestStore.flush(uuid);
        EnderChestStore.evict(uuid);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        EnderChestStore.flushAll();
    }
}
