package es.jonaykb.kb_moderation.client.gui;

import es.jonaykb.kb_moderation.client.ClientState;
import es.jonaykb.kb_moderation.client.ModToast;
import es.jonaykb.kb_moderation.client.Sounds;
import es.jonaykb.kb_moderation.network.Net;
import es.jonaykb.kb_moderation.network.Packets;
import es.jonaykb.kb_moderation.perm.Perm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Staff panel: player list on the left, the selected player's card and actions on the right; reports tab. */
public final class PanelScreen extends Screen {
    private static final int ROW = 22;

    private List<Packets.PlayerRow> players = List.of();
    private List<Packets.ReportRow> reports = List.of();
    private boolean loaded;
    private UUID selected;
    private int tab;
    private EditBox search;
    private String searchText = "";
    private double listScroll;
    private double reportScroll;
    private final long openedAt = System.currentTimeMillis();
    private int ticks;
    private int refreshIn = -1;

    private int left, top, w, h;

    public PanelScreen(Packets.Panel data) {
        super(Component.literal("Panel de moderación"));
        if (data != null) apply(data);
    }

    public void setData(Packets.Panel data) {
        apply(data);
        rebuild();
    }

    private void apply(Packets.Panel data) {
        players = new ArrayList<>(data.players());
        // Online first, then by name.
        players.sort(Comparator.comparing((Packets.PlayerRow r) -> !r.online()).thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
        reports = data.reports();
        loaded = true;
        if (selected == null && !players.isEmpty()) {
            players.stream().filter(r -> minecraft == null || minecraft.player == null || !r.uuid().equals(minecraft.player.getUUID()))
                    .findFirst().ifPresent(r -> selected = r.uuid());
        }
    }

    public void onStateChanged() {
        rebuild();
    }

    // ───────────────────────────── layout ─────────────────────────────

    @Override
    protected void init() {
        w = Math.min(460, width - 16);
        h = Math.min(280, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        search = new EditBox(font, left + 11, contentTop() + 4, 146, 12, Component.literal("Buscar"));
        search.setHint(Component.literal("Buscar jugador…").withStyle(st -> st.withColor(Theme.FAINT)));
        search.setBordered(false);
        search.setValue(searchText);
        search.setResponder(s -> {
            searchText = s;
            listScroll = 0;
        });
        rebuild();
    }

    private void rebuild() {
        if (search == null) return;
        clearWidgets();
        int online = (int) players.stream().filter(Packets.PlayerRow::online).count();
        int tx = left + w - 8;
        if (ClientState.has(Perm.REPORTS)) {
            tx -= 92;
            addRenderableWidget(new FlatButton(tx, top + 5, 88, 16, "Reportes  " + reports.size(),
                    reports.isEmpty() ? Theme.ACCENT : Theme.RED, null, b -> setTab(1)).selected(tab == 1));
        }
        if (ClientState.has(Perm.STAFFCHAT)) {
            tx -= 92;
            String label = "Chat staff" + (ClientState.staffUnread > 0 && tab != 2 ? "  " + ClientState.staffUnread : "");
            addRenderableWidget(new FlatButton(tx, top + 5, 88, 16, label, ClientState.staffUnread > 0 && tab != 2 ? Theme.PINK : Theme.ACCENT, null,
                    b -> setTab(2)).selected(tab == 2));
        }
        tx -= 92;
        addRenderableWidget(new FlatButton(tx, top + 5, 88, 16, "Jugadores  " + online, Theme.ACCENT, null, b -> setTab(0))
                .selected(tab == 0));

        if (tab == 0) {
            int ty = listBottom() + 3, tw = 73;
            addRenderableWidget(toggle(left + 9, ty, tw, ClientState.vanished, Theme.CYAN, new ItemStack(Items.GLASS), Perm.VANISH, "vanish",
                    "Vanish", "Desaparecer para los jugadores (el staff te sigue viendo)"));
            addRenderableWidget(toggle(left + 9 + tw + 2, ty, tw, ClientState.seeInvisible, Theme.YELLOW, new ItemStack(Items.ENDER_EYE),
                    Perm.SEE_INVISIBLE, "verinvisibles", "Ver invisibles", "Ver a los jugadores con invisibilidad y su nombre"));
        }

        if (tab == 0) {
            addRenderableWidget(search);
            Packets.PlayerRow r = selectedRow();
            if (r != null) addActions(r);
        } else if (tab == 1) {
            addReportButtons();
        } else {
            addChatWidgets();
        }
    }

    private FlatButton toggle(int x, int y, int width, boolean on, int color, ItemStack icon, Perm perm, String command, String name, String tip) {
        FlatButton b = new FlatButton(x, y, width, 20, on ? "ON" : "", on ? color : 0xFF4A5266, icon, btn -> {
            run(command);
            if (!on) Sounds.confirm();
            else Sounds.select();
        }).selected(on).enabled(ClientState.has(perm));
        return b.tooltip(name + (on ? " §a(activado)" : " §7(desactivado)") + "\n§7" + tip);
    }

    private void setTab(int t) {
        if (tab == t) return;
        tab = t;
        Sounds.select();
        if (t == 2) {
            ClientState.staffUnread = 0;
            chatScroll = 0;
            Net.CHANNEL.sendToServer(new Packets.RequestStaffChat());
        }
        rebuild();
        // The click that opened the tab focuses the button afterwards: give the text box the focus on the next tick.
        if (t == 2) focusChat = true;
    }

    private boolean focusChat;

    // ───────────────────────────── staff chat tab ─────────────────────────────

    private EditBox chatBox;
    private String chatText = "";
    /** Lines scrolled up from the newest message. */
    private int chatScroll;

    private void addChatWidgets() {
        int y = top + h - 26;
        chatBox = new EditBox(font, left + 12, y + 5, w - 98, 12, Component.literal("Mensaje"));
        chatBox.setBordered(false);
        chatBox.setMaxLength(256);
        chatBox.setHint(Component.literal("Escribe al staff… (Enter para enviar)").withStyle(st -> st.withColor(Theme.FAINT)));
        chatBox.setValue(chatText);
        chatBox.setResponder(v -> chatText = v);
        addRenderableWidget(chatBox);
        addRenderableWidget(new FlatButton(left + w - 80, y + 1, 72, 20, "Enviar", Theme.PINK, new ItemStack(Items.WRITABLE_BOOK),
                b -> sendChat()).enabled(ClientState.has(Perm.STAFFCHAT)));
    }

    private void sendChat() {
        String text = chatText.trim();
        if (text.isEmpty()) return;
        Net.CHANNEL.sendToServer(new Packets.SendStaffChat(text));
        chatText = "";
        if (chatBox != null) chatBox.setValue("");
        chatScroll = 0;
        Sounds.select();
    }

    /** A staff message arrived (or the history): the tab shows it; elsewhere the tab counts it. */
    public void onStaffChat() {
        if (tab == 2) ClientState.staffUnread = 0;
        else rebuild();
    }

    private List<net.minecraft.util.FormattedCharSequence> chatLines(int width) {
        List<net.minecraft.util.FormattedCharSequence> out = new ArrayList<>();
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault());
        boolean me;
        for (Packets.StaffLine l : ClientState.staffLines) {
            me = minecraft != null && minecraft.player != null && l.from().equals(minecraft.player.getGameProfile().getName());
            Component c = Component.literal(hm.format(java.time.Instant.ofEpochMilli(l.at())) + "  ").withStyle(st -> st.withColor(Theme.FAINT))
                    .append(Component.literal(l.from()).withStyle(st -> st.withColor(0xFFE879F9).withBold(true)))
                    .append(Component.literal(": ").withStyle(st -> st.withColor(Theme.FAINT)))
                    .append(Component.literal(l.text()).withStyle(st -> st.withColor(Theme.TEXT)));
            if (me) c = Component.empty().append(c);
            out.addAll(font.split(c, width));
        }
        return out;
    }

    private void renderChat(GuiGraphics g) {
        int x = left + 6, y = contentTop(), cw = w - 12, ch = h - 36 - 28;
        Theme.panel(g, x, y, cw, ch, Theme.PANEL);
        Theme.panel(g, x, top + h - 26, w - 92, 22, Theme.PANEL);
        List<net.minecraft.util.FormattedCharSequence> lines = chatLines(cw - 16);
        if (lines.isEmpty()) {
            g.drawCenteredString(font, "Aún no hay mensajes del staff", x + cw / 2, y + ch / 2 - 4, Theme.FAINT);
            return;
        }
        int visible = (ch - 8) / 10;
        chatScroll = Math.max(0, Math.min(chatScroll, Math.max(0, lines.size() - visible)));
        int end = lines.size() - chatScroll;
        int start = Math.max(0, end - visible);
        int ly = y + ch - 6 - (end - start) * 10;
        for (int i = start; i < end; i++, ly += 10) g.drawString(font, lines.get(i), x + 8, ly, Theme.TEXT, false);
        if (chatScroll > 0) g.drawCenteredString(font, "▼ " + chatScroll + " líneas más nuevas", x + cw / 2, y + ch - 12, Theme.PINK);
    }

    // ───────────────────────────── card actions ─────────────────────────────

    private void addActions(Packets.PlayerRow r) {
        int cx = left + 172, cw = w - 180;
        int bw = (cw - 8) / 3, bh = 18;
        int y = actionsTop();
        String n = r.name();
        boolean on = r.online();
        boolean muted = r.mutedUntil() >= 0, banned = r.bannedUntil() >= 0;

        List<FlatButton> b = new ArrayList<>();
        b.add(new FlatButton(0, 0, bw, bh, "Avisar", Theme.YELLOW, new ItemStack(Items.BELL),
                x -> open(SanctionScreen.Kind.WARN, r)).enabled(ClientState.has(Perm.WARN)).tooltip("Aviso por escrito. Se acumulan."));
        b.add(muted
                ? new FlatButton(0, 0, bw, bh, "Desmutear", Theme.GREEN, new ItemStack(Items.NOTE_BLOCK), x -> act("unmute " + n, n + " puede volver a hablar"))
                .enabled(ClientState.has(Perm.MUTE))
                : new FlatButton(0, 0, bw, bh, "Silenciar", Theme.ORANGE, new ItemStack(Items.NOTE_BLOCK), x -> open(SanctionScreen.Kind.MUTE, r))
                .enabled(ClientState.has(Perm.MUTE)).tooltip("Chat, mensajes privados y chat de voz"));
        b.add(new FlatButton(0, 0, bw, bh, "Expulsar", Theme.RED, new ItemStack(Items.LEATHER_BOOTS), x -> open(SanctionScreen.Kind.KICK, r))
                .enabled(on && ClientState.has(Perm.KICK)).tooltip(on ? null : "No está conectado"));
        b.add(banned
                ? new FlatButton(0, 0, bw, bh, "Desbanear", Theme.GREEN, null, x -> act("unban " + n, n + " ya puede entrar"))
                .enabled(ClientState.has(Perm.BAN))
                : new FlatButton(0, 0, bw, bh, "Banear", Theme.DARK_RED, new ItemStack(Items.ANVIL), x -> open(SanctionScreen.Kind.BAN, r))
                .enabled(ClientState.has(Perm.BAN)).tooltip("También banea su cuenta de Discord"));
        b.add(r.frozen()
                ? new FlatButton(0, 0, bw, bh, "Liberar", Theme.GREEN, new ItemStack(Items.BLUE_ICE), x -> act("unfreeze " + n, n + " descongelado"))
                .enabled(ClientState.has(Perm.FREEZE))
                : new FlatButton(0, 0, bw, bh, "Congelar", Theme.CYAN, new ItemStack(Items.BLUE_ICE), x -> act("freeze " + n, n + " congelado"))
                .enabled(on && ClientState.has(Perm.FREEZE)).tooltip(on ? "No podrá moverse ni interactuar" : "No está conectado"));
        b.add(new FlatButton(0, 0, bw, bh, "Historial", Theme.PURPLE, new ItemStack(Items.WRITABLE_BOOK),
                x -> minecraft.setScreen(new HistoryScreen(this, n))).enabled(ClientState.has(Perm.HISTORY)));
        b.add(new FlatButton(0, 0, bw, bh, "Inventario", Theme.ORANGE, new ItemStack(Items.CHEST), x -> run("invsee " + n))
                .enabled(on && ClientState.has(Perm.INVSEE)).tooltip(ClientState.has(Perm.INVSEE_EDIT) ? "Ver y editar" : "Solo ver"));
        b.add(new FlatButton(0, 0, bw, bh, "Ender", Theme.PURPLE, new ItemStack(Items.ENDER_CHEST), x -> run("endersee " + n))
                .enabled(on && ClientState.has(Perm.INVSEE)));
        b.add(new FlatButton(0, 0, bw, bh, "Info", Theme.ACCENT, new ItemStack(Items.SPYGLASS), x -> {
            run("modinfo " + n);
            onClose();
        }).enabled(ClientState.has(Perm.MODINFO)).tooltip("Cuentas del mismo Discord, IP compartida… (en el chat)"));
        b.add(new FlatButton(0, 0, bw, bh, "Ir", Theme.CYAN, new ItemStack(Items.ENDER_PEARL), x -> {
            run("mtp " + n);
            onClose();
        }).enabled(on && ClientState.has(Perm.TELEPORT)));
        b.add(new FlatButton(0, 0, bw, bh, "Traer", Theme.CYAN, new ItemStack(Items.LEAD), x -> act("mtphere " + n, n + " traído"))
                .enabled(on && ClientState.has(Perm.TELEPORT)));
        b.add(new FlatButton(0, 0, bw, bh, "Nota", 0xFF6B7385, new ItemStack(Items.PAPER), x -> open(SanctionScreen.Kind.NOTE, r))
                .enabled(ClientState.has(Perm.WARN)).tooltip("Nota interna en su historial: el jugador no se entera"));

        for (int i = 0; i < b.size(); i++) {
            FlatButton btn = b.get(i);
            btn.setX(cx + (i % 3) * (bw + 4));
            btn.setY(y + (i / 3) * (bh + 3));
            addRenderableWidget(btn);
        }
    }

    private static final int ACTION_ROWS = 4;

    /** The 11 actions take 4 rows of 3, anchored to the bottom of the card. */
    private int actionsTop() {
        return top + h - 6 - 4 - ACTION_ROWS * 21 + 3;
    }

    private void open(SanctionScreen.Kind kind, Packets.PlayerRow r) {
        minecraft.setScreen(new SanctionScreen(this, kind, r.name()));
    }

    /** Runs a command for an instant action and refreshes the panel shortly after. */
    private void act(String command, String feedback) {
        run(command);
        Sounds.confirm();
        minecraft.getToasts().addToast(new ModToast(3, "Hecho", feedback));
        refreshSoon();
    }

    public void refreshSoon() {
        refreshIn = 6;
    }

    static void run(String command) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.sendCommand(command);
    }

    // ───────────────────────────── reports tab ─────────────────────────────

    private static final int REPORT_ROW = 36;

    private int reportsTop() {
        return contentTop() + 4;
    }

    private int reportsVisible() {
        return (h - 44) / REPORT_ROW;
    }

    private void addReportButtons() {
        int first = (int) reportScroll;
        for (int i = 0; i < reportsVisible() && first + i < reports.size(); i++) {
            Packets.ReportRow r = reports.get(reports.size() - 1 - (first + i));
            int y = reportsTop() + i * REPORT_ROW + 8;
            int x = left + w - 12;
            x -= 56;
            addRenderableWidget(new FlatButton(x, y, 56, 18, "Cerrar", Theme.GREEN, null, b -> act("reports close " + r.id(), "Cerrado")));
            x -= 60;
            addRenderableWidget(new FlatButton(x, y, 56, 18, "Ver", Theme.ACCENT, null, b -> {
                players.stream().filter(p -> p.name().equalsIgnoreCase(r.target())).findFirst().ifPresent(p -> selected = p.uuid());
                setTab(0);
            }));
            x -= 60;
            addRenderableWidget(new FlatButton(x, y, 56, 18, "Ir", Theme.CYAN, new ItemStack(Items.ENDER_PEARL), b -> {
                run("mtp " + r.target());
                onClose();
            }).enabled(ClientState.has(Perm.TELEPORT)));
        }
    }

    // ───────────────────────────── list ─────────────────────────────

    private List<Packets.PlayerRow> filtered() {
        String q = searchText.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return players;
        return players.stream().filter(r -> r.name().toLowerCase(Locale.ROOT).contains(q)
                || r.discord().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private Packets.PlayerRow selectedRow() {
        if (selected == null) return null;
        for (Packets.PlayerRow r : players) if (r.uuid().equals(selected)) return r;
        return null;
    }

    private int contentTop() {
        return top + 30;
    }

    private int listTop() {
        return contentTop() + 22;
    }

    /** Above the row of toggles at the bottom of the list column. */
    private int listBottom() {
        return top + h - 6 - 26;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == 0 && mx >= left + 8 && mx < left + 160 && my >= listTop() && my < listBottom()) {
            int i = (int) ((my - listTop()) / ROW + listScroll);
            List<Packets.PlayerRow> list = filtered();
            if (i >= 0 && i < list.size()) {
                if (!list.get(i).uuid().equals(selected)) {
                    selected = list.get(i).uuid();
                    Sounds.select();
                    rebuild();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 0 && mx < left + 160) {
            int max = Math.max(0, filtered().size() - (listBottom() - listTop()) / ROW);
            listScroll = Math.max(0, Math.min(max, listScroll - delta));
            return true;
        }
        if (tab == 2) {
            chatScroll = Math.max(0, chatScroll + (int) Math.signum(delta) * 3);
            return true;
        }
        if (tab == 1) {
            int max = Math.max(0, reports.size() - reportsVisible());
            reportScroll = Math.max(0, Math.min(max, reportScroll - delta));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tab == 2 && (key == 257 || key == 335)) {
            sendChat();
            return true;
        }
        // Arrow keys move through the list, so it can be used without the mouse.
        if (tab == 0 && (key == 264 || key == 265)) {
            List<Packets.PlayerRow> list = filtered();
            if (!list.isEmpty()) {
                int i = 0;
                for (int j = 0; j < list.size(); j++) if (list.get(j).uuid().equals(selected)) i = j;
                i = Math.max(0, Math.min(list.size() - 1, i + (key == 264 ? 1 : -1)));
                selected = list.get(i).uuid();
                int visible = (listBottom() - listTop()) / ROW;
                if (i < listScroll) listScroll = i;
                if (i >= listScroll + visible) listScroll = i - visible + 1;
                Sounds.select();
                rebuild();
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        search.tick();
        if (chatBox != null) chatBox.tick();
        if (focusChat && chatBox != null && tab == 2) {
            focusChat = false;
            setFocused(chatBox);
        }
        if (++ticks % 40 == 0) Net.CHANNEL.sendToServer(new Packets.RequestPanel());
        if (refreshIn > 0 && --refreshIn == 0) Net.CHANNEL.sendToServer(new Packets.RequestPanel());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ───────────────────────────── render ─────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        float t = Theme.ease(openedAt, 180);
        g.pose().pushPose();
        float s = 0.96f + 0.04f * t;
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().scale(s, s, 1);
        g.pose().translate(-width / 2f, -height / 2f, 0);

        Theme.panel(g, left, top, w, h, Theme.BG);
        g.fill(left + 1, top + 1, left + w - 1, top + 25, Theme.PANEL);
        g.fill(left + 1, top + 25, left + w - 1, top + 26, Theme.BORDER);
        g.renderItem(new ItemStack(Items.IRON_SWORD), left + 7, top + 5);
        g.drawString(font, "Moderación", left + 27, top + 9, Theme.TEXT, false);

        if (!loaded) {
            g.drawCenteredString(font, "Cargando…", width / 2, height / 2, Theme.MUTED);
        } else if (tab == 0) {
            renderList(g, mouseX, mouseY);
            renderCard(g);
        } else if (tab == 1) {
            renderReports(g);
        } else {
            renderChat(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        Theme.panel(g, left + 6, contentTop(), 156, h - 36, Theme.PANEL);
        g.fill(left + 7, contentTop() + 19, left + 161, contentTop() + 20, Theme.BORDER);
        g.fill(left + 7, listBottom() + 1, left + 161, listBottom() + 2, Theme.BORDER);
        List<Packets.PlayerRow> list = filtered();
        int visible = (listBottom() - listTop()) / ROW;
        g.enableScissor(left + 7, listTop(), left + 161, listBottom());
        for (int i = 0; i < visible + 1; i++) {
            int idx = i + (int) listScroll;
            if (idx >= list.size()) break;
            Packets.PlayerRow r = list.get(idx);
            int y = listTop() + i * ROW;
            boolean sel = r.uuid().equals(selected);
            boolean hov = mouseX >= left + 8 && mouseX < left + 160 && mouseY >= y && mouseY < y + ROW;
            if (sel) {
                g.fill(left + 8, y + 1, left + 160, y + ROW - 1, Theme.withAlpha(Theme.ACCENT, 0.25f));
                g.fill(left + 8, y + 1, left + 10, y + ROW - 1, Theme.ACCENT);
            } else if (hov) {
                g.fill(left + 8, y + 1, left + 160, y + ROW - 1, 0x18FFFFFF);
            }
            Theme.face(g, r.uuid(), left + 13, y + 3, 16);
            g.fill(left + 26, y + 15, left + 30, y + 19, r.online() ? Theme.GREEN : Theme.FAINT);
            String icons = icons(r);
            int iw = font.width(icons);
            g.drawString(font, Theme.fit(font, r.name(), 120 - iw), left + 34, y + 7, r.online() ? Theme.TEXT : Theme.MUTED, false);
            g.drawString(font, icons, left + 156 - iw, y + 7, Theme.TEXT, false);
        }
        g.disableScissor();
        if (list.isEmpty()) g.drawCenteredString(font, "Nadie coincide", left + 84, listTop() + 10, Theme.FAINT);
        // Scrollbar
        if (list.size() > visible) {
            int trackH = listBottom() - listTop();
            int barH = Math.max(12, trackH * visible / list.size());
            int barY = listTop() + (int) ((trackH - barH) * (listScroll / Math.max(1, list.size() - visible)));
            g.fill(left + 158, barY, left + 160, barY + barH, Theme.withAlpha(Theme.TEXT, 0.35f));
        }
    }

    /** Small colored markers after a name in the list. */
    private static String icons(Packets.PlayerRow r) {
        StringBuilder b = new StringBuilder();
        if (r.bannedUntil() >= 0) b.append("§c✖");
        if (r.mutedUntil() >= 0) b.append("§6♪");
        if (r.frozen()) b.append("§b❄");
        if (r.vanished()) b.append("§d◎");
        if (r.warns() > 0) b.append("§e⚠").append(r.warns());
        return b.toString();
    }

    private void renderCard(GuiGraphics g) {
        int cx = left + 168, cy = contentTop(), cw = w - 174, ch = h - 36;
        Theme.panel(g, cx, cy, cw, ch, Theme.PANEL);
        Packets.PlayerRow r = selectedRow();
        if (r == null) {
            g.drawCenteredString(font, "Elige un jugador de la lista", cx + cw / 2, cy + 50, Theme.FAINT);
            return;
        }
        Theme.face(g, r.uuid(), cx + 7, cy + 7, 28);
        g.drawString(font, Component.literal(r.name()).withStyle(st -> st.withBold(true)), cx + 42, cy + 9, Theme.TEXT, false);
        g.drawString(font, r.discord().isEmpty() ? "Sin Discord vinculado" : "Discord " + r.discord(), cx + 42, cy + 22,
                r.discord().isEmpty() ? Theme.FAINT : 0xFF8EA1FF, false);

        // Status pills, wrapping to a second row only if needed.
        List<String> texts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        texts.add(r.online() ? "● Conectado" : "● " + (r.lastSeen() > 0 ? "Visto " + Theme.ago(r.lastSeen()) : "Desconectado"));
        colors.add(r.online() ? Theme.GREEN : Theme.MUTED);
        if (r.staff()) { texts.add("Staff"); colors.add(Theme.ACCENT); }
        if (r.bannedUntil() >= 0) { texts.add("Baneado · " + Theme.until(r.bannedUntil())); colors.add(Theme.RED); }
        if (r.mutedUntil() >= 0) { texts.add("Silenciado · " + Theme.until(r.mutedUntil())); colors.add(Theme.ORANGE); }
        if (r.frozen()) { texts.add("❄ Congelado"); colors.add(Theme.CYAN); }
        if (r.vanished()) { texts.add("◎ Vanish"); colors.add(Theme.PINK); }
        if (r.warns() > 0) { texts.add("⚠ " + r.warns() + (r.warns() == 1 ? " aviso" : " avisos")); colors.add(Theme.YELLOW); }
        int px = cx + 7, py = cy + 40;
        for (int i = 0; i < texts.size(); i++) {
            int pw = Theme.pillWidth(font, texts.get(i));
            if (px + pw > cx + cw - 6 && px > cx + 7) {
                px = cx + 7;
                py += 13;
            }
            px += Theme.pill(g, font, texts.get(i), px, py, colors.get(i)) + 3;
        }

        int ly = py + 16;
        String sanctions = "§7Sanciones: §f" + r.sanctions();
        if (r.online()) {
            g.drawString(font, "§c❤ §f" + Math.round(r.health()) + "§7/" + Math.round(r.maxHealth()) + "   §b⌁ §f" + r.ping() + " ms   " + sanctions,
                    cx + 7, ly, Theme.TEXT, false);
            if (ly + 11 < actionsTop() - 4) {
                g.drawString(font, Theme.fit(font, "§a⌖ §f" + r.x() + " " + r.y() + " " + r.z() + " §7" + r.dimension().replace("minecraft:", ""), cw - 14),
                        cx + 7, ly + 11, Theme.TEXT, false);
            }
        } else {
            g.drawString(font, "§7No está conectado   " + sanctions, cx + 7, ly, Theme.TEXT, false);
        }
        g.fill(cx + 5, actionsTop() - 4, cx + cw - 5, actionsTop() - 3, Theme.BORDER);
    }

    private void renderReports(GuiGraphics g) {
        Theme.panel(g, left + 6, contentTop(), w - 12, h - 36, Theme.PANEL);
        if (reports.isEmpty()) {
            g.drawCenteredString(font, "✔ No hay reportes abiertos", left + w / 2, top + 100, Theme.GREEN);
            return;
        }
        int first = (int) reportScroll;
        for (int i = 0; i < reportsVisible() && first + i < reports.size(); i++) {
            Packets.ReportRow r = reports.get(reports.size() - 1 - (first + i));
            int y = reportsTop() + i * REPORT_ROW;
            g.fill(left + 8, y + 2, left + w - 8, y + REPORT_ROW - 2, Theme.PANEL_2);
            g.fill(left + 8, y + 2, left + 10, y + REPORT_ROW - 2, Theme.RED);
            g.drawString(font, "§8#" + r.id() + " §f" + r.reporter() + " §7→ §c" + r.target() + " §8· §7" + Theme.ago(r.at()), left + 14, y + 6, Theme.TEXT, false);
            g.drawString(font, Theme.fit(font, r.reason(), w - 220), left + 14, y + 18, Theme.MUTED, false);
            g.drawString(font, Theme.fit(font, r.where(), 160), left + 14, y + 27, Theme.FAINT, false);
        }
    }
}
