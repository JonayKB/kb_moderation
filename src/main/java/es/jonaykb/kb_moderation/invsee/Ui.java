package es.jonaykb.kb_moderation.invsee;

import es.jonaykb.kb_moderation.util.Text;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Display-only items for the viewer's menus. They are never put in a real inventory. */
public final class Ui {
    private Ui() {
    }

    static ItemStack icon(Item item, String name, String... lore) {
        ItemStack s = new ItemStack(item);
        // Not italic: legacy() starts from an empty style, the explicit "&r" keeps it that way.
        s.setHoverName(Text.legacy("&r" + name).withStyle(st -> st.withItalic(false)));
        if (lore.length > 0) {
            ListTag list = new ListTag();
            for (String line : lore) {
                Component c = Text.legacy("&r" + line).withStyle(st -> st.withItalic(false));
                list.add(StringTag.valueOf(Component.Serializer.toJson(c)));
            }
            s.getOrCreateTagElement("display").put("Lore", list);
        }
        // Marks the stack as UI so it never merges with a real item of the same kind.
        s.getOrCreateTag().putBoolean("kb_moderation_ui", true);
        return s;
    }

    /** Display-only items made by {@link #icon}: never real items. */
    public static boolean isUi(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean("kb_moderation_ui");
    }

    /** A label (light blue pane) shown as a named tile by the screen; anything else UI is an empty filler. */
    public static boolean isLabel(ItemStack s) {
        return isUi(s) && s.is(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
    }

    static ItemStack filler() {
        return icon(Items.GRAY_STAINED_GLASS_PANE, " ");
    }

    static ItemStack selected(ItemStack s) {
        CompoundTag tag = s.getOrCreateTag();
        ListTag ench = new ListTag();
        CompoundTag e = new CompoundTag();
        e.putString("id", "minecraft:unbreaking");
        e.putShort("lvl", (short) 1);
        ench.add(e);
        tag.put("Enchantments", ench);
        tag.putInt("HideFlags", 1);
        return s;
    }

    static ItemStack label(String name, String... lore) {
        return icon(Items.LIGHT_BLUE_STAINED_GLASS_PANE, name, lore);
    }
}
