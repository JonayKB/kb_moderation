package es.jonaykb.kb_moderation;

import es.jonaykb.kb_moderation.client.ClientSetup;
import es.jonaykb.kb_moderation.config.ModConfig;
import es.jonaykb.kb_moderation.invsee.ModMenus;
import es.jonaykb.kb_moderation.network.Net;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KbModeration.MOD_ID)
public final class KbModeration {
    public static final String MOD_ID = "kb_moderation";

    public KbModeration() {
        // COMMON, not SERVER: SERVER configs are sent to clients and this one holds the webhook URLs.
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
        Net.register();
        ModMenus.MENUS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(FMLJavaModLoadingContext.get().getModEventBus()));
    }
}
