package es.jonaykb.kb_moderation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import es.jonaykb.kb_moderation.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Status badges for staff (vanish, staff chat, see invisible) and the frost screen of a frozen player. */
public final class Hud {
    private static final ResourceLocation FROST = new ResourceLocation("textures/misc/powder_snow_outline.png");

    private Hud() {
    }

    public static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        if (ClientState.frozen) renderFrozen(g, font, width, height);

        // The badges sit where the tab list opens: hide them while it is shown.
        if (mc.options.keyPlayerList.isDown()) return;
        int total = 0;
        String[] labels = {"◎ VANISH", "✉ CHAT STAFF", "◉ VES INVISIBLES"};
        boolean[] on = {ClientState.vanished, ClientState.staffChat, ClientState.seeInvisible};
        int[] colors = {Theme.CYAN, Theme.PINK, Theme.YELLOW};
        for (int i = 0; i < 3; i++) if (on[i]) total += Theme.pillWidth(font, labels[i]) + 4;
        if (total == 0) return;
        int x = (width - total) / 2;
        // Below the boss bars if any, otherwise at the top.
        int y = 3;
        for (int i = 0; i < 3; i++) {
            if (!on[i]) continue;
            float pulse = i == 0 ? 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 400.0) : 1f;
            x += Theme.pill(g, font, labels[i], x, y, Theme.withAlpha(colors[i], pulse)) + 4;
        }
    }

    private static void renderFrozen(GuiGraphics g, Font font, int width, int height) {
        float t = Theme.ease(ClientState.frozenAt, 600);
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 0.85f * t);
        g.blit(FROST, 0, 0, -90, 0f, 0f, width, height, width, height);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        float pulse = 0.85f + 0.15f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        g.pose().pushPose();
        g.pose().translate(width / 2f, height / 4f, 0);
        g.pose().scale(2.2f * pulse, 2.2f * pulse, 1);
        Component title = Component.literal("❄ CONGELADO ❄");
        g.drawString(font, title, -font.width(title) / 2, 0, Theme.withAlpha(0xFFBFF4FF, t), true);
        g.pose().popPose();
        String sub = "Un moderador (" + ClientState.frozenBy + ") quiere hablar contigo";
        g.drawCenteredString(font, sub, width / 2, height / 4 + 26, Theme.withAlpha(0xFFE8F8FF, t));
        g.drawCenteredString(font, "§7No te desconectes · usa el chat para responder", width / 2, height / 4 + 38, 0xFFFFFFFF);
    }
}
