package es.jonaykb.kb_moderation.freeze;

import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.data.ModData.Frozen;
import es.jonaykb.kb_moderation.sanction.Sanctions;
import es.jonaykb.kb_moderation.util.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * A frozen player stays where they were frozen (they can look around), cannot break, place, use, attack, drop or
 * run most commands, and optionally takes no damage. It survives a relog; staff are told if they disconnect.
 */
public final class Freeze {
    private Freeze() {
    }

    public static boolean isFrozen(UUID uuid) {
        return ModData.loaded() && ModData.get().frozen().containsKey(uuid);
    }

    public static void freeze(ServerPlayer p, String by) {
        Frozen f = new Frozen();
        f.by = by;
        f.since = System.currentTimeMillis();
        f.dimension = p.level().dimension().location().toString();
        f.x = p.getX();
        f.y = p.getY();
        f.z = p.getZ();
        ModData.get().frozen().put(p.getUUID(), f);
        ModData.get().save();
        showTitle(p);
        p.sendSystemMessage(Text.msg("&b&lTe ha congelado un moderador. &7No te desconectes: hacerlo cuenta en tu contra. "
                + "Escucha sus instrucciones."));
    }

    public static boolean unfreeze(UUID uuid) {
        boolean was = ModData.get().frozen().remove(uuid) != null;
        if (was) ModData.get().save();
        return was;
    }

    /** Vanilla title, only for a client without this mod (with it, the client draws its own frost screen). */
    public static void showTitle(ServerPlayer p) {
        if (es.jonaykb.kb_moderation.network.Net.hasMod(p)) return;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 70, 10));
        p.connection.send(new ClientboundSetTitleTextPacket(Text.legacy("&b&l❄ CONGELADO ❄")));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Text.legacy("&7Un moderador quiere hablar contigo")));
    }

    /** Every tick for a frozen player: back to the freeze spot if they moved. */
    public static void tick(ServerPlayer p) {
        Frozen f = ModData.get().frozen().get(p.getUUID());
        if (f == null) return;
        if (!p.level().dimension().location().toString().equals(f.dimension)) {
            // Moved to another dimension (portal before freezing, /tp by staff…): freeze them there instead.
            f.dimension = p.level().dimension().location().toString();
            f.x = p.getX();
            f.y = p.getY();
            f.z = p.getZ();
            ModData.get().save();
        }
        double dx = p.getX() - f.x, dy = p.getY() - f.y, dz = p.getZ() - f.z;
        if (dx * dx + dy * dy + dz * dz > 0.01) {
            p.stopRiding();
            p.connection.teleport(f.x, f.y, f.z, p.getYRot(), p.getXRot());
        }
        p.fallDistance = 0;
        if (p.tickCount % 60 == 0) showTitle(p);
    }

    /** Staff can move a frozen player (e.g. with /tp): the new spot becomes the freeze spot. */
    public static void moveTo(ServerPlayer p, double x, double y, double z) {
        Frozen f = ModData.get().frozen().get(p.getUUID());
        if (f == null) return;
        f.x = x;
        f.y = y;
        f.z = z;
        ModData.get().save();
    }

    public static void onLogout(ServerPlayer p) {
        if (!isFrozen(p.getUUID())) return;
        Sanctions.notifyStaff(p.server, Text.msg("&c⚠ " + p.getGameProfile().getName()
                + " &7se ha desconectado estando congelado. &7Sigue congelado al volver."));
    }

    public static void onLogin(ServerPlayer p) {
        if (!isFrozen(p.getUUID())) return;
        // Back to where they were frozen (they might have logged out in another spot).
        Frozen f = ModData.get().frozen().get(p.getUUID());
        if (p.level().dimension().location().toString().equals(f.dimension)) {
            p.connection.teleport(f.x, f.y, f.z, p.getYRot(), p.getXRot());
        }
        showTitle(p);
        p.sendSystemMessage(Text.msg("&bSigues congelado."));
        Sanctions.notifyStaff(p.server, Text.msg("&b" + p.getGameProfile().getName() + " &7ha vuelto y sigue congelado."));
    }

    public static Component status(UUID uuid) {
        Frozen f = ModData.get().frozen().get(uuid);
        return f == null ? Component.empty() : Text.legacy("&bCongelado por &f" + f.by + " &7desde " + Text.date(f.since));
    }
}
