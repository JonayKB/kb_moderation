package es.jonaykb.kb_moderation.invsee;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * The 27 slots inside a shulker box item (its BlockEntityTag.Items), so the viewer can open it like a backpack.
 * Every change is written back to the item at once.
 */
final class ShulkerHandler implements IItemHandlerModifiable {
    private static final int SIZE = 27;
    private final ItemStack box;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    private ShulkerHandler(ItemStack box) {
        this.box = box;
        CompoundTag tag = BlockItem.getBlockEntityData(box);
        if (tag != null && tag.contains("Items", 9)) ContainerHelper.loadAllItems(tag, items);
    }

    static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    static ShulkerHandler of(ItemStack stack) {
        return stack.getCount() == 1 && isShulker(stack) ? new ShulkerHandler(stack) : null;
    }

    private void save() {
        CompoundTag tag = box.getOrCreateTagElement("BlockEntityTag");
        tag.remove("Items");
        ContainerHelper.saveAllItems(tag, items, false);
        if (tag.isEmpty()) box.removeTagKey("BlockEntityTag");
    }

    @Override
    public int getSlots() {
        return SIZE;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return items.get(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        items.set(slot, stack);
        save();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        ItemStack current = items.get(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!current.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(current, stack)) return stack;
            limit -= current.getCount();
        }
        if (limit <= 0) return stack;
        int moved = Math.min(limit, stack.getCount());
        if (!simulate) {
            if (current.isEmpty()) items.set(slot, stack.copyWithCount(moved));
            else current.grow(moved);
            save();
        }
        return stack.getCount() == moved ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack current = items.get(slot);
        if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int n = Math.min(amount, current.getCount());
        ItemStack out = current.copyWithCount(n);
        if (!simulate) {
            current.shrink(n);
            if (current.isEmpty()) items.set(slot, ItemStack.EMPTY);
            save();
        }
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    /** Same rule as a placed shulker box: no shulkers inside, nor items that refuse to go in containers. */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !isShulker(stack) && stack.getItem().canFitInsideContainerItems();
    }
}
