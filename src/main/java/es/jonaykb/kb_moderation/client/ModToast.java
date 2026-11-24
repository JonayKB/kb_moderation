package es.jonaykb.kb_moderation.client;

import es.jonaykb.kb_moderation.client.gui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Notification in the corner, in the same style as the panel. kind: 0 info, 1 report, 2 warning, 3 success. */
public final class ModToast implements Toast {
    private static final int[] COLOR = {Theme.ACCENT, Theme.RED, Theme.YELLOW, Theme.GREEN};
    private static final ItemStack[] ICON = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.RED_BANNER),
            new ItemStack(Items.BELL), new ItemStack(Items.EMERALD)};

    private final int kind;
    private final String title;
    private final String body;
    private final long duration;

    public ModToast(int kind, String title, String body) {
        this.kind = Math.max(0, Math.min(3, kind));
        this.title = title;
        this.body = body;
        this.duration = this.kind == 1 ? 9000 : 4500;
    }

    @Override
    public int width() {
        return 180;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        Font font = toasts.getMinecraft().font;
        int c = COLOR[kind];
        Theme.panel(g, 0, 0, width(), height(), Theme.BG);
        g.fill(1, 1, 3, height() - 1, c);
        g.renderItem(ICON[kind], 8, 8);
        g.drawString(font, Theme.fit(font, title, width() - 34), 30, 7, c, false);
        g.drawString(font, Theme.fit(font, body, width() - 34), 30, 18, Theme.TEXT, false);
        // Time left, as a thin bar at the bottom.
        float left = 1f - Math.min(1f, time / (float) duration);
        g.fill(3, height() - 2, 3 + (int) ((width() - 4) * left), height() - 1, Theme.withAlpha(c, 0.6f));
        return time >= duration ? Visibility.HIDE : Visibility.SHOW;
    }
}
