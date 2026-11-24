package es.jonaykb.kb_moderation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import es.jonaykb.kb_moderation.config.ModConfig;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.data.ModData.Sanction;
import es.jonaykb.kb_moderation.data.ModData.Type;
import es.jonaykb.kb_moderation.freeze.Freeze;
import es.jonaykb.kb_moderation.invsee.InvseeMenu;
import es.jonaykb.kb_moderation.link.DiscordLink;
import es.jonaykb.kb_moderation.network.Net;
import es.jonaykb.kb_moderation.perm.Perm;
import es.jonaykb.kb_moderation.perm.Perms;
import es.jonaykb.kb_moderation.sanction.Sanctions;
import es.jonaykb.kb_moderation.staff.Reports;
import es.jonaykb.kb_moderation.staff.StaffChat;
import es.jonaykb.kb_moderation.sync.BotSync;
import es.jonaykb.kb_moderation.util.Durations;
import es.jonaykb.kb_moderation.util.Targets;
import es.jonaykb.kb_moderation.util.Text;
import es.jonaykb.kb_moderation.vanish.SeeInvisible;
import es.jonaykb.kb_moderation.vanish.Vanish;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ModCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SuggestionProvider<CommandSourceStack> DURATIONS = (ctx, b) ->
            SharedSuggestionProvider.suggest(List.of("15m", "1h", "6h", "1d", "7d", "30d", "perm"), b);

    private ModCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        boolean replace = ModConfig.REPLACE_VANILLA_COMMANDS.get();
        if (replace) {
            removeRoot(d, "kick");
            removeRoot(d, "ban");
        }
        String kick = replace ? "kick" : "kbkick";
        String ban = replace ? "ban" : "kbban";

        d.register(literal("warn").requires(Perms.require(Perm.WARN))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE)
                        .then(argument("reason", StringArgumentType.greedyString()).executes(ModCommands::warn))));

        d.register(literal("note").requires(Perms.require(Perm.WARN))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.KNOWN)
                        .then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                            Targets t = target(c);
                            if (t == null) return 0;
                            Sanction s = Sanctions.note(c.getSource().getServer(), t, c.getSource(), StringArgumentType.getString(c, "text"));
                            ok(c.getSource(), "&7Nota &f#" + s.id + " &7guardada en el historial de &f" + t.name() + "&7 (no se le avisa).");
                            return 1;
                        }))));

        d.register(literal("delwarn").requires(Perms.require(Perm.WARN))
                .then(argument("id", IntegerArgumentType.integer(1)).executes(ModCommands::delwarn)));

        d.register(literal("mute").requires(Perms.require(Perm.MUTE))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.KNOWN)
                        .then(argument("duration", StringArgumentType.word()).suggests(DURATIONS)
                                .executes(c -> timed(c, Type.MUTE, ""))
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(c -> timed(c, Type.MUTE, StringArgumentType.getString(c, "reason")))))));
        d.register(literal("unmute").requires(Perms.require(Perm.MUTE))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.KNOWN).executes(c -> lift(c, Type.MUTE))));

        d.register(literal(kick).requires(Perms.require(Perm.KICK))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE)
                        .executes(c -> kick(c, ""))
                        .then(argument("reason", StringArgumentType.greedyString())
                                .executes(c -> kick(c, StringArgumentType.getString(c, "reason"))))));

        d.register(literal(ban).requires(Perms.require(Perm.BAN))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.KNOWN)
                        .then(argument("duration", StringArgumentType.word()).suggests(DURATIONS)
                                .executes(c -> timed(c, Type.BAN, ""))
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(c -> timed(c, Type.BAN, StringArgumentType.getString(c, "reason")))))));
        d.register(literal("unban").requires(Perms.require(Perm.BAN))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.KNOWN).executes(c -> lift(c, Type.BAN))));

        d.register(literal("history").requires(Perms.require(Perm.HISTORY))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.KNOWN).executes(ModCommands::history)));

        d.register(literal("modinfo").requires(Perms.require(Perm.MODINFO))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.KNOWN).executes(ModCommands::modinfo)));

        d.register(literal("freeze").requires(Perms.require(Perm.FREEZE))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE).executes(ModCommands::freeze)));
        d.register(literal("unfreeze").requires(Perms.require(Perm.FREEZE))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.KNOWN).executes(ModCommands::unfreeze)));

        d.register(literal("mtp").requires(player(Perm.TELEPORT))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE).executes(c -> teleport(c, false))));
        d.register(literal("mtphere").requires(player(Perm.TELEPORT))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE).executes(c -> teleport(c, true))));

        d.register(literal("vanish").requires(Perms.require(Perm.VANISH))
                .executes(ModCommands::vanish)
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE).executes(ModCommands::vanishOther)));
        d.register(literal("verinvisibles").requires(player(Perm.SEE_INVISIBLE)).executes(ModCommands::seeInvisible));

        d.register(literal("invsee").requires(player(Perm.INVSEE))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE)
                        .executes(c -> invsee(c, InvseeMenu.Kind.MAIN))));
        d.register(literal("endersee").requires(player(Perm.INVSEE))
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE)
                        .executes(c -> invsee(c, InvseeMenu.Kind.ENDER))));

        d.register(literal("sc").requires(Perms.require(Perm.STAFFCHAT))
                .executes(ModCommands::staffChatToggle)
                .then(argument("message", StringArgumentType.greedyString()).executes(c -> {
                    StaffChat.send(c.getSource().getServer(), c.getSource().getTextName(), StringArgumentType.getString(c, "message"));
                    return 1;
                })));

        d.register(literal("report").requires(CommandSourceStack::isPlayer)
                .executes(ModCommands::reportScreen)
                .then(argument("player", StringArgumentType.word()).suggests(Targets.ONLINE)
                        .then(argument("reason", StringArgumentType.greedyString()).executes(ModCommands::report))));

        d.register(literal("reports").requires(Perms.require(Perm.REPORTS))
                .executes(ModCommands::reports)
                .then(literal("close").then(argument("id", IntegerArgumentType.integer(1)).executes(ModCommands::closeReport))));

        d.register(literal("mod").requires(ModCommands::isStaff).executes(ModCommands::panel));
    }

    /** Anyone with at least one staff permission: may open the panel (each button still checks its own). */
    public static boolean isStaff(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        if (p == null) return false;
        for (Perm perm : Perm.values()) if (perm != Perm.EXEMPT && Perms.has(p, perm)) return true;
        return false;
    }

    private static Predicate<CommandSourceStack> player(Perm perm) {
        return s -> s.isPlayer() && Perms.has(s, perm);
    }

    @SuppressWarnings("unchecked")
    private static void removeRoot(CommandDispatcher<CommandSourceStack> d, String name) {
        try {
            for (String field : new String[]{"children", "literals"}) {
                Field f = CommandNode.class.getDeclaredField(field);
                f.setAccessible(true);
                ((Map<String, ?>) f.get(d.getRoot())).remove(name);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Could not replace vanilla /{}; set replaceVanillaCommands = false", name, e);
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static Targets target(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        Targets t = Targets.resolve(c.getSource().getServer(), name);
        if (t == null) c.getSource().sendFailure(Text.error("No conozco a ningún jugador llamado " + name + "."));
        return t;
    }

    private static boolean mayTouch(CommandSourceStack src, Targets t) {
        boolean exempt = t.online() != null ? Perms.has(t.online(), Perm.EXEMPT)
                : ModData.get().player(t.uuid()) != null && ModData.get().player(t.uuid()).exempt;
        if (src.getPlayer() != null && exempt && !src.getPlayer().getUUID().equals(t.uuid())) {
            src.sendFailure(Text.error(t.name() + " es staff protegido: no se le puede sancionar."));
            return false;
        }
        return true;
    }

    private static void ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Text.msg(msg), false);
    }

    // ───────────────────────────── sanctions ─────────────────────────────

    private static int warn(CommandContext<CommandSourceStack> c) {
        Targets t = target(c);
        if (t == null || !mayTouch(c.getSource(), t)) return 0;
        Sanction s = Sanctions.warn(c.getSource().getServer(), t, c.getSource(), StringArgumentType.getString(c, "reason"));
        ok(c.getSource(), "&7Aviso &f#" + s.id + " &7dado a &f" + t.name() + "&7.");
        return 1;
    }

    private static int delwarn(CommandContext<CommandSourceStack> c) {
        Sanction s = ModData.get().sanction(IntegerArgumentType.getInteger(c, "id"));
        if (s == null || (s.type != Type.WARN && s.type != Type.NOTE) || s.revoked) {
            c.getSource().sendFailure(Text.error("No hay ningún aviso o nota activos con ese número."));
            return 0;
        }
        s.revoked = true;
        s.revokedBy = c.getSource().getTextName();
        s.revokedAt = System.currentTimeMillis();
        ModData.get().save();
        BotSync.removed(s, c.getSource().getPlayer() == null ? null : c.getSource().getPlayer().getUUID(), c.getSource().getTextName());
        ok(c.getSource(), "&7" + Sanctions.typeName(s.type) + " &f#" + s.id + " &7de &f" + s.targetName + " &7retirado.");
        return 1;
    }

    private static int timed(CommandContext<CommandSourceStack> c, Type type, String reason) {
        Targets t = target(c);
        if (t == null || !mayTouch(c.getSource(), t)) return 0;
        String raw = StringArgumentType.getString(c, "duration");
        long duration = Durations.parse(raw);
        if (duration < 0) {
            c.getSource().sendFailure(Text.error("Duración no válida: " + raw + ". Ejemplos: 30m, 12h, 7d, 1d12h, perm."));
            return 0;
        }
        MinecraftServer server = c.getSource().getServer();
        Sanction s = type == Type.MUTE ? Sanctions.mute(server, t, c.getSource(), reason, duration)
                : Sanctions.ban(server, t, c.getSource(), reason, duration);
        ok(c.getSource(), "&7" + Sanctions.typeName(type) + " &f#" + s.id + " &7a &f" + t.name() + "&7 · expira: &f" + Sanctions.expiresText(s));
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> c, String reason) {
        Targets t = target(c);
        if (t == null || !mayTouch(c.getSource(), t)) return 0;
        if (t.online() == null) {
            c.getSource().sendFailure(Text.error(t.name() + " no está conectado."));
            return 0;
        }
        Sanctions.kick(c.getSource().getServer(), t, c.getSource(), reason);
        return 1;
    }

    private static int lift(CommandContext<CommandSourceStack> c, Type type) {
        Targets t = target(c);
        if (t == null) return 0;
        int n = Sanctions.lift(c.getSource().getServer(), type, t, c.getSource());
        if (n == 0) {
            c.getSource().sendFailure(Text.error(t.name() + (type == Type.MUTE ? " no está silenciado." : " no está baneado.")));
            return 0;
        }
        return n;
    }

    // ───────────────────────────── history / info ─────────────────────────────

    private static int history(CommandContext<CommandSourceStack> c) {
        Targets t = target(c);
        if (t == null) return 0;
        List<Sanction> list = ModData.get().historyOf(t.uuid(), t.discordId());
        CommandSourceStack src = c.getSource();
        if (list.isEmpty()) {
            ok(src, "&f" + t.name() + " &7no tiene sanciones. &a✔");
            return 1;
        }
        src.sendSuccess(() -> Text.msg("&fHistorial de " + t.name() + " &7(" + list.size() + ")"), false);
        long now = System.currentTimeMillis();
        int from = Math.max(0, list.size() - 15);
        for (Sanction s : list.subList(from, list.size())) src.sendSuccess(() -> historyLine(s, now), false);
        if (from > 0) ok(src, "&7… y " + from + " más antiguas.");
        return list.size();
    }

    private static MutableComponent historyLine(Sanction s, long now) {
        String color = switch (s.type) {
            case WARN -> "&e";
            case MUTE -> "&6";
            case KICK -> "&c";
            case BAN -> "&4";
            case NOTE -> "&7";
        };
        String state;
        if (s.revoked) state = "&8(retirada por " + s.revokedBy + ")";
        else if (s.type == Type.KICK || s.type == Type.WARN || s.type == Type.NOTE) state = "";
        else if (s.active(now)) state = s.permanent() ? "&c(activa, permanente)" : "&c(activa, " + Durations.format(s.expiresAt - now) + ")";
        else state = "&8(expirada)";
        String dur = (s.type == Type.MUTE || s.type == Type.BAN) ? (s.permanent() ? " perm" : " " + Durations.format(s.expiresAt - s.createdAt)) : "";
        MutableComponent line = Text.legacy(" &8#" + s.id + " " + color + Sanctions.typeName(s.type) + dur + " &7" + Text.date(s.createdAt)
                + " &8· &f" + Text.plain(s.reason) + " &8· &7" + Text.plain(s.staffName) + " " + state);
        if (!s.revoked && (s.type == Type.WARN || s.type == Type.NOTE)) line.append(" ").append(Text.button("Quitar", "/delwarn " + s.id, ChatFormatting.GREEN));
        if (s.active(now) && s.type == Type.MUTE) line.append(" ").append(Text.button("Levantar", "/unmute " + s.targetName, ChatFormatting.GREEN));
        if (s.active(now) && s.type == Type.BAN) line.append(" ").append(Text.button("Levantar", "/unban " + s.targetName, ChatFormatting.GREEN));
        return line;
    }

    private static int modinfo(CommandContext<CommandSourceStack> c) {
        Targets t = target(c);
        if (t == null) return 0;
        CommandSourceStack src = c.getSource();
        DiscordLink.Info discord = DiscordLink.get(t.uuid());
        ModData.PlayerRecord rec = ModData.get().player(t.uuid());
        List<String> lines = new java.util.ArrayList<>();
        lines.add("&f&l" + t.name() + (t.online() != null ? " &a● conectado" : " &8● desconectado"));
        lines.add("&7UUID: &f" + t.uuid());
        if (discord != null) lines.add("&7Discord: &9" + discord.display() + " &8(" + discord.discordId() + ")" + (discord.inGuild() ? "" : " &c(fuera del servidor)"));
        else lines.add("&7Discord: &8sin vincular");
        if (rec != null) lines.add("&7Visto: &f" + Text.date(rec.firstSeen) + " &7→ &f" + Text.date(rec.lastSeen));

        if (discord != null) {
            List<String> alts = DiscordLink.accountsOf(discord.discordId()).stream()
                    .filter(a -> !a.uuid().equals(t.uuid())).map(DiscordLink.Account::name).toList();
            if (!alts.isEmpty()) lines.add("&7Otras cuentas del mismo Discord: &e" + String.join(", ", alts));
        }
        if (rec != null && rec.lastIp != null) {
            List<String> sameIp = ModData.get().players().entrySet().stream()
                    .filter(e -> !e.getKey().equals(t.uuid()) && rec.lastIp.equals(e.getValue().lastIp))
                    .map(e -> e.getValue().name).toList();
            if (!sameIp.isEmpty()) lines.add("&7Comparten IP: &e" + String.join(", ", sameIp));
        }

        String did = discord == null ? null : discord.discordId();
        Sanction mute = ModData.get().active(Type.MUTE, t.uuid(), did);
        Sanction ban = ModData.get().active(Type.BAN, t.uuid(), did);
        lines.add("&7Avisos activos: &f" + Sanctions.activeWarns(t.uuid(), did)
                + " &8· &7Sanciones en total: &f" + ModData.get().historyOf(t.uuid(), did).size());
        if (mute != null) lines.add("&6Silenciado &7hasta &f" + Sanctions.expiresText(mute) + " &8#" + mute.id);
        if (ban != null) lines.add("&4Baneado &7hasta &f" + Sanctions.expiresText(ban) + " &8#" + ban.id);
        if (Freeze.isFrozen(t.uuid())) lines.add("&b❄ Congelado");
        if (Vanish.isVanished(t.uuid())) lines.add("&b👁 En vanish");
        for (String l : lines) src.sendSuccess(() -> Text.legacy(l), false);
        src.sendSuccess(() -> Component.empty()
                .append(Text.button("Historial", "/history " + t.name(), ChatFormatting.YELLOW)).append(" ")
                .append(Text.button("Inventario", "/invsee " + t.name(), ChatFormatting.GOLD)).append(" ")
                .append(Text.button("TP", "/mtp " + t.name(), ChatFormatting.AQUA)), false);
        return 1;
    }

    // ───────────────────────────── tools ─────────────────────────────

    private static int freeze(CommandContext<CommandSourceStack> c) {
        Targets t = target(c);
        if (t == null || !mayTouch(c.getSource(), t)) return 0;
        if (t.online() == null) {
            c.getSource().sendFailure(Text.error(t.name() + " no está conectado."));
            return 0;
        }
        if (Freeze.isFrozen(t.uuid())) {
            Freeze.unfreeze(t.uuid());
            afterUnfreeze(c, t);
            return 1;
        }
        Freeze.freeze(t.online(), c.getSource().getTextName());
        Net.syncFrozen(t.online());
        Sanctions.notifyStaff(c.getSource().getServer(), Text.msg("&b❄ " + t.name() + " &7congelado por &f" + c.getSource().getTextName()));
        return 1;
    }

    private static int unfreeze(CommandContext<CommandSourceStack> c) {
        Targets t = target(c);
        if (t == null) return 0;
        if (!Freeze.unfreeze(t.uuid())) {
            c.getSource().sendFailure(Text.error(t.name() + " no está congelado."));
            return 0;
        }
        afterUnfreeze(c, t);
        return 1;
    }

    private static void afterUnfreeze(CommandContext<CommandSourceStack> c, Targets t) {
        if (t.online() != null) {
            t.online().sendSystemMessage(Text.msg("&aYa puedes moverte."));
            Net.syncFrozen(t.online());
        }
        Sanctions.notifyStaff(c.getSource().getServer(), Text.msg("&a" + t.name() + " &7descongelado por &f" + c.getSource().getTextName()));
    }

    /** Staff teleport without needing op: to the player, or the player to the staff member. */
    private static int teleport(CommandContext<CommandSourceStack> c, boolean here) {
        ServerPlayer staff = c.getSource().getPlayer();
        String name = StringArgumentType.getString(c, "player");
        ServerPlayer other = c.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (other == null || other == staff) {
            c.getSource().sendFailure(Text.error(name + " no está conectado."));
            return 0;
        }
        ServerPlayer moving = here ? other : staff;
        ServerPlayer to = here ? staff : other;
        moving.teleportTo(to.serverLevel(), to.getX(), to.getY(), to.getZ(), to.getYRot(), to.getXRot());
        if (here) Freeze.moveTo(moving, to.getX(), to.getY(), to.getZ());
        ok(c.getSource(), here ? "&7Has traído a &f" + other.getGameProfile().getName() + "&7." : "&7Teletransportado a &f" + other.getGameProfile().getName() + "&7.");
        return 1;
    }

    private static int vanish(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) {
            c.getSource().sendFailure(Text.error("Desde la consola: /vanish <jugador>"));
            return 0;
        }
        Vanish.set(p, !Vanish.isVanished(p.getUUID()));
        Net.syncStaff(p);
        return 1;
    }

    /** Vanish or unvanish someone else (another staff member, or from the console). */
    private static int vanishOther(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (p == null) {
            c.getSource().sendFailure(Text.error(name + " no está conectado."));
            return 0;
        }
        boolean on = !Vanish.isVanished(p.getUUID());
        Vanish.set(p, on);
        Net.syncStaff(p);
        ok(c.getSource(), "&f" + name + (on ? " &7está ahora en &bvanish&7." : " &7ya no está en vanish."));
        return 1;
    }

    private static int seeInvisible(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        boolean on = SeeInvisible.toggle(p);
        ok(c.getSource(), on ? "&aAhora ves a los jugadores invisibles y sus nombres." : "&7Ya no ves a los jugadores invisibles.");
        Net.syncStaff(p);
        return 1;
    }

    private static int invsee(CommandContext<CommandSourceStack> c, InvseeMenu.Kind kind) {
        ServerPlayer viewer = c.getSource().getPlayer();
        String name = StringArgumentType.getString(c, "player");
        ServerPlayer target = c.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            c.getSource().sendFailure(Text.error(name + " no está conectado."));
            return 0;
        }
        if (target == viewer) {
            c.getSource().sendFailure(Text.error("Ese es tu propio inventario."));
            return 0;
        }
        InvseeMenu.open(viewer, target, Perms.has(viewer, Perm.INVSEE_EDIT), kind);
        return 1;
    }

    private static int staffChatToggle(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        boolean on = StaffChat.toggle(p.getUUID());
        ok(c.getSource(), on ? "&dChat de staff activado: &7lo que escribas lo verá solo el staff. &f/sc &7para salir."
                : "&7Chat de staff desactivado.");
        Net.syncStaff(p);
        return 1;
    }

    // ───────────────────────────── reports ─────────────────────────────

    private static int reportScreen(CommandContext<CommandSourceStack> c) {
        Net.openReportScreen(c.getSource().getPlayer());
        return 1;
    }

    private static int report(CommandContext<CommandSourceStack> c) {
        ServerPlayer reporter = c.getSource().getPlayer();
        Targets t = target(c);
        if (t == null) return 0;
        if (t.uuid().equals(reporter.getUUID())) {
            c.getSource().sendFailure(Text.error("No puedes reportarte a ti mismo."));
            return 0;
        }
        long wait = Reports.cooldownLeft(reporter, t.uuid());
        if (wait > 0) {
            c.getSource().sendFailure(Text.error("Ya has reportado a " + t.name() + ". Espera " + wait + " s."));
            return 0;
        }
        Reports.create(c.getSource().getServer(), reporter, t, StringArgumentType.getString(c, "reason"));
        boolean dm = BotSync.enabled() && DiscordLink.discordId(reporter.getUUID()) != null;
        ok(c.getSource(), "&aGracias. &7El staff ha recibido tu reporte sobre &f" + t.name() + "&7."
                + (dm ? " &7Te hemos escrito por &9MD en Discord&7: responde allí si quieres añadir algo." : ""));
        return 1;
    }

    private static int reports(CommandContext<CommandSourceStack> c) {
        List<ModData.Report> open = ModData.get().reports().stream().filter(r -> !r.closed).toList();
        if (open.isEmpty()) {
            ok(c.getSource(), "&aNo hay reportes abiertos. ✔");
            return 1;
        }
        ok(c.getSource(), "&fReportes abiertos &7(" + open.size() + ")");
        for (ModData.Report r : open.subList(Math.max(0, open.size() - 10), open.size())) {
            c.getSource().sendSuccess(() -> Text.legacy(" &8#" + r.id + " &7" + Text.date(r.at) + " &f" + r.reporterName + " &7→ &c"
                    + r.targetName + "&7: &f" + Text.plain(r.reason) + " ").append(Reports.actions(r)), false);
        }
        return open.size();
    }

    private static int closeReport(CommandContext<CommandSourceStack> c) {
        int id = IntegerArgumentType.getInteger(c, "id");
        for (ModData.Report r : ModData.get().reports()) {
            if (r.id == id && !r.closed) {
                r.closed = true;
                r.closedBy = c.getSource().getTextName();
                ModData.get().save();
                BotSync.reportClosed(r, c.getSource().getTextName());
                ok(c.getSource(), "&aReporte #" + id + " cerrado.");
                return 1;
            }
        }
        c.getSource().sendFailure(Text.error("No hay ningún reporte abierto con ese número."));
        return 0;
    }

    private static int panel(CommandContext<CommandSourceStack> c) {
        Net.openPanel(c.getSource().getPlayer());
        return 1;
    }
}
