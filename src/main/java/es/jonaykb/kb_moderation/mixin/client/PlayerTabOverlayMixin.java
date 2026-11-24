package es.jonaykb.kb_moderation.mixin.client;

import es.jonaykb.kb_moderation.client.ClientState;
import es.jonaykb.kb_moderation.client.VanishMark;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Staff see "◎" before vanished players in the tab list (others do not see them there at all). */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void kbModeration$vanishMark(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (ClientState.isVanished(info.getProfile().getId())) cir.setReturnValue(VanishMark.mark(cir.getReturnValue()));
    }
}
