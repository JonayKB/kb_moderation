package es.jonaykb.kb_moderation.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything the mod remembers, in {@code <world>/kb_moderation/data.json}. Only touched on the server thread. */
public final class ModData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public enum Type { WARN, MUTE, KICK, BAN, NOTE }

    public static final class Sanction {
        public int id;
        public Type type;
        public UUID target;
        public String targetName;
        /** Discord account of the target when it was given: mutes and bans also apply to its other accounts. */
        public String discordId;
        public UUID staff;
        public String staffName;
        public String reason;
        public long createdAt;
        /** Mutes and bans: end time in ms, 0 = permanent. */
        public long expiresAt;
        public boolean revoked;
        public String revokedBy;
        public long revokedAt;
        /** Where it was given: null = this server; "discord" = from KB Discord Bot (panel or Discord); "minecraft" = another server. */
        public String origin;
        /** The same sanction in KB Discord Bot (case id), once synced. */
        public Integer botCaseId;

        public boolean permanent() {
            return expiresAt == 0;
        }

        public boolean active(long now) {
            if (revoked) return false;
            if (type == Type.KICK || type == Type.NOTE) return false;
            return expiresAt == 0 || expiresAt > now;
        }
    }

    public static final class PlayerRecord {
        public String name;
        public String lastIp;
        public long firstSeen;
        public long lastSeen;
        /** Last known: whether the player had the "exempt" permission, to protect staff while they are offline. */
        public boolean exempt;
    }

    public static final class Frozen {
        public String by;
        public long since;
        public String dimension;
        public double x;
        public double y;
        public double z;
    }

    public static final class Report {
        public int id;
        public UUID reporter;
        public String reporterName;
        public UUID target;
        public String targetName;
        public String reason;
        public long at;
        public String dimension;
        public int x;
        public int y;
        public int z;
        public boolean closed;
        public String closedBy;
    }

    /** Something waiting to be sent to KB Discord Bot, in order (kept across restarts). */
    public static final class Outbox {
        public String path;
        public java.util.Map<String, Object> body;
        /** Sanction whose botCaseId the answer fills in (actions), or null. */
        public Integer sanctionId;
        /** delete: the sanction whose bot case is removed (its botCaseId is read when sending). */
        public Integer deleteOf;
        public int attempts;
        public long nextTry;
    }

    private static final class Contents {
        int nextSanctionId = 1;
        int nextReportId = 1;
        List<Sanction> sanctions = new ArrayList<>();
        Map<UUID, PlayerRecord> players = new HashMap<>();
        Map<UUID, Frozen> frozen = new HashMap<>();
        Set<UUID> vanished = new HashSet<>();
        Set<UUID> seeInvisible = new HashSet<>();
        List<Report> reports = new ArrayList<>();
        /** Last event of KB Discord Bot already applied here; -1 = never synced (imports its whole history). */
        long botCursor = -1;
        List<Outbox> outbox = new ArrayList<>();
    }

    private static ModData instance;

    private final Path file;
    private Contents c = new Contents();

    private ModData(Path file) {
        this.file = file;
    }

    public static ModData get() {
        if (instance == null) throw new IllegalStateException("kb_moderation data used before the server started");
        return instance;
    }

    public static boolean loaded() {
        return instance != null;
    }

    public static void load(MinecraftServer server) {
        ModData data = new ModData(server.getWorldPath(LevelResource.ROOT).resolve("kb_moderation").resolve("data.json"));
        if (Files.exists(data.file)) {
            try (Reader r = Files.newBufferedReader(data.file, StandardCharsets.UTF_8)) {
                Contents read = GSON.fromJson(r, Contents.class);
                if (read != null) data.c = read;
            } catch (IOException | RuntimeException e) {
                // Never start with empty data over a file we could not read: that would lift every ban.
                throw new IllegalStateException("Could not read " + data.file + ". Fix or remove it.", e);
            }
        }
        if (data.c.sanctions == null) data.c.sanctions = new ArrayList<>();
        if (data.c.players == null) data.c.players = new HashMap<>();
        if (data.c.frozen == null) data.c.frozen = new HashMap<>();
        if (data.c.vanished == null) data.c.vanished = new HashSet<>();
        if (data.c.seeInvisible == null) data.c.seeInvisible = new HashSet<>();
        if (data.c.reports == null) data.c.reports = new ArrayList<>();
        if (data.c.outbox == null) data.c.outbox = new ArrayList<>();
        instance = data;
    }

    public static void unload() {
        if (instance != null) instance.save();
        instance = null;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(c, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Could not save {}", file, e);
        }
    }

    // ───────────────────────────── sanctions ─────────────────────────────

    public Sanction addSanction(Sanction s) {
        s.id = c.nextSanctionId++;
        c.sanctions.add(s);
        save();
        return s;
    }

    public List<Sanction> sanctions() {
        return c.sanctions;
    }

    public Sanction sanction(int id) {
        for (Sanction s : c.sanctions) if (s.id == id) return s;
        return null;
    }

    /** The active mute or ban that applies to this account, or to its Discord account. Longest one first. */
    public Sanction active(Type type, UUID uuid, String discordId) {
        long now = System.currentTimeMillis();
        Sanction best = null;
        for (Sanction s : c.sanctions) {
            if (s.type != type || !s.active(now)) continue;
            boolean match = uuid.equals(s.target) || (discordId != null && discordId.equals(s.discordId));
            if (!match) continue;
            if (best == null || s.permanent() || (!best.permanent() && s.expiresAt > best.expiresAt)) best = s;
        }
        return best;
    }

    /** Every active mute/ban of this kind on the account or its Discord account (to lift them all at once). */
    public List<Sanction> allActive(Type type, UUID uuid, String discordId) {
        long now = System.currentTimeMillis();
        List<Sanction> out = new ArrayList<>();
        for (Sanction s : c.sanctions) {
            if (s.type != type || !s.active(now)) continue;
            if (uuid.equals(s.target) || (discordId != null && discordId.equals(s.discordId))) out.add(s);
        }
        return out;
    }

    public List<Sanction> historyOf(UUID uuid, String discordId) {
        List<Sanction> out = new ArrayList<>();
        for (Sanction s : c.sanctions) {
            if (uuid.equals(s.target) || (discordId != null && discordId.equals(s.discordId))) out.add(s);
        }
        return out;
    }

    // ───────────────────────────── players ─────────────────────────────

    public PlayerRecord player(UUID uuid) {
        return c.players.get(uuid);
    }

    public PlayerRecord playerOrCreate(UUID uuid) {
        return c.players.computeIfAbsent(uuid, u -> new PlayerRecord());
    }

    public Map<UUID, PlayerRecord> players() {
        return c.players;
    }

    public UUID findByName(String name) {
        UUID found = null;
        long seen = -1;
        for (Map.Entry<UUID, PlayerRecord> e : c.players.entrySet()) {
            if (name.equalsIgnoreCase(e.getValue().name) && e.getValue().lastSeen > seen) {
                found = e.getKey();
                seen = e.getValue().lastSeen;
            }
        }
        return found;
    }

    // ───────────────────────────── freeze / vanish ─────────────────────────────

    public Map<UUID, Frozen> frozen() {
        return c.frozen;
    }

    public Set<UUID> vanished() {
        return c.vanished;
    }

    /** Staff who turned on /verinvisibles. */
    public Set<UUID> seeInvisible() {
        return c.seeInvisible;
    }

    // ───────────────────────────── reports ─────────────────────────────

    public Report addReport(Report r) {
        r.id = c.nextReportId++;
        c.reports.add(r);
        save();
        return r;
    }

    public List<Report> reports() {
        return c.reports;
    }

    public Report report(int id) {
        for (Report r : c.reports) if (r.id == id) return r;
        return null;
    }

    // ───────────────────────────── KB Discord Bot sync ─────────────────────────────

    public long botCursor() {
        return c.botCursor;
    }

    public void setBotCursor(long cursor) {
        c.botCursor = cursor;
    }

    public List<Outbox> outbox() {
        return c.outbox;
    }

    public Sanction sanctionByBotCase(int caseId) {
        for (Sanction s : c.sanctions) if (s.botCaseId != null && s.botCaseId == caseId) return s;
        return null;
    }
}
