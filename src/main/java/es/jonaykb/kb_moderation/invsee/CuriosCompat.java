package es.jonaykb.kb_moderation.invsee;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Only loaded when Curios is installed. */
final class CuriosCompat {
    private CuriosCompat() {
    }

    /** For each slot type: a label, then its slots, then its cosmetic slots if it has them. */
    static List<Entry> entries(ServerPlayer target) {
        List<Entry> out = new ArrayList<>();
        ICuriosItemHandler inv = CuriosApi.getCuriosInventory(target).resolve().orElse(null);
        if (inv == null) return out;
        for (Map.Entry<String, ICurioStacksHandler> e : inv.getCurios().entrySet()) {
            String id = e.getKey();
            ICurioStacksHandler h = e.getValue();
            if (h.getSlots() <= 0) continue;
            newRowIfNeeded(out, 1 + h.getSlots());
            out.add(new Entry.Label(Ui.label(id)));
            add(out, target, id, h.getStacks(), false);
            if (h.hasCosmetic()) {
                newRowIfNeeded(out, 1 + h.getCosmeticStacks().getSlots());
                out.add(new Entry.Label(Ui.label(id + " cosmetic")));
                add(out, target, id, h.getCosmeticStacks(), true);
            }
        }
        return out;
    }

    /** A group (label + its slots) starts on a new row when it does not fit in what is left of this one. */
    private static void newRowIfNeeded(List<Entry> out, int size) {
        int col = out.size() % 9;
        if (col == 0 || col + size <= 9) return;
        while (out.size() % 9 != 0) out.add(new Entry.Label(Ui.filler()));
    }

    private static void add(List<Entry> out, ServerPlayer target, String id, IDynamicStackHandler stacks, boolean cosmetic) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            int slot = i;
            SlotContext ctx = new SlotContext(id, target, slot, cosmetic, true);
            out.add(new Entry.Cell(() -> stacks.getStackInSlot(slot), s -> stacks.setStackInSlot(slot, s),
                    (ItemStack s) -> CuriosApi.isStackValid(ctx, s), stacks.getSlotLimit(slot)));
        }
    }
}
