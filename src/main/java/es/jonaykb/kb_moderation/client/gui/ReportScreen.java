package es.jonaykb.kb_moderation.client.gui;

import es.jonaykb.kb_moderation.client.ModToast;
import es.jonaykb.kb_moderation.client.Sounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** For every player: pick who, why, add details, send. Opened with /report or its key. */
public final class ReportScreen extends Screen {
    private static final int ROW = 20;
    private static final List<String> CATEGORIES = List.of("Hacks", "Griefeo", "Robo", "Insultos", "Acoso", "Spam", "Abuso de bugs", "Otro");

    private final Screen parent;
    private List<PlayerInfo> players = List.of();
    private PlayerInfo selected;
    private String category;
    private String details = "";
    private String filter = "";
    private EditBox search;
    private EditBox detailsBox;
    private double scroll;
    private final long openedAt = System.currentTimeMillis();
    private int left, top, w, h;

    public ReportScreen(Screen parent) {
        super(Component.literal("Reportar"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        w = Math.min(380, width - 16);
        h = Math.min(240, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        var conn = minecraft.getConnection();
        if (conn != null) {
            players = conn.getOnlinePlayers().stream()
                    .filter(p -> minecraft.player == null || !p.getProfile().getId().equals(minecraft.player.getUUID()))
                    .sorted(Comparator.comparing(p -> p.getProfile().getName().toLowerCase(Locale.ROOT))).toList();
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        search = new EditBox(font, left + 11, top + 33, 116, 12, Component.literal("Buscar"));
        search.setBordered(false);
        search.setHint(Component.literal("Buscar…").withStyle(st -> st.withColor(Theme.FAINT)));
        search.setValue(filter);
        search.setResponder(s -> {
            filter = s;
            scroll = 0;
        });
        addRenderableWidget(search);

        int x = left + 144, y = top + 44;
        int bw = (w - 154 - 3) / 2;
        for (int i = 0; i < CATEGORIES.size(); i++) {
            String c = CATEGORIES.get(i);
            addRenderableWidget(new FlatButton(x + (i % 2) * (bw + 3), y + (i / 2) * 19, bw, 16, c, Theme.RED, null, b -> {
                category = c;
                Sounds.select();
                rebuild();
            }).selected(c.equals(category)));
        }
        detailsBox = new EditBox(font, left + 147, top + 138, w - 160, 12, Component.literal("Detalles"));
        detailsBox.setBordered(false);
        detailsBox.setMaxLength(180);
        detailsBox.setHint(Component.literal("¿Qué ha pasado? (opcional)").withStyle(st -> st.withColor(Theme.FAINT)));
        detailsBox.setValue(details);
        detailsBox.setResponder(s -> details = s);
        addRenderableWidget(detailsBox);

        addRenderableWidget(new FlatButton(left + 144, top + h - 30, 70, 20, "Cancelar", 0xFF4A5266, null, b -> onClose()));
        addRenderableWidget(new FlatButton(left + w - 130, top + h - 30, 120, 20, "Enviar reporte", Theme.RED, new ItemStack(Items.RED_BANNER),
                b -> send()).enabled(selected != null && category != null)
                .tooltip(selected == null ? "Elige a quién reportas" : category == null ? "Elige el motivo" : null));
    }

    private List<PlayerInfo> filtered() {
        String q = filter.toLowerCase(Locale.ROOT).trim();
        return q.isEmpty() ? players : players.stream().filter(p -> p.getProfile().getName().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void send() {
        String reason = category + (details.isBlank() ? "" : ": " + details.trim());
        PanelScreen.run("report " + selected.getProfile().getName() + " " + reason);
        Sounds.confirm();
        minecraft.getToasts().addToast(new ModToast(3, "Reporte enviado", "El staff lo revisará. ¡Gracias!"));
        minecraft.setScreen(parent);
    }

    private int listTop() {
        return top + 52;
    }

    private int listBottom() {
        return top + h - 8;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= left + 8 && mx < left + 134 && my >= listTop() && my < listBottom()) {
            int i = (int) ((my - listTop()) / ROW + scroll);
            List<PlayerInfo> list = filtered();
            if (i >= 0 && i < list.size()) {
                selected = list.get(i);
                Sounds.select();
                rebuild();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, filtered().size() - (listBottom() - listTop()) / ROW);
        scroll = Math.max(0, Math.min(max, scroll - delta));
        return true;
    }

    @Override
    public void tick() {
        search.tick();
        detailsBox.tick();
    }

    @Override
    public void onClose() {
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
        g.pose().translate(0, (1 - t) * 12, 0);
        Theme.panel(g, left, top, w, h, Theme.BG);
        g.fill(left + 1, top + 1, left + w - 1, top + 3, Theme.RED);
        g.renderItem(new ItemStack(Items.RED_BANNER), left + 8, top + 7);
        g.drawString(font, "Reportar a un jugador", left + 28, top + 12, Theme.TEXT, false);

        Theme.panel(g, left + 6, top + 28, 130, h - 34, Theme.PANEL);
        g.fill(left + 7, top + 47, left + 135, top + 48, Theme.BORDER);
        List<PlayerInfo> list = filtered();
        g.enableScissor(left + 7, listTop(), left + 135, listBottom());
        int visible = (listBottom() - listTop()) / ROW;
        for (int i = 0; i <= visible; i++) {
            int idx = i + (int) scroll;
            if (idx >= list.size()) break;
            PlayerInfo p = list.get(idx);
            int y = listTop() + i * ROW;
            boolean sel = p == selected;
            boolean hov = mouseX >= left + 8 && mouseX < left + 134 && mouseY >= y && mouseY < y + ROW;
            if (sel) {
                g.fill(left + 8, y + 1, left + 134, y + ROW - 1, Theme.withAlpha(Theme.RED, 0.25f));
                g.fill(left + 8, y + 1, left + 10, y + ROW - 1, Theme.RED);
            } else if (hov) {
                g.fill(left + 8, y + 1, left + 134, y + ROW - 1, 0x18FFFFFF);
            }
            Theme.face(g, p.getProfile().getId(), left + 13, y + 2, 16);
            g.drawString(font, Theme.fit(font, p.getProfile().getName(), 96), left + 34, y + 6, Theme.TEXT, false);
        }
        g.disableScissor();
        if (list.isEmpty()) g.drawCenteredString(font, "No hay nadie más", left + 71, listTop() + 8, Theme.FAINT);

        g.drawString(font, selected == null ? "§7¿A quién reportas?" : "§7Reportar a §f§l" + selected.getProfile().getName(),
                left + 144, top + 31, Theme.TEXT, false);
        g.drawString(font, "Detalles", left + 144, top + 125, Theme.MUTED, false);
        Theme.panel(g, left + 144, top + 134, w - 154, 18, Theme.PANEL);
        g.drawString(font, Theme.fit(font, "Los reportes falsos también se sancionan.", w - 154), left + 144, top + 160, Theme.FAINT, false);
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }
}
