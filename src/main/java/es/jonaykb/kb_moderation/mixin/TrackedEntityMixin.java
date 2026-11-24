package es.jonaykb.kb_moderation.mixin;

import es.jonaykb.kb_moderation.vanish.TrackedEntityAccess;
import es.jonaykb.kb_moderation.vanish.Vanish;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** A vanished player is never sent to players who may not see them: no entity, no sounds of their steps. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements TrackedEntityAccess {
    @Shadow @Final Entity entity;

    @Shadow
    public abstract void removePlayer(ServerPlayer player);

    @Shadow
    public abstract void updatePlayers(List<ServerPlayer> players);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void kbModeration$hideVanished(ServerPlayer viewer, CallbackInfo ci) {
        if (entity instanceof ServerPlayer target && target != viewer && !Vanish.canSee(viewer, target)) {
            removePlayer(viewer);
            ci.cancel();
        }
    }

    @Override
    public void kbModeration$updatePlayers(List<ServerPlayer> players) {
        updatePlayers(players);
    }
}
