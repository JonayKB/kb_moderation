package es.jonaykb.kb_moderation;

import es.jonaykb.kb_moderation.command.ModCommands;
import es.jonaykb.kb_moderation.compat.VoiceMute;
import es.jonaykb.kb_moderation.config.ModConfig;
import es.jonaykb.kb_moderation.data.ModData;
import es.jonaykb.kb_moderation.data.ModData.Sanction;
import es.jonaykb.kb_moderation.freeze.Freeze;
import es.jonaykb.kb_moderation.link.DiscordLink;
import es.jonaykb.kb_moderation.network.Net;
import es.jonaykb.kb_moderation.perm.Perm;
import es.jonaykb.kb_moderation.perm.Perms;
import es.jonaykb.kb_moderation.sanction.Sanctions;
import es.jonaykb.kb_moderation.staff.StaffChat;
import es.jonaykb.kb_moderation.sync.BotSync;
import es.jonaykb.kb_moderation.util.Text;
import es.jonaykb.kb_moderation.vanish.Vanish;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ServerEvents {
    /** Online players whose Discord account was already checked against bans. */
    private final Set<UUID> discordChecked = new HashSet<>();
    private int ticks;

    @SubscribeEvent
    public void onAboutToStart(ServerAboutToStartEvent e) {
        ModData.load(e.getServer());
    }

    @SubscribeEvent
    public void onStopped(ServerStoppedEvent e) {
        ModData.unload();
        BotSync.reset();
        discordChecked.clear();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        ModCommands.register(e.getDispatcher());
    }

    // ───────────────────────────── join / leave ─────────────────────────────

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !ModData.loaded()) return;
        long now = System.currentTimeMillis();
        ModData.PlayerRecord r = ModData.get().playerOrCreate(p.getUUID());
        r.name = p.getGameProfile().getName();
        if (r.firstSeen == 0) r.firstSeen = now;
        r.lastSeen = now;
        if (p.connection.getRemoteAddress() instanceof InetSocketAddress a && a.getAddress() != null) {
            r.lastIp = a.getAddress().getHostAddress();
        }
        r.exempt = Perms.has(p, Perm.EXEMPT);
        ModData.get().save();

        Vanish.onJoin(p);
        dropLostVanish(p);
        Freeze.onLogin(p);
        Net.syncStaff(p);
        Net.syncFrozen(p);

        Sanction mute = Sanctions.activeMute(p);
        if (mute != null) p.sendSystemMessage(Sanctions.mutedMessage(mute));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !ModData.loaded()) return;
        ModData.PlayerRecord r = ModData.get().playerOrCreate(p.getUUID());
        r.lastSeen = System.currentTimeMillis();
        r.exempt = Perms.has(p, Perm.EXEMPT);
        ModData.get().save();
        Freeze.onLogout(p);
        StaffChat.forget(p.getUUID());
        discordChecked.remove(p.getUUID());
        Net.forget(p);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !ModData.loaded()) return;
        MinecraftServer server = e.getServer();
        if (++ticks % 20 != 0) return;
        VoiceMute.refresh(server);
        BotSync.tick(server);
        for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
            // An account linked for the first time just now: check its Discord account against bans.
            if (!discordChecked.contains(p.getUUID())) {
                String discordId = DiscordLink.discordId(p.getUUID());
                if (discordId != null) {
                    discordChecked.add(p.getUUID());
                    Component block = Sanctions.loginBlock(p.getGameProfile());
                    if (block != null) {
                        p.connection.disconnect(block);
                        continue;
                    }
                }
            }
            // Roles change while playing (KB Discord Auth re-reads them): keep the client UI in sync.
            if (ticks % 100 == 0) {
                dropLostVanish(p);
                Net.syncStaff(p);
            }
        }
    }

    /** Someone who lost the vanish permission (role removed) could never leave it: take them out. */
    private static void dropLostVanish(ServerPlayer p) {
        if (Vanish.isVanished(p.getUUID()) && !Perms.has(p, Perm.VANISH)) Vanish.set(p, false);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && Freeze.isFrozen(p.getUUID())) Freeze.tick(p);
    }

    // ───────────────────────────── chat & commands ─────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        Sanction mute = Sanctions.activeMute(p);
        if (mute != null) {
            e.setCanceled(true);
            p.sendSystemMessage(Sanctions.mutedMessage(mute));
            return;
        }
        if (StaffChat.isToggled(p)) {
            e.setCanceled(true);
            StaffChat.send(p.server, p.getGameProfile().getName(), e.getRawText());
            return;
        }
        // Writing in public chat would give a vanished player away: only with "!" in front, on purpose.
        if (Vanish.isVanished(p.getUUID())) {
            String raw = e.getRawText();
            if (raw.startsWith("!") && raw.length() > 1) {
                e.setMessage(Component.literal(raw.substring(1).trim()));
            } else {
                e.setCanceled(true);
                p.sendSystemMessage(Text.msg("&bEstás en vanish: &7ese mensaje te delataría. Usa &f/sc &7para el staff, "
                        + "o empieza con &f! &7para escribir en público."));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onCommand(CommandEvent e) {
        CommandSourceStack src = e.getParseResults().getContext().getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) return;
        String input = e.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        String root = input.split(" ", 2)[0].toLowerCase();
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);

        if (Freeze.isFrozen(p.getUUID()) && !ModConfig.FROZEN_COMMANDS.get().contains(root)) {
            e.setCanceled(true);
            p.sendSystemMessage(Text.error("Estás congelado: no puedes usar comandos."));
            return;
        }
        if (ModConfig.MUTED_COMMANDS.get().contains(root)) {
            Sanction mute = Sanctions.activeMute(p);
            if (mute != null) {
                e.setCanceled(true);
                p.sendSystemMessage(Sanctions.mutedMessage(mute));
            }
        }
    }

    // ───────────────────────────── freeze ─────────────────────────────

    private static boolean frozen(Object entity) {
        return entity instanceof ServerPlayer p && Freeze.isFrozen(p.getUUID());
    }

    private static void deny(PlayerInteractEvent e) {
        if (frozen(e.getEntity())) {
            if (e.isCancelable()) e.setCanceled(true);
            e.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        deny(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        deny(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        deny(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        deny(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        deny(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent e) {
        if (frozen(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (frozen(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent e) {
        if (frozen(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onToss(ItemTossEvent e) {
        if (!frozen(e.getPlayer())) return;
        // The item has already left the inventory when this fires: give it back.
        Player p = e.getPlayer();
        if (!p.getInventory().add(e.getEntity().getItem().copy())) return; // full: let it drop
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPickup(EntityItemPickupEvent e) {
        if (frozen(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingAttackEvent e) {
        if (ModConfig.FROZEN_INVULNERABLE.get() && frozen(e.getEntity())) e.setCanceled(true);
    }

    /** Staff moving a frozen player with /tp: the new spot becomes the freeze spot. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTeleportCommand(EntityTeleportEvent.TeleportCommand e) {
        if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p && Freeze.isFrozen(p.getUUID())) {
            Freeze.moveTo(p, e.getTargetX(), e.getTargetY(), e.getTargetZ());
        }
    }

    // ───────────────────────────── vanish ─────────────────────────────

    @SubscribeEvent
    public void onTarget(LivingChangeTargetEvent e) {
        if (ModConfig.VANISH_NO_MOB_TARGET.get() && e.getNewTarget() instanceof ServerPlayer p && Vanish.isVanished(p.getUUID())) {
            e.setCanceled(true);
        }
    }
}
