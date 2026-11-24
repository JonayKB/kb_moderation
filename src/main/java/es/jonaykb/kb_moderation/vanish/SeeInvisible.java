package es.jonaykb.kb_moderation.vanish;

import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.perm.Perm;
import es.jonaykb.kb_moderation.perm.Perms;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Staff setting (/verinvisibles): players under invisibility are sent to that staff member without the invisible
 * flag, so their client draws them and their name. Everyone else keeps not seeing them. Only the packets to that
 * staff member change; nothing changes on the server.
 */
public final class SeeInvisible {
    /** Entity.DATA_SHARED_FLAGS_ID and its invisible bit (Entity.FLAG_INVISIBLE = 5). */
    private static final int SHARED_FLAGS_ID = 0;
    private static final byte INVISIBLE_BIT = 1 << 5;

    private SeeInvisible() {
    }

    public static boolean enabled(ServerPlayer viewer) {
        return ModData.loaded() && ModData.get().seeInvisible().contains(viewer.getUUID()) && Perms.has(viewer, Perm.SEE_INVISIBLE);
    }

    public static boolean toggle(ServerPlayer viewer) {
        UUID id = viewer.getUUID();
        boolean on = !ModData.get().seeInvisible().remove(id);
        if (on) ModData.get().seeInvisible().add(id);
        ModData.get().save();
        resend(viewer);
        return on;
    }

    /** Resends the flags of every player near the viewer so the change shows at once (rewritten on the way out). */
    public static void resend(ServerPlayer viewer) {
        for (ServerPlayer other : viewer.serverLevel().players()) {
            if (other == viewer || !other.isInvisible()) continue;
            List<SynchedEntityData.DataValue<?>> values = other.getEntityData().getNonDefaultValues();
            if (values != null) viewer.connection.send(new ClientboundSetEntityDataPacket(other.getId(), values));
        }
    }

    /** Called for every packet sent to {@code viewer}. */
    public static Packet<?> rewrite(ServerPlayer viewer, Packet<?> packet) {
        if (viewer == null) return packet;
        if (!(packet instanceof ClientboundSetEntityDataPacket) && !(packet instanceof ClientboundBundlePacket)) return packet;
        if (!enabled(viewer)) return packet;
        if (packet instanceof ClientboundSetEntityDataPacket data) return rewrite(viewer, data);
        ClientboundBundlePacket bundle = (ClientboundBundlePacket) packet;
        List<Packet<ClientGamePacketListener>> out = new ArrayList<>();
        boolean changed = false;
        for (Packet<ClientGamePacketListener> sub : bundle.subPackets()) {
            if (sub instanceof ClientboundSetEntityDataPacket data) {
                ClientboundSetEntityDataPacket r = rewrite(viewer, data);
                changed |= r != data;
                out.add(r);
            } else {
                out.add(sub);
            }
        }
        return changed ? new ClientboundBundlePacket(out) : packet;
    }

    private static ClientboundSetEntityDataPacket rewrite(ServerPlayer viewer, ClientboundSetEntityDataPacket data) {
        if (data.id() == viewer.getId()) return data;
        List<SynchedEntityData.DataValue<?>> items = data.packedItems();
        for (int i = 0; i < items.size(); i++) {
            SynchedEntityData.DataValue<?> v = items.get(i);
            if (v.id() != SHARED_FLAGS_ID || !(v.value() instanceof Byte flags) || (flags & INVISIBLE_BIT) == 0) continue;
            Entity e = viewer.level().getEntity(data.id());
            if (!(e instanceof Player)) return data;
            List<SynchedEntityData.DataValue<?>> copy = new ArrayList<>(items);
            copy.set(i, new SynchedEntityData.DataValue<>(SHARED_FLAGS_ID, EntityDataSerializers.BYTE, (byte) (flags & ~INVISIBLE_BIT)));
            return new ClientboundSetEntityDataPacket(data.id(), copy);
        }
        return data;
    }
}
