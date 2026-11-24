package es.jonaykb.kb_moderation.compat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;

/** Found by Simple Voice Chat through the annotation; never loaded when it is not installed. */
@ForgeVoicechatPlugin
public final class VoiceChatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "kb_moderation";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender != null && VoiceMute.isSilenced(sender.getPlayer().getUuid())) event.cancel();
    }
}
