package es.jonaykb.kb_moderation.sanction;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import es.jonaykb.kb_moderation.config.ModConfig;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.data.ModData.Sanction;
import es.jonaykb.kb_moderation.data.ModData.Type;
import es.jonaykb.kb_moderation.discord.Webhook;
import es.jonaykb.kb_moderation.link.DiscordLink;
import es.jonaykb.kb_moderation.network.Net;
import es.jonaykb.kb_moderation.sync.BotSync;
import es.jonaykb.kb_moderation.perm.Perm;
import es.jonaykb.kb_moderation.perm.Perms;
import es.jonaykb.kb_moderation.util.Durations;
import es.jonaykb.kb_moderation.util.Targets;
import es.jonaykb.kb_moderation.util.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Sanctions {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String AUTO = "Automático";

    private Sanctions() {
    }

    // ───────────────────────────── giving ─────────────────────────────

    private static Sanction create(Type type, Targets t, CommandSourceStack staff, String reason, long durationMs) {
        Sanction s = new Sanction();
        s.type = type;
        s.target = t.uuid();
        s.targetName = t.name();
        s.discordId = t.discordId();
        s.staff = staff == null || staff.getPlayer() == null ? null : staff.getPlayer().getUUID();
        s.staffName = staff == null ? AUTO : staff.getTextName();
        s.reason = reason == null || reason.isBlank() ? "Sin motivo" : reason.trim();
        s.createdAt = System.currentTimeMillis();
        s.expiresAt = durationMs > 0 ? s.createdAt + durationMs : 0;
        ModData.get().addSanction(s);
        BotSync.sanctionGiven(s);
        return s;
    }

    public static Sanction note(MinecraftServer server, Targets t, CommandSourceStack staff, String text) {
        Sanction s = create(Type.NOTE, t, staff, text, 0);
        notifyStaff(server, Text.msg("&7Nota &f#" + s.id + " &7sobre &f" + t.name() + " &7por &f" + Text.plain(s.staffName) + "&7: " + Text.plain(s.reason)));
        return s;
    }

    public static Sanction warn(MinecraftServer server, Targets t, CommandSourceStack staff, String reason) {
        Sanction s = create(Type.WARN, t, staff, reason, 0);
        int count = activeWarns(t.uuid(), s.discordId);
        if (t.online() != null) {
            t.online().sendSystemMessage(Text.legacy(Text.fill(ModConfig.WARNED_MESSAGE.get(), Map.of(
                    "staff", Text.plain(s.staffName), "reason", Text.plain(s.reason), "count", String.valueOf(count)))));
            Net.toast(t.online(), 2, "Aviso del staff", s.reason);
        }
        announce(server, s, "&e" + t.name() + " &7ha recibido un aviso (" + count + " activos)");
        escalate(server, t, count);
        return s;
    }

    public static Sanction mute(MinecraftServer server, Targets t, CommandSourceStack staff, String reason, long durationMs) {
        Sanction s = create(Type.MUTE, t, staff, reason, durationMs);
        for (ServerPlayer p : onlineAccounts(server, t.uuid(), s.discordId)) {
            p.sendSystemMessage(mutedMessage(s));
            Net.toast(p, 2, "Estás silenciado", s.permanent() ? s.reason : Durations.format(s.expiresAt - s.createdAt) + " · " + s.reason);
        }
        announce(server, s, "&6" + t.name() + " &7ha sido silenciado " + durationText(s));
        return s;
    }

    public static Sanction kick(MinecraftServer server, Targets t, CommandSourceStack staff, String reason) {
        Sanction s = create(Type.KICK, t, staff, reason, 0);
        if (t.online() != null) {
            t.online().connection.disconnect(Text.legacy(Text.fill(ModConfig.KICK_SCREEN.get(), Map.of(
                    "reason", Text.plain(s.reason), "staff", Text.plain(s.staffName)))));
        }
        announce(server, s, "&c" + t.name() + " &7ha sido expulsado");
        return s;
    }

    public static Sanction ban(MinecraftServer server, Targets t, CommandSourceStack staff, String reason, long durationMs) {
        Sanction s = create(Type.BAN, t, staff, reason, durationMs);
        // Also every other Minecraft account of the same Discord user that is online right now.
        for (ServerPlayer p : onlineAccounts(server, t.uuid(), s.discordId)) p.connection.disconnect(banScreen(s));
        announce(server, s, "&4" + t.name() + " &7ha sido baneado " + durationText(s));
        return s;
    }

    /** Lifts every active mute/ban on the account and its Discord account. Returns how many. */
    public static int lift(MinecraftServer server, Type type, Targets t, CommandSourceStack staff) {
        List<Sanction> active = ModData.get().allActive(type, t.uuid(), t.discordId());
        long now = System.currentTimeMillis();
        for (Sanction s : active) {
            s.revoked = true;
            s.revokedBy = staff.getTextName();
            s.revokedAt = now;
        }
        if (active.isEmpty()) return 0;
        ModData.get().save();
        BotSync.lifted(type, t.discordId(), t.name(), active.get(0).id, staff.getPlayer() == null ? null : staff.getPlayer().getUUID(), staff.getTextName());
        String what = type == Type.MUTE ? "desilenciado" : "desbaneado";
        notifyStaff(server, Text.msg("&a" + t.name() + " &7ha sido " + what + " por &f" + staff.getTextName()));
        if (type == Type.MUTE && t.online() != null) t.online().sendSystemMessage(Text.msg("&aYa puedes volver a hablar."));
        Webhook.sanction(new Webhook.Embed(type == Type.MUTE ? "🔊 Desilenciado" : "✅ Desbaneado", 0x57F287)
                .field("Jugador", t.name()).field("Por", staff.getTextName())
                .field("Sanciones levantadas", active.stream().map(s -> "#" + s.id).reduce((a, b) -> a + ", " + b).orElse("")));
        LOGGER.info("[kb_moderation] {} lifted {} {} of {}", staff.getTextName(), active.size(), type, t.name());
        return active.size();
    }

    // ───────────────────────────── warnings ─────────────────────────────

    public static int activeWarns(UUID uuid, String discordId) {
        int days = ModConfig.WARN_EXPIRY_DAYS.get();
        long since = days == 0 ? 0 : System.currentTimeMillis() - days * 86_400_000L;
        int n = 0;
        for (Sanction s : ModData.get().historyOf(uuid, discordId)) {
            if (s.type == Type.WARN && !s.revoked && s.createdAt >= since) n++;
        }
        return n;
    }

    private static void escalate(MinecraftServer server, Targets t, int count) {
        for (String rule : ModConfig.WARN_ESCALATION.get()) {
            String[] p = rule.split("\\|");
            int at;
            try {
                at = Integer.parseInt(p[0].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (at != count) continue;
            long duration = Durations.parse(p[2]);
            if (duration < 0) {
                LOGGER.warn("Bad duration '{}' in warnEscalation", p[2]);
                continue;
            }
            String reason = "Acumular " + count + " avisos";
            switch (p[1].trim().toLowerCase()) {
                case "mute" -> mute(server, t, null, reason, duration);
                case "ban" -> ban(server, t, null, reason, duration);
                default -> LOGGER.warn("Unknown action '{}' in warnEscalation (use mute or ban)", p[1]);
            }
        }
    }

    // ───────────────────────────── checks ─────────────────────────────

    /** Ban screen if this account (or its Discord account) is banned, else null. Used at login and after the Discord login. */
    public static Component loginBlock(GameProfile profile) {
        if (!ModData.loaded() || profile.getId() == null) return null;
        Sanction s = ModData.get().active(Type.BAN, profile.getId(), DiscordLink.discordId(profile.getId()));
        return s == null ? null : banScreen(s);
    }

    public static Sanction activeMute(ServerPlayer p) {
        if (!ModData.loaded()) return null;
        return ModData.get().active(Type.MUTE, p.getUUID(), DiscordLink.discordId(p.getUUID()));
    }

    // ───────────────────────────── texts ─────────────────────────────

    public static String expiresText(Sanction s) {
        if (s.permanent()) return "Nunca";
        return Text.date(s.expiresAt) + " (en " + Durations.format(s.expiresAt - System.currentTimeMillis()) + ")";
    }

    private static String durationText(Sanction s) {
        return s.permanent() ? "&cpermanentemente" : "&7durante &f" + Durations.format(s.expiresAt - s.createdAt);
    }

    public static Component banScreen(Sanction s) {
        return Text.legacy(Text.fill(ModConfig.BAN_SCREEN.get(), Map.of(
                "reason", Text.plain(s.reason), "staff", Text.plain(s.staffName), "expires", expiresText(s),
                "appeal", ModConfig.APPEAL.get(), "id", String.valueOf(s.id))));
    }

    public static Component mutedMessage(Sanction s) {
        String msg = Text.fill(ModConfig.MUTED_MESSAGE.get(), Map.of(
                "reason", Text.plain(s.reason), "staff", Text.plain(s.staffName), "expires", expiresText(s)));
        String appeal = ModConfig.APPEAL.get();
        return Text.legacy(appeal.isBlank() ? msg : msg + "\n" + appeal);
    }

    public static String typeName(Type t) {
        return switch (t) {
            case WARN -> "Aviso";
            case MUTE -> "Silencio";
            case KICK -> "Expulsión";
            case BAN -> "Baneo";
            case NOTE -> "Nota";
        };
    }

    // ───────────────────────────── notify ─────────────────────────────

    private static void announce(MinecraftServer server, Sanction s, String line) {
        notifyStaff(server, Text.msg(line + " &8· &7por &f" + Text.plain(s.staffName) + " &8· &7" + Text.plain(s.reason) + " &8#" + s.id));
        int color = switch (s.type) {
            case WARN -> 0xFEE75C;
            case MUTE -> 0xE67E22;
            case KICK -> 0xED4245;
            case BAN -> 0x992D22;
            case NOTE -> 0x8F98A9;
        };
        String icon = switch (s.type) {
            case WARN -> "⚠️";
            case MUTE -> "🔇";
            case KICK -> "👢";
            case BAN -> "🔨";
            case NOTE -> "📝";
        };
        Webhook.Embed e = new Webhook.Embed(icon + " " + typeName(s.type) + " #" + s.id, color)
                .field("Jugador", s.targetName)
                .field("Discord", s.discordId == null ? null : "<@" + s.discordId + ">")
                .field("Por", s.staffName)
                .field("Motivo", s.reason);
        if (s.type == Type.MUTE || s.type == Type.BAN) {
            e.field("Duración", s.permanent() ? "Permanente" : Durations.format(s.expiresAt - s.createdAt));
        }
        Webhook.sanction(e);
        LOGGER.info("[kb_moderation] #{} {} {} by {}: {}", s.id, s.type, s.targetName, s.staffName, s.reason);
    }

    // ───────────────────────────── from KB Discord Bot ─────────────────────────────

    /** A case that came from the bot (its panel, Discord itself or another server). */
    public record Incoming(int caseId, String origin, String discordId, UUID target, String targetName, String staffName,
                           String reason, long createdAt, boolean history) {
        /** Old or already finished: record it without kicking or messaging anyone. */
        public Incoming asHistory() {
            return new Incoming(caseId, origin, discordId, target, targetName, staffName, reason, createdAt, true);
        }
    }

    /** Records a case from the bot and, unless it is history, applies it to the accounts of that Discord user. */
    public static Sanction importSanction(MinecraftServer server, Incoming in, Type type, long expiresAt) {
        Sanction s = new Sanction();
        s.type = type;
        s.target = in.target();
        s.targetName = in.targetName();
        s.discordId = in.discordId();
        s.staffName = in.staffName();
        s.reason = in.reason();
        s.createdAt = in.createdAt();
        s.expiresAt = expiresAt;
        s.origin = in.origin();
        s.botCaseId = in.caseId();
        ModData.get().addSanction(s);
        if (in.history()) return s;

        String where = "minecraft".equals(in.origin()) ? "otro servidor" : "Discord";
        List<ServerPlayer> online = onlineAccounts(server, in.target() == null ? new UUID(0, 0) : in.target(), in.discordId());
        switch (type) {
            case WARN -> {
                int count = activeWarns(in.target() == null ? new UUID(0, 0) : in.target(), in.discordId());
                for (ServerPlayer p : online) {
                    p.sendSystemMessage(Text.legacy(Text.fill(ModConfig.WARNED_MESSAGE.get(), Map.of(
                            "staff", Text.plain(s.staffName), "reason", Text.plain(s.reason), "count", String.valueOf(count)))));
                    Net.toast(p, 2, "Aviso del staff", s.reason);
                }
            }
            case MUTE -> {
                for (ServerPlayer p : online) {
                    p.sendSystemMessage(mutedMessage(s));
                    Net.toast(p, 2, "Estás silenciado", s.permanent() ? s.reason : Durations.format(s.expiresAt - System.currentTimeMillis()) + " · " + s.reason);
                }
            }
            case KICK -> {
                for (ServerPlayer p : online) p.connection.disconnect(Text.legacy(Text.fill(ModConfig.KICK_SCREEN.get(), Map.of(
                        "reason", Text.plain(s.reason), "staff", Text.plain(s.staffName)))));
            }
            case BAN -> {
                for (ServerPlayer p : online) p.connection.disconnect(banScreen(s));
            }
            case NOTE -> {
            }
        }
        notifyStaff(server, Text.msg("&8[" + where + "] &f" + typeName(type) + " &7a &f" + s.targetName
                + (type == Type.MUTE || type == Type.BAN ? " &7(" + (s.permanent() ? "permanente" : "hasta " + Text.date(s.expiresAt)) + ")" : "")
                + " &7por &f" + Text.plain(s.staffName) + "&7: " + Text.plain(s.reason) + " &8#" + s.id));
        LOGGER.info("[kb_moderation] #{} {} {} from {} (bot case #{}) by {}: {}", s.id, type, s.targetName, where, in.caseId(), s.staffName, s.reason);
        return s;
    }

    /** Unban / end of timeout that came from the bot: lift them here too, without sending it back. */
    public static void liftFromBot(MinecraftServer server, Type type, String discordId, String by, boolean history) {
        long now = System.currentTimeMillis();
        // That Discord account, and every Minecraft account linked to it (sanctions given before the link changed).
        java.util.Set<UUID> accounts = new java.util.HashSet<>();
        for (DiscordLink.Account a : DiscordLink.accountsOf(discordId)) accounts.add(a.uuid());
        List<Sanction> active = new ArrayList<>();
        for (Sanction s : ModData.get().sanctions()) {
            if (s.type == type && s.active(now) && (discordId.equals(s.discordId) || (s.target != null && accounts.contains(s.target)))) active.add(s);
        }
        if (active.isEmpty()) return;
        for (Sanction s : active) {
            s.revoked = true;
            s.revokedBy = by;
            s.revokedAt = now;
        }
        ModData.get().save();
        if (history) return;
        String name = active.get(0).targetName;
        notifyStaff(server, Text.msg("&8[Discord] &a" + name + " &7" + (type == Type.MUTE ? "ya puede hablar" : "ha sido desbaneado") + " &7por &f" + Text.plain(by)));
        if (type == Type.MUTE) {
            for (ServerPlayer p : onlineAccounts(server, new UUID(0, 0), discordId)) p.sendSystemMessage(Text.msg("&aYa puedes volver a hablar."));
        }
    }

    /** Staff online (staffchat permission) and the server log. */
    public static void notifyStaff(MinecraftServer server, Component msg) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Perms.has(p, Perm.STAFFCHAT)) p.sendSystemMessage(msg);
        }
        server.sendSystemMessage(msg);
    }

    public static List<ServerPlayer> onlineAccounts(MinecraftServer server, UUID uuid, String discordId) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(uuid) || (discordId != null && discordId.equals(DiscordLink.discordId(p.getUUID())))) out.add(p);
        }
        return out;
    }
}
