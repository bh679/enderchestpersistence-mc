package games.brennan.enderchestpersistence.fabric.mixin;

import games.brennan.enderchestpersistence.EnderChestStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerSaveMixin {

    /**
     * Checkpoint the ender chest whenever vanilla saves the player — autosave,
     * {@code /save-all}, logout — so a crash cannot roll it back further than
     * vanilla rolls back the rest of the player's data. Fabric API 0.103.x
     * (1.21.1) has no player-save event, hence the mixin; NeoForge/Forge use
     * {@code PlayerEvent.SaveToFile}.
     */
    @Inject(method = "save", at = @At("HEAD"))
    private void onSavePlayer(ServerPlayer player, CallbackInfo ci) {
        EnderChestStore.checkpoint(player);
    }
}
