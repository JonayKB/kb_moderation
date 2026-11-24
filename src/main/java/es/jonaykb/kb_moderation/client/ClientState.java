package es.jonaykb.kb_moderation.client;

import es.jonaykb.kb_moderation.perm.Perm;

/** What the server told this client about its own player. */
public final class ClientState {
    public static int perms;
    public static boolean vanished;
    public static boolean seeInvisible;
    public static boolean staffChat;
    public static boolean frozen;
    public static String frozenBy = "";
    /** When the freeze started on this client, for the overlay animation. */
    public static long frozenAt;
    /** Vanished players this staff client may see (empty for everyone else). */
    public static java.util.Set<java.util.UUID> vanishedPlayers = java.util.Set.of();
    /** Recent staff chat (for the panel tab) and how many arrived since it was last looked at. */
    public static final java.util.List<es.jonaykb.kb_moderation.network.Packets.StaffLine> staffLines = new java.util.ArrayList<>();
    public static int staffUnread;

    private ClientState() {
    }

    public static boolean has(Perm p) {
        return (perms & (1 << p.ordinal())) != 0;
    }

    public static boolean isStaff() {
        return (perms & ~(1 << Perm.EXEMPT.ordinal())) != 0;
    }

    public static void reset() {
        perms = 0;
        vanished = seeInvisible = staffChat = frozen = false;
        frozenBy = "";
        vanishedPlayers = java.util.Set.of();
        staffLines.clear();
        staffUnread = 0;
    }

    public static boolean isVanished(java.util.UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }
}
