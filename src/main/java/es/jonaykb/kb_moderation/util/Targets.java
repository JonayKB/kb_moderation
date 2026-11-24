package es.jonaykb.kb_moderation.util;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.link.DiscordLink;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A player named in a command, online or not. */
public record Targets(UUID uuid, String name, ServerPlayer online) {

    public static Targets resolve(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null) return new Targets(p.getUUID(), p.getGameProfile().getName(), p);
        if (ModData.loaded()) {
            UUID uuid = ModData.get().findByName(name);
            if (uuid != null) return new Targets(uuid, ModData.get().player(uuid).name, null);
        }
        UUID linked = DiscordLink.findByName(name);
        if (linked != null) return new Targets(linked, name, null);
        if (server.getProfileCache() != null) {
            Optional<GameProfile> profile = server.getProfileCache().get(name);
            if (profile.isPresent()) return new Targets(profile.get().getId(), profile.get().getName(), null);
        }
        return null;
    }

    public String discordId() {
        return DiscordLink.discordId(uuid);
    }

    public static final SuggestionProvider<CommandSourceStack> ONLINE = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder);

    /** Online players first, then everyone the mod has seen. */
    public static final SuggestionProvider<CommandSourceStack> KNOWN = (ctx, builder) -> {
        Set<String> names = new LinkedHashSet<>(java.util.List.of(ctx.getSource().getServer().getPlayerNames()));
        if (ModData.loaded()) ModData.get().players().values().forEach(r -> {
            if (r.name != null) names.add(r.name);
        });
        return SharedSuggestionProvider.suggest(names, builder);
    };
}
