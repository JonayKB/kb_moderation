package es.jonaykb.kb_moderation.perm;

import com.mojang.logging.LogUtils;
import es.jonaykb.kb_moderation.config.ModConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Staff permissions. KB Discord Auth mirrors each player's Discord roles as {@code discord_role.<id>} entity tags;
 * the config maps role ids to permissions.
 */
public final class Perms {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ROLE_TAG_PREFIX = "discord_role.";

    private static List<? extends String> parsedFrom;
    private static Map<String, Set<Perm>> byRole = Map.of();

    private Perms() {
    }

    private static Map<String, Set<Perm>> roles() {
        List<? extends String> raw = ModConfig.ROLES.get();
        if (raw != parsedFrom) {
            Map<String, Set<Perm>> map = new HashMap<>();
            for (String entry : raw) {
                String[] parts = entry.split("\\|", 2);
                String role = parts[0].trim();
                Set<Perm> perms = map.computeIfAbsent(role, r -> EnumSet.noneOf(Perm.class));
                for (String key : parts[1].split(",")) {
                    key = key.trim();
                    if (key.isEmpty()) continue;
                    if (key.equals("*")) {
                        perms.addAll(EnumSet.allOf(Perm.class));
                    } else {
                        Perm p = Perm.byKey(key);
                        if (p == null) LOGGER.warn("Unknown permission '{}' for role {} in kb_moderation config", key, role);
                        else perms.add(p);
                    }
                }
            }
            byRole = map;
            parsedFrom = raw;
        }
        return byRole;
    }

    public static boolean has(ServerPlayer player, Perm perm) {
        int opLevel = ModConfig.OP_LEVEL_ALL.get();
        if (opLevel > 0 && player.hasPermissions(opLevel)) return true;
        Map<String, Set<Perm>> roles = roles();
        for (String tag : player.getTags()) {
            if (!tag.startsWith(ROLE_TAG_PREFIX)) continue;
            Set<Perm> perms = roles.get(tag.substring(ROLE_TAG_PREFIX.length()));
            if (perms != null && perms.contains(perm)) return true;
        }
        return false;
    }

    /** The console, command blocks and functions are always allowed; players need the permission. */
    public static boolean has(CommandSourceStack source, Perm perm) {
        ServerPlayer player = source.getPlayer();
        return player == null ? source.hasPermission(2) : has(player, perm);
    }

    public static Predicate<CommandSourceStack> require(Perm perm) {
        return source -> has(source, perm);
    }

    /** Whether {@code staff} may sanction {@code target}: nobody may sanction an exempt player, except the console. */
    public static boolean canSanction(CommandSourceStack staff, ServerPlayer target) {
        if (target == null || staff.getPlayer() == null) return true;
        return !has(target, Perm.EXEMPT);
    }
}
