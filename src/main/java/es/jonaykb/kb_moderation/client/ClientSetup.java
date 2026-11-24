package es.jonaykb.kb_moderation.client;

import com.mojang.blaze3d.platform.InputConstants;
import es.jonaykb.kb_moderation.KbModeration;
import es.jonaykb.kb_moderation.client.gui.InvseeScreen;
import es.jonaykb.kb_moderation.client.gui.PanelScreen;
import es.jonaykb.kb_moderation.invsee.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import es.jonaykb.kb_moderation.client.gui.ReportScreen;
import es.jonaykb.kb_moderation.network.Net;
import es.jonaykb.kb_moderation.network.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientSetup {
    private static final String CATEGORY = "key.categories.kb_moderation";
    public static final KeyMapping OPEN_PANEL = new KeyMapping("key.kb_moderation.panel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_REPORT = new KeyMapping("key.kb_moderation.report", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        Net.ClientBridge.set(new ClientNet());
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> MenuScreens.register(ModMenus.INVSEE.get(), InvseeScreen::new)));
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(OPEN_PANEL);
            e.register(OPEN_REPORT);
        });
        modBus.addListener((RegisterGuiOverlaysEvent e) ->
                e.registerAboveAll(KbModeration.MOD_ID, (gui, g, partialTick, w, h) -> Hud.render(g, w, h)));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) tick();
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientState.reset());
        // Staff see "◎" before the name of vanished staff, so they know the others cannot see them.
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderNameTagEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.world.entity.player.Player p && ClientState.isVanished(p.getUUID())) {
                e.setContent(VanishMark.mark(e.getContent()));
            }
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (OPEN_PANEL.consumeClick()) {
            if (ClientState.isStaff() && mc.screen == null) {
                mc.setScreen(new PanelScreen(null));
                Net.CHANNEL.sendToServer(new Packets.RequestPanel());
            }
        }
        while (OPEN_REPORT.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ReportScreen(null));
        }
    }
}
