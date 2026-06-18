package games.brennan.enderchestpersistence.neoforge;

import games.brennan.enderchestpersistence.ConfigDir;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(EnderChestPersistenceNeoForge.MOD_ID)
public final class EnderChestPersistenceNeoForge {

    public static final String MOD_ID = "enderchestpersistence";

    public EnderChestPersistenceNeoForge(IEventBus modBus) {
        ConfigDir.set(FMLPaths.CONFIGDIR.get());
        // EnderChestEvents registers itself via @EventBusSubscriber
    }
}
