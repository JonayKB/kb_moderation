package es.jonaykb.kb_moderation.invsee;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** One of the 45 content slots of the viewer: either a real slot of the target or a display-only label. */
sealed interface Entry {
    ItemStack shown();

    record Label(ItemStack icon) implements Entry {
        @Override
        public ItemStack shown() {
            return icon;
        }
    }

    /** A real slot. {@code set} == null means read-only (e.g. an item handler that cannot be set directly). */
    record Cell(Supplier<ItemStack> get, Consumer<ItemStack> set, Predicate<ItemStack> valid, int max) implements Entry {
        @Override
        public ItemStack shown() {
            return get.get();
        }

        static Cell of(IItemHandler h, int slot) {
            Consumer<ItemStack> set = h instanceof IItemHandlerModifiable m ? s -> m.setStackInSlot(slot, s) : null;
            return new Cell(() -> h.getStackInSlot(slot), set, s -> h.isItemValid(slot, s), h.getSlotLimit(slot));
        }
    }
}
