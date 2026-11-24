package es.jonaykb.kb_moderation.client;

import es.jonaykb.kb_moderation.client.gui.HistoryScreen;
import es.jonaykb.kb_moderation.client.gui.PanelScreen;
import es.jonaykb.kb_moderation.client.gui.ReportScreen;
import es.jonaykb.kb_moderation.network.Net;
import es.jonaykb.kb_moderation.network.Packets;
import net.minecraft.client.Minecraft;

final class ClientNet implements Net.ClientBridge.Handler {
    @Override
    public void staffState(Packets.StaffState m) {
        ClientState.perms = m.perms();
        ClientState.vanished = m.vanished();
        ClientState.seeInvisible = m.seeInvisible();
        ClientState.staffChat = m.staffChat();
        if (Minecraft.getInstance().screen instanceof PanelScreen panel) panel.onStateChanged();
    }

    @Override
    public void frozen(Packets.Frozen m) {
        if (m.frozen() && !ClientState.frozen) ClientState.frozenAt = System.currentTimeMillis();
        ClientState.frozen = m.frozen();
        ClientState.frozenBy = m.by();
        if (m.frozen()) Sounds.freeze();
        else Sounds.unfreeze();
    }

    @Override
    public void panel(Packets.Panel m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PanelScreen panel) panel.setData(m);
        else if (m.open()) mc.setScreen(new PanelScreen(m));
    }

    @Override
    public void history(Packets.History m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HistoryScreen h && h.isFor(m.name())) h.setData(m);
    }

    @Override
    public void openReport() {
        Minecraft.getInstance().setScreen(new ReportScreen(null));
    }

    @Override
    public void vanished(Packets.Vanished m) {
        ClientState.vanishedPlayers = java.util.Set.copyOf(m.players());
    }

    @Override
    public void staffChat(Packets.StaffChatData m) {
        if (m.full()) {
            ClientState.staffLines.clear();
        } else {
            ClientState.staffUnread += m.lines().size();
        }
        ClientState.staffLines.addAll(m.lines());
        while (ClientState.staffLines.size() > 100) ClientState.staffLines.remove(0);
        if (Minecraft.getInstance().screen instanceof PanelScreen panel) panel.onStaffChat();
    }

    @Override
    public void toast(Packets.Toast m) {
        Minecraft.getInstance().getToasts().addToast(new ModToast(m.kind(), m.title(), m.body()));
        Sounds.toast(m.kind());
    }
}
