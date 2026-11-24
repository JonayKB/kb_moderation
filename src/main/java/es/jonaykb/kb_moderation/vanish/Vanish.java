package es.jonaykb.kb_moderation.vanish;

import es.jonaykb.kb_moderation.config.ModConfig;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.mixin.ChunkMapAccessor;
import es.jonaykb.kb_moderation.perm.Perm;
import es.jonaykb.kb_moderation.perm.Perms;
import es.jonaykb.kb_moderation.util.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Vanished players are not sent to whoever lacks "vanish.see": not as an entity (see TrackedEntityMixin), not in the
 * tab list and without join/leave messages. The state is saved, so it survives a relog or a restart.
 */
public final class Vanish {
    /** The player joining or leaving right now, so their join/leave message can be swallowed (see PlayerListMixin). */
    public static ServerPlayer joiningOrLeaving;

    private Vanish() {
    }

    public static boolean isVanished(UUID uuid) {
        return ModData.loaded() && ModData.get().vanished().contains(uuid);
    }

    public static boolean canSee(ServerPlayer viewer, ServerPlayer target) {
        return viewer == target || !isVanished(target.getUUID()) || Perms.has(viewer, Perm.VANISH_SEE);
    }

    public static void set(ServerPlayer p, boolean vanish) {
        MinecraftServer server = p.server;
        boolean changed = vanish ? ModData.get().vanished().add(p.getUUID()) : ModData.get().vanished().remove(p.getUUID());
        if (!changed) return;
        ModData.get().save();

        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer == p || Perms.has(viewer, Perm.VANISH_SEE)) continue;
            if (vanish) {
                viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(p.getUUID())));
            } else {
                viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(p)));
            }
            if (ModConfig.FAKE_JOIN_LEAVE.get()) {
                String key = vanish ? "multiplayer.player.left" : "multiplayer.player.joined";
                viewer.sendSystemMessage(Component.translatable(key, p.getDisplayName()).withStyle(ChatFormatting.YELLOW));
            }
        }
        refreshTracking(p);
        es.jonaykb.kb_moderation.network.Net.syncVanishedAll(server);
        Component note = Text.msg(vanish ? "&f" + p.getGameProfile().getName() + " &7ahora está en &bvanish"
                : "&f" + p.getGameProfile().getName() + " &7ha salido del &bvanish");
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != p && Perms.has(viewer, Perm.VANISH_SEE)) viewer.sendSystemMessage(note);
        }
        p.sendSystemMessage(Text.msg(vanish ? "&bAhora eres invisible para los jugadores." : "&bVuelves a ser visible."));
    }

    /** Makes every player in the level re-check whether they should see {@code p}. */
    public static void refreshTracking(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).kbModeration$entityMap().get(p.getId());
        if (tracked instanceof TrackedEntityAccess t) t.kbModeration$updatePlayers(level.players());
    }

    /** A player just joined: hide the vanished from them, or hide them if they are vanished. */
    public static void onJoin(ServerPlayer joined) {
        MinecraftServer server = joined.server;
        if (!Perms.has(joined, Perm.VANISH_SEE)) {
            List<UUID> hidden = server.getPlayerList().getPlayers().stream()
                    .filter(o -> o != joined && isVanished(o.getUUID())).map(ServerPlayer::getUUID).toList();
            if (!hidden.isEmpty()) joined.connection.send(new ClientboundPlayerInfoRemovePacket(hidden));
        }
        if (isVanished(joined.getUUID())) {
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                if (viewer != joined && !Perms.has(viewer, Perm.VANISH_SEE)) {
                    viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(joined.getUUID())));
                }
            }
            joined.sendSystemMessage(Text.msg("&bSigues en vanish. &7Usa &f/vanish &7para salir."));
        }
    }
}
