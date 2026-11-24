package es.jonaykb.kb_moderation.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import es.jonaykb.kb_moderation.config.ModConfig;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.data.ModData.Outbox;
import es.jonaykb.kb_moderation.data.ModData.Report;
import es.jonaykb.kb_moderation.data.ModData.Sanction;
import es.jonaykb.kb_moderation.data.ModData.Type;
import es.jonaykb.kb_moderation.link.DiscordLink;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps sanctions and reports in step with KB Discord Bot.
 * <ul>
 *   <li>Out: every sanction and report made here goes to the bot, in order, through a queue saved with the data
 *   (nothing is lost if the bot is down; retries are idempotent on the bot side by {@code ref}).</li>
 *   <li>In: every few seconds the server asks the bot for what happened since its cursor (sanctions from the panel or
 *   made by hand in Discord, sanctions from other servers, removed cases, reports closed in the panel) and applies it.</li>
 * </ul>
 * HTTP runs off the server thread; everything that touches game data goes back to it with {@code server.execute}.
 */
public final class BotSync {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static boolean sending;
    private static boolean polling;
    private static long nextPoll;
    private static long nextProblemLog;
    private static String lastProblem;

    private BotSync() {
    }

    // ───────────────────────────── config ─────────────────────────────

    private record Endpoint(String base, String key) {
    }

    private static Endpoint endpoint() {
        if (!ModConfig.BOT_SYNC.get()) return null;
        String url = ModConfig.BOT_URL.get().trim();
        if (url.isEmpty()) {
            // Same bot as KB Discord Auth's stats: .../minecraft/stats → .../minecraft/moderation
            String stats = DiscordLink.authConfig("STATS_ENDPOINT");
            if (stats.endsWith("/stats")) url = stats.substring(0, stats.length() - "/stats".length()) + "/moderation";
        }
        String key = ModConfig.BOT_API_KEY.get().trim();
        if (key.isEmpty()) key = DiscordLink.authConfig("STATS_API_KEY");
        if (url.isEmpty() || key.isEmpty()) return null;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return new Endpoint(url, key);
    }

    public static boolean enabled() {
        return endpoint() != null;
    }

    // ───────────────────────────── out: queue ─────────────────────────────

    private static void enqueue(String path, Map<String, Object> body, Integer sanctionId, Integer deleteOf) {
        if (!ModConfig.BOT_SYNC.get()) return;
        Outbox o = new Outbox();
        o.path = path;
        o.body = body;
        o.sanctionId = sanctionId;
        o.deleteOf = deleteOf;
        ModData.get().outbox().add(o);
        ModData.get().save();
    }

    private static Map<String, Object> moderator(UUID staff, String staffName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("discordId", staff == null ? null : DiscordLink.discordId(staff));
        m.put("name", staffName);
        return m;
    }

    /** A sanction just given on this server. Only accounts linked to Discord can be shared. */
    public static void sanctionGiven(Sanction s) {
        if (s.origin != null || s.discordId == null) return;
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("ref", "s" + s.id);
        b.put("action", switch (s.type) {
            case WARN -> "warn";
            case NOTE -> "note";
            case MUTE -> "timeout";
            case KICK -> "kick";
            case BAN -> "ban";
        });
        b.put("userId", s.discordId);
        b.put("mcName", s.targetName);
        b.put("reason", s.reason);
        if (s.type == Type.MUTE) b.put("durationMs", s.permanent() ? 0 : s.expiresAt - s.createdAt);
        if (s.type == Type.BAN) b.put("expiresAt", s.permanent() ? null : s.expiresAt);
        b.put("moderator", moderator(s.staff, s.staffName));
        enqueue("/actions", b, s.id, null);
    }

    /** Mutes or bans lifted here (unmute/unban): one message for the Discord account. */
    public static void lifted(Type type, String discordId, String mcName, int firstSanctionId, UUID staff, String staffName) {
        if (discordId == null) return;
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("ref", "lift" + firstSanctionId + "-" + System.currentTimeMillis());
        b.put("action", type == Type.MUTE ? "untimeout" : "unban");
        b.put("userId", discordId);
        b.put("mcName", mcName);
        b.put("reason", "");
        b.put("moderator", moderator(staff, staffName));
        enqueue("/actions", b, null, null);
    }

    /** A warning or note removed here: its case is removed in the bot too. */
    public static void removed(Sanction s, UUID staff, String staffName) {
        if (s.discordId == null || (s.botCaseId == null && s.origin != null)) return;
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("ref", "del" + s.id);
        b.put("action", "delete");
        b.put("userId", s.discordId);
        b.put("reason", "");
        b.put("moderator", moderator(staff, staffName));
        enqueue("/actions", b, null, s.id);
    }

    public static void reportCreated(Report r, String reporterDiscordId, String targetDiscordId) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("ref", "r" + r.id);
        b.put("reporterName", r.reporterName);
        b.put("reporterDiscordId", reporterDiscordId);
        b.put("targetName", r.targetName);
        b.put("targetDiscordId", targetDiscordId);
        b.put("reason", r.reason);
        b.put("location", r.dimension.replace("minecraft:", "") + " " + r.x + " " + r.y + " " + r.z);
        enqueue("/reports", b, null, null);
    }

    public static void reportClosed(Report r, String by) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("ref", "r" + r.id);
        b.put("by", by);
        enqueue("/reports/close", b, null, null);
    }

    // ───────────────────────────── tick ─────────────────────────────

    /** Called once a second on the server thread. */
    public static void tick(MinecraftServer server) {
        Endpoint ep = endpoint();
        if (ep == null || !ModData.loaded()) return;
        long now = System.currentTimeMillis();
        if (!sending) sendNext(server, ep, now);
        if (!polling && now >= nextPoll) poll(server, ep);
    }

    private static void sendNext(MinecraftServer server, Endpoint ep, long now) {
        var outbox = ModData.get().outbox();
        if (outbox.isEmpty()) return;
        Outbox o = outbox.get(0);
        if (o.nextTry > now) return;
        Map<String, Object> body = o.body;
        if (o.deleteOf != null) {
            Sanction target = ModData.get().sanction(o.deleteOf);
            if (target == null || target.botCaseId == null) {
                // Never reached the bot (no Discord, or its own send was dropped): nothing to remove there.
                outbox.remove(0);
                ModData.get().save();
                return;
            }
            body = new LinkedHashMap<>(body);
            body.put("caseId", target.botCaseId);
        }
        sending = true;
        HttpRequest req = HttpRequest.newBuilder(URI.create(ep.base() + o.path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + ep.key())
                .header("User-Agent", "kb_moderation")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> server.execute(() -> {
            sending = false;
            if (!ModData.loaded() || ModData.get().outbox().isEmpty() || ModData.get().outbox().get(0) != o) return;
            int status = err != null ? -1 : res.statusCode();
            if (status >= 200 && status < 300) {
                ModData.get().outbox().remove(0);
                if (o.sanctionId != null) {
                    Integer caseId = intField(res.body(), "caseId");
                    Sanction s = ModData.get().sanction(o.sanctionId);
                    if (s != null && caseId != null) s.botCaseId = caseId;
                }
                ModData.get().save();
                lastProblem = null;
            } else if (status == 400 || status == 404 || status == 413) {
                // The bot will never accept this one: drop it so the rest can go.
                LOGGER.error("KB Discord Bot rejected {} ({}): {}. Dropped.", o.path, status, res == null ? "" : cut(res.body()));
                ModData.get().outbox().remove(0);
                ModData.get().save();
            } else {
                o.attempts++;
                o.nextTry = System.currentTimeMillis() + Math.min(300_000L, 5_000L * (1L << Math.min(6, o.attempts)));
                problem(err != null ? err.toString() : status + " " + cut(res.body()));
            }
        }));
    }

    private static void poll(MinecraftServer server, Endpoint ep) {
        polling = true;
        long after = Math.max(0, ModData.get().botCursor());
        HttpRequest req = HttpRequest.newBuilder(URI.create(ep.base() + "/feed?after=" + after))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + ep.key())
                .header("User-Agent", "kb_moderation")
                .GET()
                .build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> server.execute(() -> {
            polling = false;
            nextPoll = System.currentTimeMillis() + ModConfig.BOT_POLL_SECONDS.get() * 1000L;
            if (!ModData.loaded()) return;
            if (err != null || res.statusCode() != 200) {
                nextPoll = System.currentTimeMillis() + 30_000L;
                problem(err != null ? err.toString() : res.statusCode() + " " + cut(res.body()));
                return;
            }
            try {
                JsonObject feed = JsonParser.parseString(res.body()).getAsJsonObject();
                boolean firstSync = ModData.get().botCursor() < 0;
                JsonArray events = feed.getAsJsonArray("events");
                for (JsonElement e : events) BotEvents.apply(server, e.getAsJsonObject(), firstSync);
                ModData.get().setBotCursor(feed.get("cursor").getAsLong());
                ModData.get().save();
                if (firstSync) LOGGER.info("KB Discord Bot: imported {} cases from its history", events.size());
                // A full page: there is more waiting, ask again right away.
                if (events.size() >= 200) nextPoll = 0;
                lastProblem = null;
            } catch (RuntimeException ex) {
                problem("bad feed: " + ex);
            }
        }));
    }

    private static Integer intField(String json, String field) {
        try {
            JsonElement e = JsonParser.parseString(json).getAsJsonObject().get(field);
            return e == null || e.isJsonNull() ? null : e.getAsInt();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Logs a problem with the bot at most every 10 minutes (and when it changes). */
    private static void problem(String what) {
        long now = System.currentTimeMillis();
        if (what.equals(lastProblem) && now < nextProblemLog) return;
        lastProblem = what;
        nextProblemLog = now + 600_000L;
        LOGGER.warn("Could not reach KB Discord Bot (will retry): {}", what);
    }

    private static String cut(String s) {
        return s == null ? "" : s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    /** Number of things waiting to be sent (for /modinfo-like status). */
    public static int pending() {
        return ModData.loaded() ? ModData.get().outbox().size() : 0;
    }

    /** Server stopped: forget in-flight state (the queue itself is saved with the data). */
    public static void reset() {
        sending = false;
        polling = false;
        nextPoll = 0;
    }
}
