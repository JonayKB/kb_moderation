package es.jonaykb.kb_moderation.staff;

import es.jonaykb.kb_moderation.config.ModConfig;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.data.ModData.Report;
import es.jonaykb.kb_moderation.discord.Webhook;
import es.jonaykb.kb_moderation.network.Net;
import es.jonaykb.kb_moderation.link.DiscordLink;
import es.jonaykb.kb_moderation.sync.BotSync;
import es.jonaykb.kb_moderation.perm.Perm;
import es.jonaykb.kb_moderation.perm.Perms;
import es.jonaykb.kb_moderation.util.Targets;
import es.jonaykb.kb_moderation.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Reports {
    /** reporter → (target → last report time), so one player cannot spam reports about another. */
    private static final Map<UUID, Map<UUID, Long>> lastReport = new HashMap<>();

    private Reports() {
    }

    /** Seconds the reporter still has to wait, or 0. */
    public static long cooldownLeft(ServerPlayer reporter, UUID target) {
        long cd = ModConfig.REPORT_COOLDOWN.get() * 1000L;
        Long last = lastReport.getOrDefault(reporter.getUUID(), Map.of()).get(target);
        if (last == null) return 0;
        long left = last + cd - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    public static Report create(MinecraftServer server, ServerPlayer reporter, Targets target, String reason) {
        lastReport.computeIfAbsent(reporter.getUUID(), u -> new HashMap<>()).put(target.uuid(), System.currentTimeMillis());
        Report r = new Report();
        r.reporter = reporter.getUUID();
        r.reporterName = reporter.getGameProfile().getName();
        r.target = target.uuid();
        r.targetName = target.name();
        r.reason = reason;
        r.at = System.currentTimeMillis();
        ServerPlayer where = target.online() != null ? target.online() : reporter;
        r.dimension = where.level().dimension().location().toString();
        r.x = where.getBlockX();
        r.y = where.getBlockY();
        r.z = where.getBlockZ();
        ModData.get().addReport(r);
        BotSync.reportCreated(r, DiscordLink.discordId(reporter.getUUID()), target.discordId());

        MutableComponent line = Text.msg("&c&lReporte #" + r.id + " &7de &f" + r.reporterName + " &7sobre &f" + r.targetName
                + "&7: &f").append(Component.literal(reason)).append(" ");
        line.append(actions(r));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!Perms.has(p, Perm.REPORTS)) continue;
            p.sendSystemMessage(line);
            Net.toast(p, 1, "Reporte de " + r.reporterName, r.targetName + ": " + reason);
        }
        server.sendSystemMessage(line);

        Webhook.report(new Webhook.Embed("🚩 Reporte #" + r.id, 0xED4245)
                .field("Reportado", r.targetName + (target.online() == null ? " (desconectado)" : ""))
                .field("Por", r.reporterName)
                .field("Motivo", reason)
                .field("Dónde", r.dimension + " " + r.x + " " + r.y + " " + r.z));
        return r;
    }

    public static MutableComponent actions(Report r) {
        return Component.empty()
                .append(Text.button("TP", "/mtp " + r.targetName, ChatFormatting.AQUA)).append(" ")
                .append(Text.button("Inv", "/invsee " + r.targetName, ChatFormatting.GOLD)).append(" ")
                .append(Text.button("Info", "/modinfo " + r.targetName, ChatFormatting.YELLOW)).append(" ")
                .append(Text.button("Cerrar", "/reports close " + r.id, ChatFormatting.GREEN));
    }
}
