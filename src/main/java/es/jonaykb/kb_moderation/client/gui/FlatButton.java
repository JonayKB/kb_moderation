package es.jonaykb.kb_moderation.client.gui;

import es.jonaykb.kb_moderation.client.Sounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Flat colored button with an optional item icon. Hover smoothly brightens it. With holdMs > 0 it has to be held
 * down that long (a bar fills up) before it fires: for actions that are hard to undo.
 */
public class FlatButton extends AbstractButton {
    private final int color;
    private final ItemStack icon;
    private final Consumer<FlatButton> onPress;
    private final long holdMs;
    private float hover;
    private long holdStart = -1;
    private boolean wasHovered;
    private boolean selected;

    public FlatButton(int x, int y, int w, int h, String label, int color, ItemStack icon, Consumer<FlatButton> onPress) {
        this(x, y, w, h, label, color, icon, 0, onPress);
    }

    public FlatButton(int x, int y, int w, int h, String label, int color, ItemStack icon, long holdMs, Consumer<FlatButton> onPress) {
        super(x, y, w, h, Component.literal(label));
        this.color = color;
        this.icon = icon;
        this.holdMs = holdMs;
        this.onPress = onPress;
    }

    public FlatButton tooltip(String text) {
        setTooltip(text == null ? null : Tooltip.create(Component.literal(text)));
        return this;
    }

    public FlatButton enabled(boolean on) {
        this.active = on;
        return this;
    }

    public FlatButton selected(boolean on) {
        this.selected = on;
        return this;
    }

    @Override
    public void onPress() {
        if (holdMs > 0) return; // fired from renderWidget when the hold completes
        onPress.accept(this);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (holdMs > 0 && active && visible && button == 0 && isMouseOver(mx, my)) {
            holdStart = System.currentTimeMillis();
            Sounds.select();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (holdMs > 0 && button == 0) holdStart = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void playDownSound(SoundManager sounds) {
        if (holdMs > 0) return;
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused() && active;
        if (hovered && !wasHovered) Sounds.hover();
        wasHovered = hovered;
        hover += ((hovered ? 1f : 0f) - hover) * 0.35f;

        int base = active ? color : 0xFF3A4152;
        int fill = Theme.mix(Theme.withAlpha(base, selected ? 0.55f : 0.22f), Theme.withAlpha(base, 0.55f), hover);
        int x = getX(), y = getY(), w = width, h = height;
        g.fill(x + 1, y, x + w - 1, y + h, fill);
        g.fill(x, y + 1, x + w, y + h - 1, fill);
        // Bottom edge in the full color: gives the button some depth.
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, Theme.withAlpha(base, active ? 0.9f : 0.4f));
        if (selected) {
            g.fill(x + 1, y, x + w - 1, y + 1, Theme.withAlpha(base, 0.9f));
        }

        // The screen only forwards the release when the mouse is still over the button: check the real state.
        if (holdStart > 0 && (!isMouseOver(mouseX, mouseY) || org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) != org.lwjgl.glfw.GLFW.GLFW_PRESS)) {
            holdStart = -1;
        }
        if (holdStart > 0 && active) {
            float t = Math.min(1f, (System.currentTimeMillis() - holdStart) / (float) holdMs);
            g.fill(x + 1, y + 1, x + 1 + (int) ((w - 2) * t), y + h - 1, Theme.withAlpha(base, 0.75f));
            if (t >= 1f) {
                holdStart = -1;
                Sounds.danger();
                onPress.accept(this);
            }
        }

        Font font = Minecraft.getInstance().font;
        int textColor = active ? Theme.TEXT : Theme.FAINT;
        String label = getMessage().getString();
        if (holdStart > 0 && active) label = "Mantén…";
        int contentW = font.width(label) + (icon != null ? 18 : 0);
        int cx = x + (w - contentW) / 2;
        if (icon != null) {
            g.renderItem(icon, cx, y + (h - 16) / 2);
            cx += 18;
        }
        g.drawString(font, Theme.fit(font, label, w - (icon != null ? 22 : 6)), cx, y + (h - 8) / 2, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
