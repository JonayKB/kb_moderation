package es.jonaykb.kb_moderation.client.gui;

import es.jonaykb.kb_moderation.client.Sounds;
import es.jonaykb.kb_moderation.invsee.InvseeMenu;
import es.jonaykb.kb_moderation.invsee.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static es.jonaykb.kb_moderation.invsee.InvseeMenu.*;

/** The inventory viewer, drawn in the same style as the moderation panel. */
public final class InvseeScreen extends AbstractContainerScreen<InvseeMenu> {
    private static final String[] PAGE_NAMES = {"Inventario", "Curios", "Cofre de Ender", "Armadura cosmética", "Contenido"};
    private static final String[] DIMS = {"Overworld", "Nether", "End", "Otra dimensión"};

    private final long openedAt = System.currentTimeMillis();
    private int shownKind = -1, shownPages = -1, shownPage = -1, shownEdit = -1;

    public InvseeScreen(InvseeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = InvseeMenu.GRID_X + 9 * 18 + 8;
        imageHeight = InvseeMenu.HOTBAR_Y + 18 + 7;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int kind = menu.data(D_KIND);
        shownKind = kind;
        shownPages = menu.data(D_PAGES);
        shownPage = menu.data(D_PAGE);
        shownEdit = menu.data(D_EDIT);

        int x = leftPos + 7, y = topPos + GRID_Y - 3, bw = InvseeMenu.GRID_X - 15;
        y = tab(x, y, bw, "Inventario", Items.CHEST, B_MAIN, kind == 0, true);
        y = tab(x, y, bw, "Curios", Items.AMETHYST_SHARD, B_CURIOS, kind == 1, menu.data(D_CURIOS) == 1);
        y = tab(x, y, bw, "Ender", Items.ENDER_CHEST, B_ENDER, kind == 2, true);
        tab(x, y, bw, "Cosmético", Items.LEATHER_CHESTPLATE, B_COSMETIC, kind == 3, menu.data(D_COSMETIC) == 1);
        // Inside a backpack or shulker: "back" on the line of the page title.
        if (kind == 4) {
            int bx = leftPos + GRID_X + font.width(PAGE_NAMES[4]) + 8;
            addRenderableWidget(new FlatButton(bx, topPos + GRID_Y - 15, 52, 12, "← Volver", Theme.GREEN, null, b -> button(B_BACK)));
        }

        // Page arrows on the line of the page title.
        if (shownPages > 1) {
            int rx = leftPos + GRID_X + 9 * 18;
            int py = topPos + GRID_Y - 15;
            addRenderableWidget(new FlatButton(rx - 16, py, 16, 12, "▶", Theme.ACCENT, null, b -> button(B_NEXT)).enabled(shownPage < shownPages - 1));
            addRenderableWidget(new FlatButton(rx - 34, py, 16, 12, "◀", Theme.ACCENT, null, b -> button(B_PREV)).enabled(shownPage > 0));
        }
    }

    private int tab(int x, int y, int w, String label, net.minecraft.world.item.Item icon, int id, boolean selected, boolean available) {
        if (!available) return y;
        addRenderableWidget(new FlatButton(x, y, w, 20, label, selected ? Theme.ACCENT : 0xFF4A5266, new ItemStack(icon), b -> {
            if (!selected) button(id);
        }).selected(selected));
        return y + 22;
    }

    private void button(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        if (menu.data(D_KIND) != shownKind || menu.data(D_PAGES) != shownPages || menu.data(D_PAGE) != shownPage
                || menu.data(D_EDIT) != shownEdit) {
            if (shownKind >= 0 && menu.data(D_KIND) != shownKind) Sounds.select();
            rebuild();
        }
    }

    // ───────────────────────────── drawing ─────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        Theme.panel(g, x, y, imageWidth, imageHeight, Theme.BG);

        // Header: who, and how they are doing.
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 28, Theme.PANEL);
        g.fill(x + 1, y + 28, x + imageWidth - 1, y + 29, Theme.BORDER);
        if (menu.targetId != null) Theme.face(g, menu.targetId, x + 7, y + 5, 18);
        g.drawString(font, Component.literal(menu.targetName).withStyle(st -> st.withBold(true)), x + 31, y + 5, Theme.TEXT, false);
        String stats = "§c❤ §f" + menu.data(D_HEALTH) + "§7/" + menu.data(D_MAX_HEALTH) + "  §6☕ §f" + menu.data(D_FOOD)
                + "  §a✦ §f" + menu.data(D_LEVEL) + "  §7⛨ §f" + menu.data(D_ARMOR);
        g.drawString(font, stats, x + 31, y + 16, Theme.TEXT, false);
        String pos = menu.data(D_X) + " " + menu.data(D_Y) + " " + menu.data(D_Z);
        String dim = DIMS[Math.max(0, Math.min(3, menu.data(D_DIM)))];
        int pw = font.width(pos);
        g.drawString(font, "§b⌖ §f" + pos, x + imageWidth - 8 - pw - 10, y + 5, Theme.TEXT, false);
        g.drawString(font, dim, x + imageWidth - 8 - font.width(dim), y + 16, Theme.MUTED, false);
        if (menu.data(D_EDIT) == 0) {
            Theme.pill(g, font, "Solo ver", x + 31 + font.width(menu.targetName) + 12, y + 4, Theme.YELLOW);
        }

        // Page title and content grid.
        int kind = Math.max(0, Math.min(4, menu.data(D_KIND)));
        String title = PAGE_NAMES[kind] + (menu.data(D_PAGES) > 1 ? "  §7" + (menu.data(D_PAGE) + 1) + "/" + menu.data(D_PAGES) : "");
        g.drawString(font, title, x + GRID_X, y + GRID_Y - 13, Theme.MUTED, false);
        Theme.panel(g, x + GRID_X - 3, y + GRID_Y - 3, 9 * 18 + 6, 5 * 18 + 6, Theme.PANEL);
        for (int i = 0; i < 45; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + GRID_X + (i % 9) * 18, sy = y + GRID_Y + (i / 9) * 18;
            ItemStack st = s.getItem();
            if (Ui.isLabel(st)) {
                label(g, st, sx, sy);
            } else if (Ui.isUi(st)) {
                // Filler: no slot here.
            } else {
                well(g, sx, sy, kind == 0 && i >= 36 && i <= 40 ? Theme.withAlpha(Theme.ACCENT, 0.18f) : 0xFF151922);
            }
        }
        boolean empty = true;
        for (int i = 0; i < 45; i++) {
            if (!menu.slots.get(i).getItem().isEmpty() || menu.slots.get(i).isActive()) {
                empty = false;
                break;
            }
        }
        if (empty) g.drawCenteredString(font, "Nada que mostrar aquí", x + GRID_X + 81, y + GRID_Y + 40, Theme.FAINT);

        // Viewer's own inventory.
        g.drawString(font, "Tu inventario", x + 7, y + INV_Y + 1, Theme.MUTED, false);
        Theme.panel(g, x + INV_X - 3, y + INV_Y - 3, 9 * 18 + 6, HOTBAR_Y - INV_Y + 18 + 6, Theme.PANEL);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) well(g, x + INV_X + c * 18, y + INV_Y + r * 18, 0xFF151922);
        for (int c = 0; c < 9; c++) well(g, x + INV_X + c * 18, y + HOTBAR_Y, 0xFF151922);

        // Hint under the tabs.
        List<FormattedCharSequence> hint = font.split(Component.literal(
                menu.data(D_EDIT) == 1 ? "Shift + clic derecho en una mochila: ver lo que lleva" : "Solo puedes mirar. Shift + clic derecho en una mochila: ver dentro"),
                GRID_X - 16);
        int hy = y + INV_Y + 16;
        for (FormattedCharSequence line : hint) {
            g.drawString(font, line, x + 7, hy, Theme.FAINT, false);
            hy += 10;
        }
    }

    private static void well(GuiGraphics g, int x, int y, int inner) {
        g.fill(x, y, x + 18, y + 18, 0xFF0D1016);
        g.fill(x + 1, y + 1, x + 17, y + 17, inner);
    }

    /** A Curios slot type, before the cells of its group: its icon (or a short name) on a colored tile. */
    private void label(GuiGraphics g, ItemStack st, int x, int y) {
        String raw = st.getHoverName().getString();
        boolean cosmetic = raw.endsWith(" cosmetic");
        String id = cosmetic ? raw.substring(0, raw.length() - 9) : raw;
        int color = cosmetic ? Theme.PINK : Theme.ACCENT;
        g.fill(x + 1, y + 1, x + 17, y + 17, Theme.withAlpha(color, 0.30f));
        g.fill(x + 1, y + 16, x + 17, y + 17, color);
        net.minecraft.client.renderer.texture.TextureAtlasSprite icon =
                net.minecraftforge.fml.ModList.get().isLoaded("curios") ? CuriosIcons.icon(id) : null;
        if (icon != null) {
            g.blit(x + 1, y + 1, 0, 16, 16, icon);
            return;
        }
        String name = curioName(id);
        String shortName = name.length() > 3 ? name.substring(0, 3) : name;
        g.pose().pushPose();
        g.pose().translate(x + 9, y + 6, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawCenteredString(font, shortName, 0, 0, Theme.TEXT);
        g.pose().popPose();
    }

    /** Curios' own translation of a slot type ("ring" → "Anillo"), or the id. */
    private static String curioName(String id) {
        String key = "curios.identifier." + id;
        return net.minecraft.client.resources.language.I18n.exists(key) ? net.minecraft.client.resources.language.I18n.get(key) : id;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Everything is drawn in renderBg.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        float t = Theme.ease(openedAt, 160);
        g.pose().pushPose();
        g.pose().translate(0, (1 - t) * 10, 0);
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
        renderTooltip(g, mouseX, mouseY);
        // Tooltip of the Curios labels (they are not real slots).
        for (int i = 0; i < 45; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            int sx = leftPos + GRID_X + (i % 9) * 18, sy = topPos + GRID_Y + (i / 9) * 18;
            if (Ui.isLabel(st) && mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                String raw = st.getHoverName().getString();
                boolean cosmetic = raw.endsWith(" cosmetic");
                String name = curioName(cosmetic ? raw.substring(0, raw.length() - 9) : raw) + (cosmetic ? " (cosmético)" : "");
                g.renderComponentTooltip(font, List.of(Component.literal(name).withStyle(s -> s.withColor(cosmetic ? Theme.PINK : Theme.CYAN)),
                        Component.literal("Sus huecos van a la derecha →").withStyle(s -> s.withColor(Theme.MUTED))), mouseX, mouseY);
            }
        }
    }
}
