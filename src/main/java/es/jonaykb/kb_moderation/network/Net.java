package es.jonaykb.kb_moderation.network;

import es.jonaykb.kb_moderation.KbModeration;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.data.ModData.Sanction;
import es.jonaykb.kb_moderation.data.ModData.Type;
import es.jonaykb.kb_moderation.freeze.Freeze;
import es.jonaykb.kb_moderation.link.DiscordLink;
import es.jonaykb.kb_moderation.perm.Perm;
import es.jonaykb.kb_moderation.perm.Perms;
import es.jonaykb.kb_moderation.sanction.Sanctions;
import es.jonaykb.kb_moderation.staff.StaffChat;
import es.jonaykb.kb_moderation.util.Targets;
import es.jonaykb.kb_moderation.vanish.SeeInvisible;
import es.jonaykb.kb_moderation.vanish.Vanish;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Net {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KbModeration.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    /** Last StaffState sent to each player, to only send changes. */
    private static final Map<UUID, Packets.StaffState> lastState = new HashMap<>();
    private static int nextId;
    /** Staff with the panel open, and the last panel each was sent: only changes are sent again. */
    private static final Map<UUID, Packets.Panel> panelViewers = new HashMap<>();

    private Net() {
    }

    public static void register() {
        toClient(Packets.StaffState.class, Packets.StaffState::write, Packets.StaffState::read, m -> ClientBridge.run(c -> c.staffState(m)));
        toClient(Packets.Frozen.class, Packets.Frozen::write, Packets.Frozen::read, m -> ClientBridge.run(c -> c.frozen(m)));
        toClient(Packets.Panel.class, Packets.Panel::write, Packets.Panel::read, m -> ClientBridge.run(c -> c.panel(m)));
        toClient(Packets.History.class, Packets.History::write, Packets.History::read, m -> ClientBridge.run(c -> c.history(m)));
        toClient(Packets.OpenReport.class, Packets.OpenReport::write, Packets.OpenReport::read, m -> ClientBridge.run(c -> c.openReport()));
        toClient(Packets.Toast.class, Packets.Toast::write, Packets.Toast::read, m -> ClientBridge.run(c -> c.toast(m)));
        toClient(Packets.Vanished.class, Packets.Vanished::write, Packets.Vanished::read, m -> ClientBridge.run(c -> c.vanished(m)));
        toClient(Packets.StaffChatData.class, Packets.StaffChatData::write, Packets.StaffChatData::read, m -> ClientBridge.run(c -> c.staffChat(m)));

        toServer(Packets.RequestPanel.class, Packets.RequestPanel::write, Packets.RequestPanel::read, (m, p) -> {
            panelViewers.put(p.getUUID(), null);
            sendPanel(p, false);
        });
        toServer(Packets.ClosePanel.class, Packets.ClosePanel::write, Packets.ClosePanel::read, (m, p) -> panelViewers.remove(p.getUUID()));
        toServer(Packets.RequestHistory.class, Packets.RequestHistory::write, Packets.RequestHistory::read, (m, p) -> sendHistory(p, m.name()));
        toServer(Packets.RequestStaffChat.class, Packets.RequestStaffChat::write, Packets.RequestStaffChat::read, (m, p) -> {
            if (Perms.has(p, Perm.STAFFCHAT)) send(p, new Packets.StaffChatData(true, StaffChat.recent()));
        });
        toServer(Packets.SendStaffChat.class, Packets.SendStaffChat::write, Packets.SendStaffChat::read, (m, p) -> {
            if (Perms.has(p, Perm.STAFFCHAT)) StaffChat.send(p.server, p.getGameProfile().getName(), m.text());
        });
    }

    private static <T> void toClient(Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec, Consumer<T> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(enc).decoder(dec)
                .consumerMainThread((T msg, Supplier<NetworkEvent.Context> ctx) -> handler.accept(msg))
                .add();
    }

    private static <T> void toServer(Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
                                     BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(enc).decoder(dec)
                .consumerMainThread((T msg, Supplier<NetworkEvent.Context> ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null && ModData.loaded()) handler.accept(msg, sender);
                })
                .add();
    }

    public static boolean hasMod(ServerPlayer p) {
        return p.connection != null && CHANNEL.isRemotePresent(p.connection.connection);
    }

    private static void send(ServerPlayer p, Object msg) {
        if (hasMod(p)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    // ───────────────────────────── state ─────────────────────────────

    public static void syncStaff(ServerPlayer p) {
        int mask = 0;
        for (Perm perm : Perm.values()) if (Perms.has(p, perm)) mask |= 1 << perm.ordinal();
        Packets.StaffState state = new Packets.StaffState(mask, Vanish.isVanished(p.getUUID()), SeeInvisible.enabled(p), StaffChat.isToggled(p));
        Packets.StaffState last = lastState.get(p.getUUID());
        if (state.equals(last)) return;
        lastState.put(p.getUUID(), state);
        // Roles arrive after joining (KB Discord Auth) and can change while playing: resend the command tree so
        // the client offers the staff commands it can now use, and stops offering the ones it lost.
        if (last != null && last.perms() != mask) p.server.getCommands().sendCommands(p);
        send(p, state);
        syncVanished(p);
    }

    /** Tells one player who is vanished (an empty list if they may not see them). */
    public static void syncVanished(ServerPlayer p) {
        List<UUID> list = Perms.has(p, Perm.VANISH_SEE) ? List.copyOf(ModData.get().vanished()) : List.of();
        send(p, new Packets.Vanished(list));
    }

    /** After someone vanishes or comes back: update every staff client. */
    public static void syncVanishedAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncVanished(p);
    }

    public static void forget(ServerPlayer p) {
        lastState.remove(p.getUUID());
        panelViewers.remove(p.getUUID());
    }

    /** Server stopped. */
    public static void reset() {
        lastState.clear();
        panelViewers.clear();
    }

    public static void syncFrozen(ServerPlayer p) {
        ModData.Frozen f = ModData.get().frozen().get(p.getUUID());
        send(p, new Packets.Frozen(f != null, f == null ? "" : f.by));
    }

    public static void staffLine(ServerPlayer p, Packets.StaffLine line) {
        send(p, new Packets.StaffChatData(false, List.of(line)));
    }

    public static void toast(ServerPlayer p, int kind, String title, String body) {
        send(p, new Packets.Toast(kind, title, body));
    }

    public static void openReportScreen(ServerPlayer p) {
        send(p, new Packets.OpenReport());
    }

    // ───────────────────────────── panel ─────────────────────────────

    public static void openPanel(ServerPlayer p) {
        panelViewers.put(p.getUUID(), null);
        sendPanel(p, true);
    }

    /**
     * Pushes the panel to the staff who have it open, only to those whose view changed (a sanction, someone joining,
     * a report, health or position of an online player…). Called once a second; costs nothing while nobody has it open.
     */
    public static void pushPanels(MinecraftServer server) {
        if (panelViewers.isEmpty()) return;
        for (UUID id : List.copyOf(panelViewers.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) panelViewers.remove(id);
            else sendPanel(p, false);
        }
    }

    private static boolean isStaff(ServerPlayer p) {
        for (Perm perm : Perm.values()) if (perm != Perm.EXEMPT && Perms.has(p, perm)) return true;
        return false;
    }

    private static void sendPanel(ServerPlayer viewer, boolean open) {
        if (!isStaff(viewer)) {
            panelViewers.remove(viewer.getUUID());
            return;
        }
        MinecraftServer server = viewer.server;
        boolean seeVanished = Perms.has(viewer, Perm.VANISH_SEE);
        long now = System.currentTimeMillis();
        List<Packets.PlayerRow> rows = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!Vanish.canSee(viewer, p) && !seeVanished) continue;
            seen.add(p.getUUID());
            rows.add(row(p.getUUID(), p.getGameProfile().getName(), p, now));
        }
        // Players seen in the last 14 days, and anyone with an active mute or ban.
        List<Map.Entry<UUID, ModData.PlayerRecord>> offline = new ArrayList<>(ModData.get().players().entrySet());
        offline.sort(Comparator.comparingLong((Map.Entry<UUID, ModData.PlayerRecord> e) -> e.getValue().lastSeen).reversed());
        int added = 0;
        for (Map.Entry<UUID, ModData.PlayerRecord> e : offline) {
            if (seen.contains(e.getKey()) || e.getValue().name == null) continue;
            boolean recent = now - e.getValue().lastSeen < 14L * 86_400_000L;
            boolean sanctioned = ModData.get().active(Type.BAN, e.getKey(), null) != null || ModData.get().active(Type.MUTE, e.getKey(), null) != null;
            if (!recent && !sanctioned) continue;
            rows.add(row(e.getKey(), e.getValue().name, null, now));
            if (++added >= 150) break;
        }
        List<Packets.ReportRow> reports = new ArrayList<>();
        if (Perms.has(viewer, Perm.REPORTS)) {
            for (ModData.Report r : ModData.get().reports()) {
                if (r.closed) continue;
                reports.add(new Packets.ReportRow(r.id, r.reporterName, r.targetName, r.reason, r.at,
                        r.dimension.replace("minecraft:", "") + " " + r.x + " " + r.y + " " + r.z));
            }
        }
        Packets.Panel panel = new Packets.Panel(open, rows, reports);
        if (!open && panel.equals(panelViewers.get(viewer.getUUID()))) return;
        panelViewers.put(viewer.getUUID(), new Packets.Panel(false, rows, reports));
        send(viewer, panel);
    }

    private static Packets.PlayerRow row(UUID uuid, String name, ServerPlayer online, long now) {
        DiscordLink.Info discord = DiscordLink.get(uuid);
        String did = discord == null ? null : discord.discordId();
        Sanction mute = ModData.get().active(Type.MUTE, uuid, did);
        Sanction ban = ModData.get().active(Type.BAN, uuid, did);
        ModData.PlayerRecord rec = ModData.get().player(uuid);
        boolean staff = online != null ? isStaff(online) : rec != null && rec.exempt;
        return new Packets.PlayerRow(uuid, name, online != null,
                online == null ? 0 : online.latency,
                online == null ? 0 : online.getHealth(), online == null ? 0 : online.getMaxHealth(),
                online == null ? "" : online.level().dimension().location().toString(),
                online == null ? 0 : online.getBlockX(), online == null ? 0 : online.getBlockY(), online == null ? 0 : online.getBlockZ(),
                discord == null ? "" : discord.display(),
                Sanctions.activeWarns(uuid, did), ModData.get().historyOf(uuid, did).size(),
                mute == null ? -1 : mute.expiresAt, ban == null ? -1 : ban.expiresAt,
                Freeze.isFrozen(uuid), Vanish.isVanished(uuid), staff,
                rec == null ? 0 : rec.lastSeen);
    }

    private static void sendHistory(ServerPlayer viewer, String name) {
        if (!Perms.has(viewer, Perm.HISTORY)) return;
        Targets t = Targets.resolve(viewer.server, name);
        if (t == null) return;
        long now = System.currentTimeMillis();
        List<Packets.HistoryRow> rows = new ArrayList<>();
        for (Sanction s : ModData.get().historyOf(t.uuid(), t.discordId())) {
            rows.add(new Packets.HistoryRow(s.id, s.type.ordinal(), s.createdAt, s.expiresAt, s.revoked, s.revokedBy,
                    s.staffName, s.reason, s.active(now), s.origin == null ? "" : s.origin));
        }
        send(viewer, new Packets.History(t.name(), rows));
    }

    /** Client-only code, reached without loading client classes on a dedicated server. */
    public static final class ClientBridge {
        public interface Handler {
            void staffState(Packets.StaffState m);

            void frozen(Packets.Frozen m);

            void panel(Packets.Panel m);

            void history(Packets.History m);

            void openReport();

            void toast(Packets.Toast m);

            void vanished(Packets.Vanished m);

            void staffChat(Packets.StaffChatData m);
        }

        private static Handler handler;

        public static void set(Handler h) {
            handler = h;
        }

        static void run(Consumer<Handler> action) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (handler != null) action.accept(handler);
            });
        }
    }
}
