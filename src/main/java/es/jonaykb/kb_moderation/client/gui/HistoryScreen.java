package es.jonaykb.kb_moderation.client.gui;

import es.jonaykb.kb_moderation.client.ClientState;
import es.jonaykb.kb_moderation.client.ModToast;
import es.jonaykb.kb_moderation.client.Sounds;
import es.jonaykb.kb_moderation.network.Net;
import es.jonaykb.kb_moderation.network.Packets;
import es.jonaykb.kb_moderation.perm.Perm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Every sanction of one player (and of their Discord account), newest first, with lift / remove buttons. */
public final class HistoryScreen extends Screen {
    private static final int ROW = 40;
    private static final String[] TYPE = {"Aviso", "Silencio", "Expulsión", "Baneo", "Nota"};
    private static final int[] COLOR = {Theme.YELLOW, Theme.ORANGE, Theme.RED, Theme.DARK_RED, Theme.MUTED};
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yy HH:mm");

    private final Screen parent;
    private final String name;
    private List<Packets.HistoryRow> rows;
    private double scroll;
    private final long openedAt = System.currentTimeMillis();
    private int left, top, w, h;

    public HistoryScreen(Screen parent, String name) {
        super(Component.literal("Historial de " + name));
        this.parent = parent;
        this.name = name;
    }

    public boolean isFor(String n) {
        return name.equalsIgnoreCase(n);
    }

    public void setData(Packets.History m) {
        List<Packets.HistoryRow> copy = new java.util.ArrayList<>(m.rows());
        java.util.Collections.reverse(copy);
        rows = copy;
        rebuild();
    }

    @Override
    protected void init() {
        w = Math.min(420, width - 16);
        h = Math.min(270, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        if (rows == null) Net.CHANNEL.sendToServer(new Packets.RequestHistory(name));
        rebuild();
    }

    private int visible() {
        return (h - 44) / ROW;
    }

    private void rebuild() {
        clearWidgets();
        addRenderableWidget(new FlatButton(left + w - 78, top + 6, 70, 18, "← Volver", 0xFF4A5266, null, b -> onClose()));
        if (rows == null) return;
        int first = (int) scroll;
        for (int i = 0; i < visible() && first + i < rows.size(); i++) {
            Packets.HistoryRow r = rows.get(first + i);
            int y = top + 34 + i * ROW;
            FlatButton b = null;
            if ((r.type() == 0 || r.type() == 4) && !r.revoked() && ClientState.has(Perm.WARN)) {
                b = new FlatButton(left + w - 84, y + 10, 72, 18, "Quitar", Theme.GREEN, null, x -> act("delwarn " + r.id(), "Aviso #" + r.id() + " retirado"));
            } else if (r.active() && r.type() == 1 && ClientState.has(Perm.MUTE)) {
                b = new FlatButton(left + w - 84, y + 10, 72, 18, "Levantar", Theme.GREEN, null, x -> act("unmute " + name, name + " puede hablar"));
            } else if (r.active() && r.type() == 3 && ClientState.has(Perm.BAN)) {
                b = new FlatButton(left + w - 84, y + 10, 72, 18, "Levantar", Theme.GREEN, null, x -> act("unban " + name, name + " desbaneado"));
            }
            if (b != null) addRenderableWidget(b);
        }
    }

    private void act(String cmd, String feedback) {
        PanelScreen.run(cmd);
        Sounds.confirm();
        minecraft.getToasts().addToast(new ModToast(3, "Hecho", feedback));
        rows = null;
        // Sent after the command on the same connection, so the server answers with it already applied.
        Net.CHANNEL.sendToServer(new Packets.RequestHistory(name));
        rebuild();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (rows == null) return false;
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible()), scroll - delta));
        rebuild();
        return true;
    }

    @Override
    public void onClose() {
        if (parent instanceof PanelScreen p) p.refreshSoon();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        float t = Theme.ease(openedAt, 160);
        g.pose().pushPose();
        g.pose().translate((1 - t) * 16, 0, 0);
        Theme.panel(g, left, top, w, h, Theme.BG);
        g.renderItem(new ItemStack(Items.WRITABLE_BOOK), left + 8, top + 6);
        g.drawString(font, Component.literal("Historial de ").append(Component.literal(name).withStyle(st -> st.withBold(true))),
                left + 28, top + 11, Theme.TEXT, false);
        if (rows == null) {
            g.drawCenteredString(font, "Cargando…", left + w / 2, top + h / 2, Theme.MUTED);
        } else if (rows.isEmpty()) {
            g.drawCenteredString(font, "✔ Sin sanciones. ¡Un jugador ejemplar!", left + w / 2, top + h / 2, Theme.GREEN);
        } else {
            int first = (int) scroll;
            for (int i = 0; i < visible() && first + i < rows.size(); i++) {
                Packets.HistoryRow r = rows.get(first + i);
                int y = top + 34 + i * ROW;
                int c = COLOR[r.type()];
                boolean faded = r.revoked() || (!r.active() && r.type() != 0 && r.type() != 2 && r.type() != 4);
                g.fill(left + 8, y, left + w - 8, y + ROW - 4, faded ? 0xFF1A1E27 : Theme.PANEL_2);
                g.fill(left + 8, y, left + 10, y + ROW - 4, faded ? Theme.withAlpha(c, 0.4f) : c);
                int px = left + 16;
                px += Theme.pill(g, font, TYPE[r.type()], px, y + 4, c) + 4;
                if (r.type() == 1 || r.type() == 3) {
                    px += Theme.pill(g, font, r.expiresAt() == 0 ? "permanente" : duration(r.expiresAt() - r.createdAt()), px, y + 4, Theme.MUTED) + 4;
                }
                String state;
                int sc;
                if (r.revoked()) {
                    state = "Retirada por " + r.revokedBy();
                    sc = Theme.FAINT;
                } else if (r.active()) {
                    state = r.expiresAt() == 0 ? "Activa" : "Activa · quedan " + Theme.until(r.expiresAt());
                    sc = Theme.RED;
                } else if (r.type() == 1 || r.type() == 3) {
                    state = "Cumplida";
                    sc = Theme.FAINT;
                } else {
                    state = "";
                    sc = Theme.FAINT;
                }
                if (!state.isEmpty()) px += Theme.pill(g, font, state, px, y + 4, sc) + 4;
                if (r.origin().equals("discord")) Theme.pill(g, font, "Discord", px, y + 4, 0xFF8EA1FF);
                else if (r.origin().equals("minecraft")) Theme.pill(g, font, "Otro servidor", px, y + 4, Theme.CYAN);
                g.drawString(font, "§8#" + r.id() + "  §7" + DATE.format(new Date(r.createdAt())) + " · por §f" + r.staff(), left + 16, y + 18,
                        Theme.TEXT, false);
                g.drawString(font, Theme.fit(font, r.reason(), w - 120), left + 16, y + 27, faded ? Theme.FAINT : Theme.TEXT, false);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    private static String duration(long ms) {
        long m = ms / 60000;
        if (m < 60) return m + " min";
        if (m < 1440) return m / 60 + " h";
        return m / 1440 + " d";
    }
}
