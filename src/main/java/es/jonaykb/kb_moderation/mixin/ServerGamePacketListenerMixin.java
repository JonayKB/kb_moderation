package es.jonaykb.kb_moderation.mixin;

import es.jonaykb.kb_moderation.vanish.SeeInvisible;
import es.jonaykb.kb_moderation.vanish.Vanish;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void kbModeration$leaving(Component reason, CallbackInfo ci) {
        Vanish.joiningOrLeaving = player;
    }

    @Inject(method = "onDisconnect", at = @At("RETURN"))
    private void kbModeration$left(Component reason, CallbackInfo ci) {
        Vanish.joiningOrLeaving = null;
    }

    /** /verinvisibles: invisible players are sent to this staff member without the invisible flag. */
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> kbModeration$seeInvisible(Packet<?> packet) {
        return SeeInvisible.rewrite(player, packet);
    }
}
