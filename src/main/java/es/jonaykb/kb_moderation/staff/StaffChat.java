package es.jonaykb.kb_moderation.staff;

import es.jonaykb.kb_moderation.perm.Perm;
import es.jonaykb.kb_moderation.perm.Perms;
import es.jonaykb.kb_moderation.util.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Staff chat: the "Chat staff" tab of the panel, /sc <message>, or /sc alone to make normal chat go to staff.
 * Every message also shows in the normal chat of each staff member. The last messages are kept for the tab.
 */
public final class StaffChat {
    private static final Set<UUID> toggled = new HashSet<>();
    private static final int KEEP = 100;
    private static final java.util.ArrayDeque<es.jonaykb.kb_moderation.network.Packets.StaffLine> recent = new java.util.ArrayDeque<>();

    /** The last messages, oldest first (for the panel tab). */
    public static java.util.List<es.jonaykb.kb_moderation.network.Packets.StaffLine> recent() {
        return java.util.List.copyOf(recent);
    }

    private StaffChat() {
    }

    public static boolean toggle(UUID uuid) {
        if (toggled.remove(uuid)) return false;
        toggled.add(uuid);
        return true;
    }

    public static boolean isToggled(ServerPlayer p) {
        return toggled.contains(p.getUUID()) && Perms.has(p, Perm.STAFFCHAT);
    }

    public static void forget(UUID uuid) {
        toggled.remove(uuid);
    }

    public static void send(MinecraftServer server, String from, String message) {
        message = message.trim();
        if (message.isEmpty()) return;
        if (message.length() > 256) message = message.substring(0, 256);
        var entry = new es.jonaykb.kb_moderation.network.Packets.StaffLine(System.currentTimeMillis(), from, message);
        recent.addLast(entry);
        while (recent.size() > KEEP) recent.removeFirst();
        Component line = Text.legacy("&8[&dStaff&8] &d" + from + "&8: &f").append(Component.literal(message));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!Perms.has(p, Perm.STAFFCHAT)) continue;
            p.sendSystemMessage(line);
            es.jonaykb.kb_moderation.network.Net.staffLine(p, entry);
            // A soft ping for the others, so a staff message does not get lost in a busy chat.
            if (!p.getGameProfile().getName().equals(from)) {
                p.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), net.minecraft.sounds.SoundSource.MASTER, 0.5f, 1.6f);
            }
        }
        server.sendSystemMessage(line);
    }
}
