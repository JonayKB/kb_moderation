package es.jonaykb.kb_moderation.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Colors and drawing helpers shared by every screen, so they all look like one tool. */
public final class Theme {
    public static final int BG = 0xF0141821;
    public static final int PANEL = 0xFF1C212C;
    public static final int PANEL_2 = 0xFF232938;
    public static final int BORDER = 0xFF323A4D;
    public static final int TEXT = 0xFFE8ECF4;
    public static final int MUTED = 0xFF8A93A6;
    public static final int FAINT = 0xFF5A6275;
    public static final int ACCENT = 0xFF5B8CFF;

    public static final int GREEN = 0xFF3DDC84;
    public static final int YELLOW = 0xFFFFC857;
    public static final int ORANGE = 0xFFFF8A3D;
    public static final int RED = 0xFFFF5A5F;
    public static final int DARK_RED = 0xFFC0392B;
    public static final int CYAN = 0xFF4FD1E8;
    public static final int PINK = 0xFFE879F9;
    public static final int PURPLE = 0xFFA78BFA;

    private Theme() {
    }

    /** A panel with a 1px border and slightly rounded corners. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x + 1, y, x + w - 1, y + h, BORDER);
        g.fill(x, y + 1, x + w, y + h - 1, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** A rounded "pill" with centered text; returns its width. */
    public static int pill(GuiGraphics g, Font font, String text, int x, int y, int color) {
        int w = font.width(text) + 8;
        int bg = (color & 0x00FFFFFF) | 0x40000000;
        g.fill(x + 1, y, x + w - 1, y + 11, bg);
        g.fill(x, y + 1, x + w, y + 10, bg);
        g.drawString(font, text, x + 4, y + 2, color, false);
        return w;
    }

    public static int pillWidth(Font font, String text) {
        return font.width(text) + 8;
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** The player's skin face if this client knows them (online and visible), else the default skin. */
    public static void face(GuiGraphics g, UUID uuid, int x, int y, int size) {
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(uuid);
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            PlayerInfo info = conn.getPlayerInfo(uuid);
            if (info != null) skin = info.getSkinLocation();
        }
        PlayerFaceRenderer.draw(g, skin, x, y, size);
    }

    /** "hace 3 h" style. */
    public static String ago(long ms) {
        long s = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
        if (s < 60) return "hace un momento";
        if (s < 3600) return "hace " + s / 60 + " min";
        if (s < 86400) return "hace " + s / 3600 + " h";
        return "hace " + s / 86400 + " d";
    }

    /** "2 h 5 min" until ms; "permanente" when 0. */
    public static String until(long ms) {
        if (ms == 0) return "permanente";
        long s = Math.max(0, (ms - System.currentTimeMillis()) / 1000);
        if (s < 3600) return Math.max(1, s / 60) + " min";
        if (s < 86400) return s / 3600 + " h " + (s % 3600) / 60 + " min";
        return s / 86400 + " d " + (s % 86400) / 3600 + " h";
    }

    /** Cuts text to fit, with an ellipsis. */
    public static String fit(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, width - font.width("…")) + "…";
    }

    /** Smooth 0→1 over durationMs since start (ease-out). */
    public static float ease(long start, long durationMs) {
        float t = Math.min(1f, (System.currentTimeMillis() - start) / (float) durationMs);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }
}
