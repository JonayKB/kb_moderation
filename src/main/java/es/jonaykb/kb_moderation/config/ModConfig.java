package es.jonaykb.kb_moderation.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** config/kb_moderation-common.toml */
public final class ModConfig {
    public static final ForgeConfigSpec SPEC;

    // permissions
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ROLES;
    public static final ForgeConfigSpec.IntValue OP_LEVEL_ALL;

    // sanctions
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MUTED_COMMANDS;
    public static final ForgeConfigSpec.BooleanValue MUTE_VOICE;
    public static final ForgeConfigSpec.IntValue WARN_EXPIRY_DAYS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WARN_ESCALATION;
    public static final ForgeConfigSpec.BooleanValue REPLACE_VANILLA_COMMANDS;
    public static final ForgeConfigSpec.ConfigValue<String> APPEAL;
    public static final ForgeConfigSpec.ConfigValue<String> BAN_SCREEN;
    public static final ForgeConfigSpec.ConfigValue<String> KICK_SCREEN;
    public static final ForgeConfigSpec.ConfigValue<String> MUTED_MESSAGE;
    public static final ForgeConfigSpec.ConfigValue<String> WARNED_MESSAGE;

    // vanish
    public static final ForgeConfigSpec.BooleanValue FAKE_JOIN_LEAVE;
    public static final ForgeConfigSpec.BooleanValue VANISH_BLOCKS_VOICE;
    public static final ForgeConfigSpec.BooleanValue VANISH_NO_MOB_TARGET;

    // freeze
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FROZEN_COMMANDS;
    public static final ForgeConfigSpec.BooleanValue FROZEN_INVULNERABLE;

    // reports
    public static final ForgeConfigSpec.IntValue REPORT_COOLDOWN;

    // KB Discord Bot
    public static final ForgeConfigSpec.BooleanValue BOT_SYNC;
    public static final ForgeConfigSpec.ConfigValue<String> BOT_URL;
    public static final ForgeConfigSpec.ConfigValue<String> BOT_API_KEY;
    public static final ForgeConfigSpec.IntValue BOT_POLL_SECONDS;

    // discord
    public static final ForgeConfigSpec.ConfigValue<String> WEBHOOK;
    public static final ForgeConfigSpec.ConfigValue<String> REPORTS_WEBHOOK;
    public static final ForgeConfigSpec.ConfigValue<String> REPORTS_PING;
    public static final ForgeConfigSpec.ConfigValue<String> SERVER_NAME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Who can use what. Staff are recognised by the discord_role.<id> tags that KB Discord Auth puts on players.")
                .push("permissions");
        ROLES = b.comment(
                        "One entry per Discord role: \"roleId|perm1,perm2\". \"*\" = every permission.",
                        "Permissions: warn, mute, kick, ban, history, modinfo, freeze, teleport, vanish, vanish.see, seeinvisible, invsee,",
                        "invsee.edit, staffchat, reports, exempt (cannot be sanctioned by other staff).",
                        "Example: [\"111111111111111111|*\", \"222222222222222222|warn,mute,kick,history,freeze,invsee,staffchat,reports,vanish.see\"]")
                .defineListAllowEmpty(List.of("roles"), List::of, o -> o instanceof String s && s.contains("|"));
        OP_LEVEL_ALL = b.comment("Players with at least this op level have every permission (the console always does). 0 = disabled.")
                .defineInRange("opLevelAll", 4, 0, 4);
        b.pop();

        b.push("sanctions");
        REPLACE_VANILLA_COMMANDS = b.comment("Replace vanilla /kick and /ban with these (with reason, duration, Discord ban and history).",
                        "If false, use /kbkick and /kbban.")
                .define("replaceVanillaCommands", true);
        MUTED_COMMANDS = b.comment("Commands a muted player cannot use (private messages and similar).")
                .defineListAllowEmpty(List.of("mutedCommands"),
                        () -> List.of("msg", "tell", "w", "me", "say", "teammsg", "tm", "r", "reply"), o -> o instanceof String);
        MUTE_VOICE = b.comment("A muted player is also silent in Simple Voice Chat.").define("muteVoice", true);
        WARN_EXPIRY_DAYS = b.comment("Warnings older than this stop counting for escalation (they stay in the history). 0 = never.")
                .defineInRange("warnExpiryDays", 30, 0, 3650);
        WARN_ESCALATION = b.comment("Automatic sanction when a player reaches N active warnings: \"N|mute|duration\" or \"N|ban|duration\".",
                        "Durations: 30m, 12h, 7d, 2w, 1d12h, perm.")
                .defineListAllowEmpty(List.of("warnEscalation"), () -> List.of("3|mute|1h", "5|ban|1d"),
                        o -> o instanceof String s && s.split("\\|").length == 3);
        APPEAL = b.comment("Shown on ban screens and to muted players. Empty = nothing.")
                .define("appeal", "&7Puedes apelar en nuestro Discord, canal &f#apelaciones&7.");
        BAN_SCREEN = b.comment("Placeholders: {reason} {staff} {expires} {appeal} {id}")
                .define("banScreen", "&c&lEstás baneado del servidor\n\n&7Motivo: &f{reason}\n&7Por: &f{staff}\n&7Expira: &f{expires}\n&8ID #{id}\n\n{appeal}");
        KICK_SCREEN = b.define("kickScreen", "&c&lHas sido expulsado\n\n&7Motivo: &f{reason}\n&7Por: &f{staff}");
        MUTED_MESSAGE = b.define("mutedMessage", "&cEstás silenciado. &7Motivo: &f{reason} &7· Expira: &f{expires}");
        WARNED_MESSAGE = b.define("warnedMessage", "&e&l⚠ Aviso de {staff}: &f{reason} &7({count} avisos activos)");
        b.pop();

        b.push("vanish");
        FAKE_JOIN_LEAVE = b.comment("Show a fake \"left the game\" message when someone vanishes, and \"joined\" when they come back.",
                        "Players who join while vanished never show the join/leave messages.")
                .define("fakeJoinLeave", true);
        VANISH_BLOCKS_VOICE = b.comment("Vanished staff cannot speak in Simple Voice Chat (they can still hear).")
                .define("blocksVoice", true);
        VANISH_NO_MOB_TARGET = b.comment("Mobs ignore vanished staff.").define("noMobTarget", true);
        b.pop();

        b.push("freeze");
        FROZEN_COMMANDS = b.comment("Commands a frozen player can still use.")
                .defineListAllowEmpty(List.of("allowedCommands"), () -> List.of("msg", "tell", "w", "r", "report"), o -> o instanceof String);
        FROZEN_INVULNERABLE = b.comment("A frozen player cannot take damage.").define("invulnerable", true);
        b.pop();

        b.push("reports");
        REPORT_COOLDOWN = b.comment("Seconds between two /report of the same player.").defineInRange("cooldownSeconds", 60, 0, 86400);
        b.pop();

        b.comment("Sanctions and reports shared with KB Discord Bot: what is done in the game is applied in Discord (ban ↔ ban,",
                "mute ↔ timeout, warn ↔ warn with a DM, note ↔ note) and what is done in the bot panel or in Discord is applied here.").push("bot");
        BOT_SYNC = b.comment("Keep sanctions and reports in sync with the bot.").define("enabled", true);
        BOT_URL = b.comment("Base URL of the bot's moderation API, e.g. https://panel.example.com/api/integrations/minecraft/moderation",
                        "Empty = taken from KB Discord Auth (stats.endpoint, same server).")
                .define("url", "");
        BOT_API_KEY = b.comment("This server's API key in the bot panel (Servers → statistics). Empty = KB Discord Auth's stats.apiKey.")
                .define("apiKey", "");
        BOT_POLL_SECONDS = b.comment("How often to ask the bot for sanctions made in Discord or in its panel.")
                .defineInRange("pollSeconds", 5, 2, 300);
        b.pop();

        b.comment("Discord webhooks (Channel settings → Integrations → Webhooks). Empty = off. Not needed with the bot sync,",
                "which already posts everything in its moderation log channel.").push("discord");
        WEBHOOK = b.comment("Sanctions log: warn, mute, kick, ban, unmute, unban.").define("webhookUrl", "");
        REPORTS_WEBHOOK = b.comment("Reports from /report. Empty = use webhookUrl.").define("reportsWebhookUrl", "");
        REPORTS_PING = b.comment("Text before each report, e.g. \"<@&ROLE_ID>\" to ping the moderators.").define("reportsPing", "");
        SERVER_NAME = b.comment("Shown in the footer of each message.").define("serverName", "KB Server");
        b.pop();

        SPEC = b.build();
    }

    private ModConfig() {
    }
}
