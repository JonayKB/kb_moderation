package es.jonaykb.kb_moderation.vanish;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Added to ChunkMap.TrackedEntity by TrackedEntityMixin. */
public interface TrackedEntityAccess {
    void kbModeration$updatePlayers(List<ServerPlayer> players);
}
