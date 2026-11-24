package es.jonaykb.kb_moderation.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Texts are built on the server (the client may not have this mod), with & color codes like the config. */
public final class Text {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private Text() {
    }

    public static final String PREFIX = "&8[&cMod&8] &r";

    /** "&cHola &#FF8800mundo" → colored component. Codes: &0-&f, &k-&o, &r, &#RRGGBB. "\n" stays a line break. */
    public static MutableComponent legacy(String s) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                if (code == '#' && i + 7 < s.length() && s.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    flush(out, buf, style);
                    style = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(s.substring(i + 2, i + 8), 16)));
                    i += 7;
                    continue;
                }
                ChatFormatting f = ChatFormatting.getByCode(code);
                if (f != null) {
                    flush(out, buf, style);
                    style = f == ChatFormatting.RESET ? Style.EMPTY : f.isColor() ? Style.EMPTY.applyFormat(f) : style.applyFormat(f);
                    i++;
                    continue;
                }
            }
            buf.append(ch);
        }
        flush(out, buf, style);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder buf, Style style) {
        if (buf.isEmpty()) return;
        out.append(Component.literal(buf.toString()).setStyle(style));
        buf.setLength(0);
    }

    public static String fill(String template, Map<String, String> values) {
        String s = template;
        for (Map.Entry<String, String> e : values.entrySet()) s = s.replace("{" + e.getKey() + "}", e.getValue());
        return s;
    }

    public static MutableComponent msg(String s) {
        return legacy(PREFIX + s);
    }

    public static MutableComponent error(String s) {
        return legacy(PREFIX + "&c" + s);
    }

    /** A clickable "[label]" that runs a command, with the command as tooltip. */
    public static MutableComponent button(String label, String command, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command).withStyle(ChatFormatting.GRAY))));
    }

    public static String date(long ms) {
        return DATE.format(Instant.ofEpochMilli(ms));
    }

    /** Escapes & so player-typed text cannot inject colors into legacy templates. */
    public static String plain(String s) {
        return s == null ? "" : s.replace('&', '＆');
    }
}
