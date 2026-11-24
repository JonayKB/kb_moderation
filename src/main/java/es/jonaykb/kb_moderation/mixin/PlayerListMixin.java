package es.jonaykb.kb_moderation.mixin;

import com.mojang.authlib.GameProfile;
import es.jonaykb.kb_moderation.sanction.Sanctions;
import es.jonaykb.kb_moderation.vanish.Vanish;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    /** Banned accounts (or accounts of a banned Discord user) are refused before they join. */
    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void kbModeration$banned(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() != null) return;
        Component reason = Sanctions.loginBlock(profile);
        if (reason != null) cir.setReturnValue(reason);
    }

    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void kbModeration$joining(Connection connection, ServerPlayer player, CallbackInfo ci) {
        Vanish.joiningOrLeaving = player;
    }

    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void kbModeration$joined(Connection connection, ServerPlayer player, CallbackInfo ci) {
        Vanish.joiningOrLeaving = null;
    }

    /** No "joined/left the game" for a vanished player. */
    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void kbModeration$silentJoinLeave(Component message, boolean overlay, CallbackInfo ci) {
        ServerPlayer p = Vanish.joiningOrLeaving;
        if (p == null || !Vanish.isVanished(p.getUUID())) return;
        if (message.getContents() instanceof TranslatableContents t && t.getKey().startsWith("multiplayer.player.")) ci.cancel();
    }
}
