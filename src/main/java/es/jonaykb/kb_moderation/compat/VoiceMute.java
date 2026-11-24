package es.jonaykb.kb_moderation.compat;

import es.jonaykb.kb_moderation.config.ModConfig;
import es.jonaykb.kb_moderation.sanction.Sanctions;
import es.jonaykb.kb_moderation.vanish.Vanish;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who may not speak in Simple Voice Chat. Rebuilt once a second on the server thread and read by the voice thread,
 * so the voice plugin never touches game data.
 */
public final class VoiceMute {
    private static volatile Set<UUID> silenced = Set.of();

    private VoiceMute() {
    }

    public static boolean isSilenced(UUID uuid) {
        return silenced.contains(uuid);
    }

    public static void refresh(MinecraftServer server) {
        Set<UUID> next = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean muted = ModConfig.MUTE_VOICE.get() && Sanctions.activeMute(p) != null;
            boolean vanished = ModConfig.VANISH_BLOCKS_VOICE.get() && Vanish.isVanished(p.getUUID());
            if (muted || vanished) next.add(p.getUUID());
        }
        silenced = Set.copyOf(next);
    }
}
