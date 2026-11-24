package es.jonaykb.kb_moderation.client.gui;

import es.jonaykb.kb_moderation.client.ModToast;
import es.jonaykb.kb_moderation.client.Sounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.regex.Pattern;

/** Reason and duration for a warn, mute, kick or ban, with one-click presets. */
public final class SanctionScreen extends Screen {
    public enum Kind {
        WARN("Avisar", "warn", Theme.YELLOW, Items.BELL, false),
        MUTE("Silenciar", "mute", Theme.ORANGE, Items.NOTE_BLOCK, true),
        KICK("Expulsar", "kick", Theme.RED, Items.LEATHER_BOOTS, false),
        BAN("Banear", "ban", Theme.DARK_RED, Items.ANVIL, true),
        NOTE("Anotar", "note", Theme.MUTED, Items.PAPER, false);

        final String verb;
        final String command;
        final int color;
        final net.minecraft.world.item.Item icon;
        final boolean timed;

        Kind(String verb, String command, int color, net.minecraft.world.item.Item icon, boolean timed) {
            this.verb = verb;
            this.command = command;
            this.color = color;
            this.icon = icon;
            this.timed = timed;
        }
    }

    private static final List<String> REASONS = List.of("Spam", "Lenguaje ofensivo", "Acoso", "Griefeo", "Robo", "Hacks",
            "Abuso de bugs", "Publicidad", "Nombre/skin inapropiado", "Desobedecer al staff");
    private static final List<String[]> DURATIONS = List.of(new String[]{"15 min", "15m"}, new String[]{"1 h", "1h"},
            new String[]{"6 h", "6h"}, new String[]{"1 día", "1d"}, new String[]{"3 días", "3d"}, new String[]{"7 días", "7d"},
            new String[]{"30 días", "30d"}, new String[]{"Permanente", "perm"});
    private static final Pattern DURATION = Pattern.compile("(?i)perm|(\\d+[smhdw])+");

    private final PanelScreen parent;
    private final Kind kind;
    private final String target;
    private EditBox reason;
    private EditBox customDuration;
    private String duration = "1d";
    private String reasonText = "";
    private final long openedAt = System.currentTimeMillis();
    private int left, top, w, h;

    public SanctionScreen(PanelScreen parent, Kind kind, String target) {
        super(Component.literal(kind.verb + " a " + target));
        this.parent = parent;
        this.kind = kind;
        this.target = target;
        if (kind == Kind.MUTE) duration = "1h";
    }

    @Override
    protected void init() {
        w = Math.min(340, width - 16);
        h = kind.timed ? 236 : 178;
        left = (width - w) / 2;
        top = (height - h) / 2;

        int y = top + 46;
        int x = left + 10;
        for (String r : REASONS) {
            int bw = font.width(r) + 12;
            if (x + bw > left + w - 10) {
                x = left + 10;
                y += 18;
            }
            addRenderableWidget(new FlatButton(x, y, bw, 15, r, Theme.ACCENT, null, b -> {
                reason.setValue(r);
                reasonText = r;
                Sounds.select();
                rebuildChips();
            }).selected(r.equals(reasonText)));
            x += bw + 3;
        }
        y += 22;
        reason = new EditBox(font, left + 12, y + 4, w - 24, 12, Component.literal("Motivo"));
        reason.setMaxLength(200);
        reason.setBordered(false);
        reason.setHint(Component.literal("Escribe el motivo o elige uno arriba…").withStyle(st -> st.withColor(Theme.FAINT)));
        reason.setValue(reasonText);
        reason.setResponder(s -> reasonText = s);
        addRenderableWidget(reason);
        setInitialFocus(reason);
        y += 26;

        if (kind.timed) {
            // "Duración" title with the free-form field on its right, then the preset chips.
            customDuration = new EditBox(font, left + w - 92, y + 2, 80, 12, Component.literal("Otra"));
            customDuration.setBordered(false);
            customDuration.setMaxLength(12);
            customDuration.setHint(Component.literal("Otra: 2d12h").withStyle(st -> st.withColor(Theme.FAINT)));
            customDuration.setResponder(s -> {
                if (DURATION.matcher(s.trim()).matches()) duration = s.trim().toLowerCase();
            });
            addRenderableWidget(customDuration);
            y += 18;
            int bw = (w - 20 - 3 * 3) / 4;
            for (int i = 0; i < DURATIONS.size(); i++) {
                String[] d = DURATIONS.get(i);
                int bx = left + 10 + (i % 4) * (bw + 3);
                int by = y + (i / 4) * 19;
                boolean perm = d[1].equals("perm");
                addRenderableWidget(new FlatButton(bx, by, bw, 16, d[0], perm ? Theme.RED : Theme.ACCENT, null, b -> {
                    duration = d[1];
                    customDuration.setValue("");
                    Sounds.select();
                    rebuildChips();
                }).selected(d[1].equals(duration)));
            }
            y += 40;
        }

        int by = top + h - 30;
        addRenderableWidget(new FlatButton(left + 10, by, 90, 20, "Cancelar", 0xFF4A5266, null, b -> onClose()));
        boolean hold = kind == Kind.BAN;
        addRenderableWidget(new FlatButton(left + w - 160, by, 150, 20, hold ? "Mantén para banear" : kind.verb,
                kind.color, new ItemStack(kind.icon), hold ? 900 : 0, b -> confirm())
                .tooltip(hold ? "Mantén pulsado: los baneos se notan" : null));
    }

    private void rebuildChips() {
        String r = reasonText;
        String custom = customDuration == null ? "" : customDuration.getValue();
        clearWidgets();
        init();
        reason.setValue(r);
        if (customDuration != null) customDuration.setValue(custom);
    }

    private boolean valid() {
        return kind == Kind.KICK || !reasonText.isBlank();
    }

    private void confirm() {
        if (!valid()) {
            Sounds.play(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f, 0.6f);
            return;
        }
        String r = reasonText.trim().replace('\n', ' ');
        String cmd = switch (kind) {
            case WARN -> "warn " + target + " " + r;
            case MUTE -> "mute " + target + " " + duration + (r.isEmpty() ? "" : " " + r);
            case KICK -> "kick " + target + (r.isEmpty() ? "" : " " + r);
            case BAN -> "ban " + target + " " + duration + (r.isEmpty() ? "" : " " + r);
            case NOTE -> "note " + target + " " + r;
        };
        PanelScreen.run(cmd);
        if (kind != Kind.BAN) Sounds.confirm();
        minecraft.getToasts().addToast(new ModToast(kind == Kind.WARN ? 2 : 3, (kind == Kind.NOTE ? "Nota" : kind.verb) + ": " + target,
                (kind.timed ? label(duration) + " · " : "") + (r.isEmpty() ? "Sin motivo" : r)));
        minecraft.setScreen(parent);
    }

    private static String label(String d) {
        for (String[] x : DURATIONS) if (x[1].equals(d)) return x[0];
        return d;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && kind != Kind.BAN) { // Enter confirms, except bans (hold the button)
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        reason.tick();
        if (customDuration != null) customDuration.tick();
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
        g.fill(left + 1, top + 1, left + w - 1, top + 3, kind.color);
        g.renderItem(new ItemStack(kind.icon), left + 10, top + 10);
        g.drawString(font, Component.literal(kind.verb + " a ").append(Component.literal(target).withStyle(st -> st.withBold(true))),
                left + 32, top + 14, Theme.TEXT, false);
        g.drawString(font, "Motivo", left + 10, top + 34, Theme.MUTED, false);

        Theme.panel(g, reason.getX() - 3, reason.getY() - 4, reason.getWidth() + 6, 18, Theme.PANEL);
        if (kind.timed) {
            int dy = customDuration.getY();
            g.drawString(font, "Duración: " + (duration.equals("perm") ? "§cpermanente" : "§f" + label(duration)), left + 10, dy, Theme.MUTED, false);
            Theme.panel(g, customDuration.getX() - 3, dy - 4, customDuration.getWidth() + 6, 18, Theme.PANEL);
        }
        String summary = summary();
        g.drawString(font, Theme.fit(font, summary, w - 20), left + 10, top + h - 43, valid() ? Theme.MUTED : Theme.RED, false);
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    private String summary() {
        if (!valid()) return "Falta el motivo";
        String r = reasonText.isBlank() ? "sin motivo" : "«" + reasonText.trim() + "»";
        return switch (kind) {
            case WARN -> target + " recibirá un aviso: " + r;
            case MUTE -> target + " no podrá hablar " + (duration.equals("perm") ? "nunca más" : "durante " + label(duration)) + ": " + r;
            case KICK -> target + " será expulsado: " + r;
            case BAN -> target + " y su Discord no podrán entrar " + (duration.equals("perm") ? "nunca" : "durante " + label(duration)) + ": " + r;
            case NOTE -> "Nota interna para el staff (" + target + " no se entera): " + r;
        };
    }
}
