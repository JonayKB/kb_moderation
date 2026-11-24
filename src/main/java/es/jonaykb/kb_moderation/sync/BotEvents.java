package es.jonaykb.kb_moderation.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.data.ModData.Sanction;
import es.jonaykb.kb_moderation.data.ModData.Type;
import es.jonaykb.kb_moderation.link.DiscordLink;
import es.jonaykb.kb_moderation.sanction.Sanctions;
import es.jonaykb.kb_moderation.util.Text;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.List;

/** Applies one event of KB Discord Bot's feed on the server thread. */
final class BotEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BotEvents() {
    }

    /** @param history the first sync: old cases are recorded, but nobody is kicked or messaged for them. */
    static void apply(MinecraftServer server, JsonObject e, boolean history) {
        String kind = str(e, "kind");
        switch (kind == null ? "" : kind) {
            case "case" -> applyCase(server, e.getAsJsonObject("case"), history);
            case "delete" -> applyDelete(server, e.get("caseId").getAsInt());
            case "report_closed" -> applyReportClosed(server, str(e, "reportRef"), str(e, "closedBy"));
            default -> LOGGER.debug("Unknown event from KB Discord Bot: {}", kind);
        }
    }

    private static void applyCase(MinecraftServer server, JsonObject c, boolean history) {
        if (c == null) return;
        int caseId = c.get("id").getAsInt();
        if (ModData.get().sanctionByBotCase(caseId) != null) return; // already here
        String action = str(c, "action");
        String discordId = str(c, "userId");
        if (action == null || discordId == null) return;

        // Who it is in the game: the account named in the case, or the first account linked to that Discord.
        List<DiscordLink.Account> accounts = DiscordLink.accountsOf(discordId);
        String mcName = str(c, "mcName");
        String tag = str(c, "userTag");
        DiscordLink.Account account = accounts.stream().filter(a -> a.name() != null && a.name().equalsIgnoreCase(mcName)).findFirst()
                .orElse(accounts.isEmpty() ? null : accounts.get(0));

        String source = str(c, "source");
        String staff = str(c, "moderatorName");
        if (staff == null) staff = "discord".equals(source) ? "Discord" : "Panel del bot";
        String reason = str(c, "reason");
        long createdAt = c.has("createdAt") ? c.get("createdAt").getAsLong() : System.currentTimeMillis();

        Sanctions.Incoming in = new Sanctions.Incoming(
                caseId, "minecraft".equals(source) ? "minecraft" : "discord", discordId,
                account == null ? null : account.uuid(),
                mcName != null ? mcName : account != null ? account.name() : "@" + (tag == null ? discordId : tag),
                staff, reason == null || reason.isBlank() ? "Sin motivo" : reason, createdAt, history);

        switch (action) {
            case "warn" -> Sanctions.importSanction(server, in, Type.WARN, 0);
            case "note" -> Sanctions.importSanction(server, in, Type.NOTE, 0);
            case "kick" -> Sanctions.importSanction(server, in, Type.KICK, 0);
            case "timeout" -> {
                long duration = optLong(c, "durationMs");
                // A timeout ends on its own in Discord: here it ends at the same time (or never, if it came without one).
                long expires = duration > 0 ? createdAt + duration : 0;
                if (expires != 0 && expires <= System.currentTimeMillis()) Sanctions.importSanction(server, in.asHistory(), Type.MUTE, expires);
                else Sanctions.importSanction(server, in, Type.MUTE, expires);
            }
            case "ban" -> {
                long expires = optLong(c, "expiresAt");
                if (expires != 0 && expires <= System.currentTimeMillis()) Sanctions.importSanction(server, in.asHistory(), Type.BAN, expires);
                else Sanctions.importSanction(server, in, Type.BAN, expires);
            }
            case "untimeout" -> Sanctions.liftFromBot(server, Type.MUTE, discordId, staff, history);
            case "unban" -> Sanctions.liftFromBot(server, Type.BAN, discordId, staff, history);
            default -> LOGGER.debug("Unknown case action from KB Discord Bot: {}", action);
        }
    }

    private static void applyDelete(MinecraftServer server, int caseId) {
        Sanction s = ModData.get().sanctionByBotCase(caseId);
        if (s == null || s.revoked) return;
        s.revoked = true;
        s.revokedBy = "Panel del bot";
        s.revokedAt = System.currentTimeMillis();
        ModData.get().save();
        if (s.type == Type.WARN) {
            Sanctions.notifyStaff(server, Text.msg("&7Aviso &f#" + s.id + " &7de &f" + s.targetName + " &7retirado desde el panel del bot."));
        }
    }

    private static void applyReportClosed(MinecraftServer server, String ref, String by) {
        if (ref == null || !ref.startsWith("r")) return;
        int id;
        try {
            id = Integer.parseInt(ref.substring(1));
        } catch (NumberFormatException ex) {
            return;
        }
        ModData.Report r = ModData.get().report(id);
        if (r == null || r.closed) return;
        r.closed = true;
        r.closedBy = by == null ? "Panel del bot" : by + " (panel)";
        ModData.get().save();
        Sanctions.notifyStaff(server, Text.msg("&aReporte #" + id + " &7cerrado desde el panel del bot por &f" + r.closedBy));
    }

    private static String str(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static long optLong(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e == null || e.isJsonNull() ? 0 : e.getAsLong();
    }
}
