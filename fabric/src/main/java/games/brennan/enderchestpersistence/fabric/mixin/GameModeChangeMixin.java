package games.brennan.enderchestpersistence.fabric.mixin;

import games.brennan.enderchestpersistence.EnderChestStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeChangeMixin {

    @Shadow
    protected ServerPlayer player;

    /**
     * Swap the ender chest contents when the player's game mode changes.
     * Injects at HEAD so {@link EnderChestStore#swapGameMode} reads the old
     * mode via {@code player.gameMode.getGameModeForPlayer()} before the change
     * is applied.
     */
    @Inject(method = "changeGameModeForPlayer", at = @At("HEAD"))
    private void onChangeGameMode(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (player == null) return;
        EnderChestStore.swapGameMode(player, gameType);
    }
}
