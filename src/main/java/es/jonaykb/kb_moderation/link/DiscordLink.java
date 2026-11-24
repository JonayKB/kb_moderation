package es.jonaykb.kb_moderation.link;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Read-only view of KB Discord Auth's links (Minecraft account → Discord account), through reflection so this mod
 * also works without it. Everything returns null / empty when KB Discord Auth is missing or not started yet.
 */
public final class DiscordLink {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record Info(String discordId, String username, String globalName, boolean inGuild) {
        public String display() {
            return username == null || username.isBlank() ? discordId : "@" + username;
        }
    }

    private static boolean resolved;
    private static Method managerGet;
    private static Method storeMethod;
    private static Method storeGet;
    private static Method storeFindByName;
    private static Method storeForEach;
    private static Field fDiscordId;
    private static Field fUsername;
    private static Field fGlobalName;
    private static Field fInGuild;
    private static Field fMinecraftName;

    private DiscordLink() {
    }

    public static boolean available() {
        return resolve();
    }

    private static synchronized boolean resolve() {
        if (resolved) return managerGet != null;
        resolved = true;
        if (!ModList.get().isLoaded("kb_discord_auth")) return false;
        try {
            Class<?> manager = Class.forName("es.jonaykb.kb_discord_auth.server.AuthManager");
            Class<?> store = Class.forName("es.jonaykb.kb_discord_auth.server.LinkStore");
            Class<?> record = Class.forName("es.jonaykb.kb_discord_auth.server.LinkStore$LinkRecord");
            Method get = manager.getMethod("get");
            storeMethod = manager.getMethod("store");
            storeGet = store.getMethod("get", UUID.class);
            storeFindByName = store.getMethod("findByName", String.class);
            storeForEach = store.getMethod("forEach", BiConsumer.class);
            fDiscordId = record.getField("discordId");
            fUsername = record.getField("discordUsername");
            fGlobalName = record.getField("discordGlobalName");
            fInGuild = record.getField("inGuild");
            fMinecraftName = record.getField("minecraftName");
            managerGet = get;
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("KB Discord Auth is installed but its links could not be read; Discord bans are off: {}", e.toString());
            return false;
        }
    }

    private static Object store() throws ReflectiveOperationException {
        if (!resolve()) return null;
        Object manager = managerGet.invoke(null);
        return manager == null ? null : storeMethod.invoke(manager);
    }

    public static Info get(UUID uuid) {
        try {
            Object store = store();
            if (store == null) return null;
            Object r = storeGet.invoke(store, uuid);
            if (r == null) return null;
            String id = (String) fDiscordId.get(r);
            if (id == null || id.isBlank()) return null;
            return new Info(id, (String) fUsername.get(r), (String) fGlobalName.get(r), fInGuild.getBoolean(r));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("Could not read the Discord link of {}", uuid, e);
            return null;
        }
    }

    public static String discordId(UUID uuid) {
        Info info = get(uuid);
        return info == null ? null : info.discordId();
    }

    public static UUID findByName(String name) {
        try {
            Object store = store();
            return store == null ? null : (UUID) storeFindByName.invoke(store, name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public record Account(UUID uuid, String name) {
    }

    /** A String value of KB Discord Auth's config (e.g. "STATS_ENDPOINT"), or "" if not available. */
    public static String authConfig(String field) {
        if (!ModList.get().isLoaded("kb_discord_auth")) return "";
        try {
            Object value = Class.forName("es.jonaykb.kb_discord_auth.config.AuthConfig").getField(field).get(null);
            Object got = value.getClass().getMethod("get").invoke(value);
            return got instanceof String str ? str.trim() : "";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "";
        }
    }

    /** Every Minecraft account linked to this Discord account. */
    public static List<Account> accountsOf(String discordId) {
        List<Account> out = new ArrayList<>();
        if (discordId == null) return out;
        try {
            Object store = store();
            if (store == null) return out;
            BiConsumer<UUID, Object> visit = (uuid, r) -> {
                try {
                    if (discordId.equals(fDiscordId.get(r))) out.add(new Account(uuid, (String) fMinecraftName.get(r)));
                } catch (IllegalAccessException ignored) {
                }
            };
            storeForEach.invoke(store, visit);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.debug("Could not list the accounts of Discord {}", discordId, e);
        }
        return out;
    }
}
