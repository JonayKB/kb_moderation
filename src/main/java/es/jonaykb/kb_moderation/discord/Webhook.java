package es.jonaykb.kb_moderation.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import es.jonaykb.kb_moderation.config.ModConfig;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Posts embeds to Discord webhooks, off the server thread. Failures are only logged. */
public final class Webhook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private Webhook() {
    }

    public static final class Embed {
        final String title;
        final int color;
        final Map<String, String> fields = new LinkedHashMap<>();
        String description;

        public Embed(String title, int color) {
            this.title = title;
            this.color = color;
        }

        public Embed field(String name, String value) {
            if (value != null && !value.isBlank()) fields.put(name, value);
            return this;
        }

        public Embed description(String d) {
            this.description = d;
            return this;
        }
    }

    public static void sanction(Embed e) {
        post(ModConfig.WEBHOOK.get(), null, e);
    }

    public static void report(Embed e) {
        String url = ModConfig.REPORTS_WEBHOOK.get();
        if (url.isBlank()) url = ModConfig.WEBHOOK.get();
        post(url, ModConfig.REPORTS_PING.get(), e);
    }

    private static void post(String url, String content, Embed e) {
        if (url == null || url.isBlank()) return;
        JsonObject embed = new JsonObject();
        embed.addProperty("title", e.title);
        embed.addProperty("color", e.color);
        if (e.description != null) embed.addProperty("description", cut(e.description, 4000));
        JsonArray fields = new JsonArray();
        e.fields.forEach((k, v) -> {
            JsonObject f = new JsonObject();
            f.addProperty("name", k);
            f.addProperty("value", cut(v, 1000));
            f.addProperty("inline", v.length() < 40);
            fields.add(f);
        });
        embed.add("fields", fields);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", ModConfig.SERVER_NAME.get());
        embed.add("footer", footer);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject body = new JsonObject();
        if (content != null && !content.isBlank()) body.addProperty("content", content);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        // Only role pings written in the config may ping; names and reasons typed by players never do.
        JsonObject mentions = new JsonObject();
        JsonArray parse = new JsonArray();
        parse.add("roles");
        mentions.add("parse", parse);
        body.add("allowed_mentions", mentions);

        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "kb_moderation")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Invalid Discord webhook URL in kb_moderation config");
            return;
        }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
            if (err != null) LOGGER.warn("Could not post to the Discord webhook: {}", err.toString());
            else if (res.statusCode() >= 300) LOGGER.warn("Discord webhook answered {}: {}", res.statusCode(), cut(res.body(), 200));
        });
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
