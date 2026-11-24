package es.jonaykb.kb_moderation.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.UUID;

/** The data each packet carries. Encoding lives here; handling in {@link Net} (server) and ClientNet (client). */
public final class Packets {
    private Packets() {
    }

    // ───────────────────────────── server → client ─────────────────────────────

    /** What the local player may do and their own toggles. perms = bit per Perm ordinal. */
    public record StaffState(int perms, boolean vanished, boolean seeInvisible, boolean staffChat) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(perms);
            b.writeBoolean(vanished);
            b.writeBoolean(seeInvisible);
            b.writeBoolean(staffChat);
        }

        public static StaffState read(FriendlyByteBuf b) {
            return new StaffState(b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean());
        }
    }

    public record Frozen(boolean frozen, String by) {
        public void write(FriendlyByteBuf b) {
            b.writeBoolean(frozen);
            b.writeUtf(by);
        }

        public static Frozen read(FriendlyByteBuf b) {
            return new Frozen(b.readBoolean(), b.readUtf());
        }
    }

    /** -1 = none, 0 = permanent, else end time (ms). */
    public record PlayerRow(UUID uuid, String name, boolean online, int ping, float health, float maxHealth, String dimension,
                            int x, int y, int z, String discord, int warns, int sanctions, long mutedUntil, long bannedUntil,
                            boolean frozen, boolean vanished, boolean staff, long lastSeen) {
        void write(FriendlyByteBuf b) {
            b.writeUUID(uuid);
            b.writeUtf(name);
            b.writeBoolean(online);
            b.writeVarInt(ping);
            b.writeFloat(health);
            b.writeFloat(maxHealth);
            b.writeUtf(dimension);
            b.writeInt(x);
            b.writeInt(y);
            b.writeInt(z);
            b.writeUtf(discord);
            b.writeVarInt(warns);
            b.writeVarInt(sanctions);
            b.writeLong(mutedUntil);
            b.writeLong(bannedUntil);
            b.writeBoolean(frozen);
            b.writeBoolean(vanished);
            b.writeBoolean(staff);
            b.writeLong(lastSeen);
        }

        static PlayerRow read(FriendlyByteBuf b) {
            return new PlayerRow(b.readUUID(), b.readUtf(), b.readBoolean(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readUtf(),
                    b.readInt(), b.readInt(), b.readInt(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readLong(), b.readLong(),
                    b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readLong());
        }
    }

    public record ReportRow(int id, String reporter, String target, String reason, long at, String where) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeUtf(reporter);
            b.writeUtf(target);
            b.writeUtf(reason);
            b.writeLong(at);
            b.writeUtf(where);
        }

        static ReportRow read(FriendlyByteBuf b) {
            return new ReportRow(b.readVarInt(), b.readUtf(), b.readUtf(), b.readUtf(), b.readLong(), b.readUtf());
        }
    }

    /** open = open the panel; otherwise only refresh it if it is open. */
    public record Panel(boolean open, List<PlayerRow> players, List<ReportRow> reports) {
        public void write(FriendlyByteBuf b) {
            b.writeBoolean(open);
            b.writeCollection(players, (buf, r) -> r.write(buf));
            b.writeCollection(reports, (buf, r) -> r.write(buf));
        }

        public static Panel read(FriendlyByteBuf b) {
            return new Panel(b.readBoolean(), b.readList(PlayerRow::read), b.readList(ReportRow::read));
        }
    }

    /** type = ModData.Type ordinal; expiresAt 0 = permanent. */
    /** origin: "" = this server, "discord" = bot panel or Discord, "minecraft" = another server. */
    public record HistoryRow(int id, int type, long createdAt, long expiresAt, boolean revoked, String revokedBy, String staff,
                             String reason, boolean active, String origin) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeVarInt(type);
            b.writeLong(createdAt);
            b.writeLong(expiresAt);
            b.writeBoolean(revoked);
            b.writeUtf(revokedBy == null ? "" : revokedBy);
            b.writeUtf(staff);
            b.writeUtf(reason);
            b.writeBoolean(active);
            b.writeUtf(origin);
        }

        static HistoryRow read(FriendlyByteBuf b) {
            return new HistoryRow(b.readVarInt(), b.readVarInt(), b.readLong(), b.readLong(), b.readBoolean(), b.readUtf(), b.readUtf(),
                    b.readUtf(), b.readBoolean(), b.readUtf());
        }
    }

    public record History(String name, List<HistoryRow> rows) {
        public void write(FriendlyByteBuf b) {
            b.writeUtf(name);
            b.writeCollection(rows, (buf, r) -> r.write(buf));
        }

        public static History read(FriendlyByteBuf b) {
            return new History(b.readUtf(), b.readList(HistoryRow::read));
        }
    }

    public record OpenReport() {
        public void write(FriendlyByteBuf b) {
        }

        public static OpenReport read(FriendlyByteBuf b) {
            return new OpenReport();
        }
    }

    /** kind: 0 info, 1 report, 2 warning, 3 success. */
    public record Toast(int kind, String title, String body) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(kind);
            b.writeUtf(title);
            b.writeUtf(body);
        }

        public static Toast read(FriendlyByteBuf b) {
            return new Toast(b.readVarInt(), b.readUtf(), b.readUtf());
        }
    }

    /** Who is vanished right now; only sent to staff who may see them. */
    public record Vanished(List<UUID> players) {
        public void write(FriendlyByteBuf b) {
            b.writeCollection(players, FriendlyByteBuf::writeUUID);
        }

        public static Vanished read(FriendlyByteBuf b) {
            return new Vanished(b.readList(FriendlyByteBuf::readUUID));
        }
    }

    public record StaffLine(long at, String from, String text) {
        void write(FriendlyByteBuf b) {
            b.writeLong(at);
            b.writeUtf(from);
            b.writeUtf(text);
        }

        static StaffLine read(FriendlyByteBuf b) {
            return new StaffLine(b.readLong(), b.readUtf(), b.readUtf());
        }
    }

    /** full = the whole recent history (when the tab opens); otherwise one new message. */
    public record StaffChatData(boolean full, List<StaffLine> lines) {
        public void write(FriendlyByteBuf b) {
            b.writeBoolean(full);
            b.writeCollection(lines, (buf, l) -> l.write(buf));
        }

        public static StaffChatData read(FriendlyByteBuf b) {
            return new StaffChatData(b.readBoolean(), b.readList(StaffLine::read));
        }
    }

    // ───────────────────────────── client → server ─────────────────────────────

    public record RequestPanel() {
        public void write(FriendlyByteBuf b) {
        }

        public static RequestPanel read(FriendlyByteBuf b) {
            return new RequestPanel();
        }
    }

    /** Opened the staff chat tab: send me the recent messages. */
    public record RequestStaffChat() {
        public void write(FriendlyByteBuf b) {
        }

        public static RequestStaffChat read(FriendlyByteBuf b) {
            return new RequestStaffChat();
        }
    }

    /** A message written in the staff chat tab. */
    public record SendStaffChat(String text) {
        public void write(FriendlyByteBuf b) {
            b.writeUtf(text, 256);
        }

        public static SendStaffChat read(FriendlyByteBuf b) {
            return new SendStaffChat(b.readUtf(256));
        }
    }

    public record RequestHistory(String name) {
        public void write(FriendlyByteBuf b) {
            b.writeUtf(name, 32);
        }

        public static RequestHistory read(FriendlyByteBuf b) {
            return new RequestHistory(b.readUtf(32));
        }
    }
}
