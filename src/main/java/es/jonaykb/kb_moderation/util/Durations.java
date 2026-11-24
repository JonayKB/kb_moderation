package es.jonaykb.kb_moderation.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {
    /** Returned by {@link #parse} for "perm"/"permanente". */
    public static final long PERMANENT = 0;
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])");

    private Durations() {
    }

    /** "30m", "12h", "7d", "2w", "1d12h" → ms; "perm" → {@link #PERMANENT}; invalid → -1. */
    public static long parse(String s) {
        s = s.toLowerCase(Locale.ROOT).trim();
        if (s.equals("perm") || s.equals("permanente") || s.equals("permanent") || s.equals("p")) return PERMANENT;
        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            end = m.end();
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L;
            };
        }
        return end == s.length() && total > 0 ? total : -1;
    }

    public static boolean isDuration(String s) {
        return parse(s) >= 0;
    }

    /** 90061000 → "1d 1h 1m". */
    public static String format(long ms) {
        if (ms <= 0) return "0m";
        long s = ms / 1000;
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        StringBuilder b = new StringBuilder();
        if (d > 0) b.append(d).append("d ");
        if (h > 0) b.append(h).append("h ");
        if (m > 0) b.append(m).append("m ");
        if (b.isEmpty()) b.append(sec).append("s");
        return b.toString().trim();
    }
}
