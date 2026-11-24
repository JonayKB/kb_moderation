package es.jonaykb.kb_moderation.invsee;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Inventory viewer: 45 content slots (+ 9 hidden navigation slots kept for the server logic) and the viewer's own
 * inventory. Pages: inventory + armor + off hand, Curios, ender chest, cosmetic armor, and the contents of any item
 * with an inventory (backpacks…), opened with shift + right click. It reads the target's slots live, so both see each
 * other's changes at once. The client draws it with InvseeScreen; tabs are menu button clicks (see clickMenuButton).
 */
public final class InvseeMenu extends AbstractContainerMenu {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONTENT = 45;
    private static final int TOP = 54;
    /** Shown past the last real slot of a page, so the client does not draw empty slots there. */
    private static final ItemStack FILLER = Ui.filler();

    public enum Kind { MAIN, CURIOS, ENDER, COSMETIC, CONTENTS }

    /** Where the client draws the slots (InvseeScreen uses the same numbers). */
    public static final int GRID_X = 96, GRID_Y = 44, INV_X = 96, INV_Y = 142, HOTBAR_Y = 200;

    /** Synced numbers for the screen (DataSlots are sent as shorts: keep them small). */
    public static final int D_KIND = 0, D_PAGE = 1, D_PAGES = 2, D_EDIT = 3, D_HEALTH = 4, D_MAX_HEALTH = 5, D_FOOD = 6,
            D_LEVEL = 7, D_X = 8, D_Y = 9, D_Z = 10, D_DIM = 11, D_CURIOS = 12, D_COSMETIC = 13, D_ARMOR = 14, D_COUNT = 15;
    public static final int B_MAIN = 0, B_CURIOS = 1, B_ENDER = 2, B_COSMETIC = 3, B_BACK = 4, B_PREV = 5, B_NEXT = 6;

    /** Null on the client. */
    private final ServerPlayer viewer;
    private final ServerPlayer target;
    private final boolean canEdit;
    private final Container dummy = new SimpleContainer(TOP);
    private final ContainerData data;
    /** Client only: who is being looked at. */
    public UUID targetId;
    public String targetName = "";

    private Kind kind;
    private int page;
    private List<Entry> entries = List.of();
    private final ItemStack[] nav = new ItemStack[9];

    /** CONTENTS page: the item handler shown and the page to go back to. */
    private IItemHandler contents;
    /** The cell that holds the open container item, and that item: if it moves, the page closes. */
    private java.util.function.Supplier<ItemStack> contentsCell;
    private ItemStack contentsItem;
    private String contentsName;
    private Kind backKind;
    private int backPage;
    private int ticks;

    public static void open(ServerPlayer viewer, ServerPlayer target, boolean canEdit, Kind start) {
        Component title = Component.literal("Inventario de " + target.getGameProfile().getName());
        NetworkHooks.openScreen(viewer, new SimpleMenuProvider((id, inv, p) -> new InvseeMenu(id, viewer, target, canEdit, start), title),
                buf -> {
                    buf.writeUUID(target.getUUID());
                    buf.writeUtf(target.getGameProfile().getName());
                });
    }

    private InvseeMenu(int id, ServerPlayer viewer, ServerPlayer target, boolean canEdit, Kind start) {
        super(ModMenus.INVSEE.get(), id);
        this.viewer = viewer;
        this.target = target;
        this.canEdit = canEdit;
        this.kind = start;
        this.data = new LiveData();
        for (int i = 0; i < TOP; i++) addSlot(new ViewSlot(i));
        addOwnInventory(viewer.getInventory());
        addDataSlots(data);
        rebuild();
    }

    /** Client side: plain slots filled by the server's sync, at the screen's positions. */
    public static InvseeMenu client(int id, Inventory own, FriendlyByteBuf buf) {
        InvseeMenu m = new InvseeMenu(id, own, new SimpleContainerData(D_COUNT));
        m.targetId = buf.readUUID();
        m.targetName = buf.readUtf();
        return m;
    }

    private InvseeMenu(int id, Inventory own, ContainerData data) {
        super(ModMenus.INVSEE.get(), id);
        this.viewer = null;
        this.target = null;
        this.canEdit = false;
        this.data = data;
        SimpleContainer shown = new SimpleContainer(TOP);
        for (int i = 0; i < TOP; i++) {
            int x = i < CONTENT ? GRID_X + (i % 9) * 18 : -1000;
            int y = i < CONTENT ? GRID_Y + (i / 9) * 18 : -1000;
            addSlot(new ClientSlot(shown, i, x, y));
        }
        addOwnInventory(own);
        addDataSlots(data);
    }

    private void addOwnInventory(Inventory own) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(own, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(own, col, INV_X + col * 18, HOTBAR_Y));
    }

    public int data(int i) {
        return data.get(i);
    }

    /** Live numbers about the target, read by the vanilla container sync every tick. */
    private final class LiveData implements ContainerData {
        @Override
        public int get(int i) {
            return switch (i) {
                case D_KIND -> kind.ordinal();
                case D_PAGE -> page;
                case D_PAGES -> pages();
                case D_EDIT -> canEdit ? 1 : 0;
                case D_HEALTH -> Math.round(target.getHealth());
                case D_MAX_HEALTH -> Math.round(target.getMaxHealth());
                case D_FOOD -> target.getFoodData().getFoodLevel();
                case D_LEVEL -> Math.min(Short.MAX_VALUE, target.experienceLevel);
                case D_X -> clamp(target.getBlockX());
                case D_Y -> clamp(target.getBlockY());
                case D_Z -> clamp(target.getBlockZ());
                case D_DIM -> {
                    var dim = target.level().dimension();
                    yield dim == net.minecraft.world.level.Level.OVERWORLD ? 0 : dim == net.minecraft.world.level.Level.NETHER ? 1
                            : dim == net.minecraft.world.level.Level.END ? 2 : 3;
                }
                case D_CURIOS -> ModList.get().isLoaded("curios") ? 1 : 0;
                case D_COSMETIC -> ModList.get().isLoaded("cosmeticarmorreworked") ? 1 : 0;
                case D_ARMOR -> target.getArmorValue();
                default -> 0;
            };
        }

        private int clamp(int v) {
            return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return D_COUNT;
        }
    }

    /** Client slot: UI items (labels, fillers) cannot be taken; armor slots show their empty icon. */
    private final class ClientSlot extends Slot {
        ClientSlot(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        /** Labels and fillers are drawn by the screen, not as slots. */
        @Override
        public boolean isActive() {
            return index < CONTENT && !Ui.isUi(getItem());
        }

        @Override
        public boolean mayPickup(Player player) {
            return data.get(D_EDIT) == 1 && !Ui.isUi(getItem());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return data.get(D_EDIT) == 1 && index < CONTENT && !Ui.isUi(getItem());
        }

        @Override
        public com.mojang.datafixers.util.Pair<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> getNoItemIcon() {
            int k = data.get(D_KIND);
            int armor;
            if (k == Kind.MAIN.ordinal() && index >= 36 && index <= 40) armor = index - 36;
            else if (k == Kind.COSMETIC.ordinal() && index <= 3) armor = index;
            else return null;
            net.minecraft.resources.ResourceLocation icon = switch (armor) {
                case 0 -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case 1 -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case 2 -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                case 3 -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                default -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            };
            return com.mojang.datafixers.util.Pair.of(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS, icon);
        }
    }

    // ───────────────────────────── pages ─────────────────────────────

    private void rebuild() {
        entries = switch (kind) {
            case MAIN -> mainEntries();
            case CURIOS -> ModList.get().isLoaded("curios") ? CuriosCompat.entries(target) : List.of();
            case ENDER -> handlerEntries(new net.minecraftforge.items.wrapper.InvWrapper(target.getEnderChestInventory()));
            case COSMETIC -> cosmeticEntries();
            case CONTENTS -> contents == null ? List.of() : handlerEntries(contents);
        };
        int pages = pages();
        if (page >= pages) page = pages - 1;
        buildNav();
    }

    private int pages() {
        return Math.max(1, (entries.size() + CONTENT - 1) / CONTENT);
    }

    private List<Entry> mainEntries() {
        Inventory inv = target.getInventory();
        List<Entry> out = new ArrayList<>(CONTENT);
        for (int i = 9; i < 36; i++) out.add(invCell(inv, i, null));
        for (int i = 0; i < 9; i++) out.add(invCell(inv, i, null));
        EquipmentSlot[] armor = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : armor) out.add(invCell(inv, 36 + slot.getIndex(), slot));
        out.add(invCell(inv, 40, null)); // off hand
        out.add(new Entry.Label(Ui.filler()));
        out.add(new Entry.Label(Ui.filler()));
        out.add(new Entry.Label(Ui.filler()));
        out.add(new Entry.Label(Ui.filler()));
        return out;
    }

    private Entry.Cell invCell(Inventory inv, int index, EquipmentSlot armor) {
        return new Entry.Cell(() -> inv.getItem(index), s -> inv.setItem(index, s),
                armor == null ? s -> true : s -> s.canEquip(armor, target), armor == null ? 64 : 1);
    }

    private static List<Entry> handlerEntries(IItemHandler h) {
        List<Entry> out = new ArrayList<>(h.getSlots());
        for (int i = 0; i < h.getSlots(); i++) out.add(Entry.Cell.of(h, i));
        return out;
    }

    private List<Entry> cosmeticEntries() {
        IItemHandler h = cosmeticHandler(target);
        if (h == null) return List.of();
        // Head, chest, legs, feet: the client draws the empty armor icons on cells 0-3.
        List<Entry> out = new ArrayList<>();
        for (int i = Math.min(3, h.getSlots() - 1); i >= 0; i--) out.add(Entry.Cell.of(h, i));
        return out;
    }

    private static Method cosArmorGet;

    /** Cosmetic Armor Reworked's slots, through its API by reflection (it is optional). */
    private static IItemHandler cosmeticHandler(ServerPlayer target) {
        if (!ModList.get().isLoaded("cosmeticarmorreworked")) return null;
        try {
            if (cosArmorGet == null) {
                cosArmorGet = Class.forName("lain.mods.cos.api.CosArmorAPI").getMethod("getCAStacks", java.util.UUID.class);
            }
            return (IItemHandler) cosArmorGet.invoke(null, target.getUUID());
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            LOGGER.warn("Could not read Cosmetic Armor slots: {}", e.toString());
            return null;
        }
    }

    private void buildNav() {
        ItemStack filler = Ui.filler();
        java.util.Arrays.fill(nav, filler);
        int pages = pages();
        if (page > 0) nav[0] = Ui.icon(Items.ARROW, "&e← Página anterior", "&7Página " + page + " de " + pages);
        if (page < pages - 1) nav[8] = Ui.icon(Items.ARROW, "&ePágina siguiente →", "&7Página " + (page + 2) + " de " + pages);

        nav[1] = tab(Kind.MAIN, Ui.icon(Items.CHEST, "&6Inventario", "&7Inventario, armadura y mano secundaria"));
        if (ModList.get().isLoaded("curios")) nav[2] = tab(Kind.CURIOS, Ui.icon(Items.AMETHYST_SHARD, "&bCurios", "&7Anillos, collares, cinturones…"));
        nav[3] = tab(Kind.ENDER, Ui.icon(Items.ENDER_CHEST, "&5Cofre de Ender"));
        if (ModList.get().isLoaded("cosmeticarmorreworked")) nav[4] = tab(Kind.COSMETIC, Ui.icon(Items.LEATHER_CHESTPLATE, "&dArmadura cosmética"));
        if (kind == Kind.CONTENTS) {
            nav[5] = Ui.icon(Items.OAK_DOOR, "&aVolver", "&7Contenido de: &f" + contentsName);
        } else {
            nav[5] = Ui.icon(Items.BOOK, "&7Ayuda", "&7Shift + clic derecho en una mochila", "&7u objeto con inventario: ver dentro",
                    canEdit ? "&aPuedes editar" : "&cSolo puedes mirar");
        }
        nav[7] = info();
    }

    private ItemStack tab(Kind k, ItemStack icon) {
        return kind == k ? Ui.selected(icon) : icon;
    }

    private ItemStack info() {
        String dim = target.level().dimension().location().toString();
        GameType mode = target.gameMode.getGameModeForPlayer();
        ItemStack head = Ui.icon(Items.PLAYER_HEAD, "&e" + target.getGameProfile().getName(),
                "&c❤ &f" + Math.round(target.getHealth()) + "&7/" + Math.round(target.getMaxHealth()),
                "&6🍖 &f" + target.getFoodData().getFoodLevel() + "&7/20",
                "&a✦ &7Nivel &f" + target.experienceLevel,
                "&b⌖ &f" + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ(),
                "&7" + dim,
                "&7Modo: &f" + mode.getName());
        head.getOrCreateTag().putString("SkullOwner", target.getGameProfile().getName());
        return head;
    }

    private void switchTo(Kind k) {
        if (kind == k && k != Kind.CONTENTS) return;
        kind = k;
        page = 0;
        rebuild();
        click();
    }

    private void click() {
        viewer.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4f, 1.0f);
    }

    // ───────────────────────────── clicks ─────────────────────────────

    private Entry entryAt(int slot) {
        int i = page * CONTENT + slot;
        return i < entries.size() ? entries.get(i) : null;
    }

    private boolean editable(Entry e) {
        return canEdit && e instanceof Entry.Cell c && c.set() != null;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (target == null) {
            super.clicked(slotId, button, type, player);
            return;
        }
        if (slotId >= CONTENT && slotId < TOP) {
            navClick(slotId - CONTENT);
            sendAllDataToRemote();
            return;
        }
        if (slotId >= 0 && slotId < CONTENT) {
            Entry e = entryAt(slotId);
            if (type == ClickType.QUICK_MOVE && button == 1 && e instanceof Entry.Cell c) {
                ItemStack stack = c.get().get();
                // Backpacks and other items with an inventory, and shulker boxes (which keep theirs in NBT).
                IItemHandler h = stack.isEmpty() ? null : stack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
                if (h == null && !stack.isEmpty()) h = ShulkerHandler.of(stack);
                if (h != null) {
                    if (kind != Kind.CONTENTS) {
                        backKind = kind;
                        backPage = page;
                    }
                    contents = h;
                    contentsCell = c.get();
                    contentsItem = stack;
                    contentsName = stack.getHoverName().getString();
                    switchTo(Kind.CONTENTS);
                    sendAllDataToRemote();
                    return;
                }
            }
            if (!editable(e)) {
                sendAllDataToRemote();
                return;
            }
        }
        if (!canEdit && slotId >= TOP && type == ClickType.QUICK_MOVE) {
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    private void navClick(int i) {
        int pages = pages();
        switch (i) {
            case 0 -> {
                if (page > 0) {
                    page--;
                    buildNav();
                    click();
                }
            }
            case 8 -> {
                if (page < pages - 1) {
                    page++;
                    buildNav();
                    click();
                }
            }
            case 1 -> switchTo(Kind.MAIN);
            case 2 -> {
                if (ModList.get().isLoaded("curios")) switchTo(Kind.CURIOS);
            }
            case 3 -> switchTo(Kind.ENDER);
            case 4 -> {
                if (ModList.get().isLoaded("cosmeticarmorreworked")) switchTo(Kind.COSMETIC);
            }
            case 5 -> {
                if (kind == Kind.CONTENTS) {
                    contents = null;
                    contentsCell = null;
                    contentsItem = null;
                    kind = backKind == null ? Kind.MAIN : backKind;
                    rebuild();
                    page = Math.min(backPage, pages() - 1);
                    buildNav();
                    click();
                }
            }
            default -> {
            }
        }
    }

    /** Tabs and page buttons of the screen (vanilla "container button click" packet). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (target == null) return false;
        int nav = switch (id) {
            case B_MAIN -> 1;
            case B_CURIOS -> 2;
            case B_ENDER -> 3;
            case B_COSMETIC -> 4;
            case B_BACK -> 5;
            case B_PREV -> 0;
            case B_NEXT -> 8;
            default -> -1;
        };
        if (nav < 0) return false;
        navClick(nav);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (target == null) return ItemStack.EMPTY; // the client waits for the server's answer
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        if (index < CONTENT) {
            // Target → viewer's own inventory.
            ItemStack moving = slot.getItem().copy();
            if (!moveItemStackTo(moving, TOP, slots.size(), true)) return ItemStack.EMPTY;
            slot.set(moving);
            return ItemStack.EMPTY;
        }
        if (index >= TOP && canEdit) {
            // Viewer → the open page: first onto equal stacks, then into empty slots.
            ItemStack moving = slot.getItem().copy();
            for (int pass = 0; pass < 2 && !moving.isEmpty(); pass++) {
                for (int i = 0; i < CONTENT && !moving.isEmpty(); i++) {
                    Slot to = slots.get(i);
                    if (!to.mayPlace(moving)) continue;
                    boolean empty = !to.hasItem();
                    if ((pass == 0) == empty) continue;
                    if (!empty && !ItemStack.isSameItemSameTags(to.getItem(), moving)) continue;
                    moving = to.safeInsert(moving);
                }
            }
            slot.set(moving);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        if (target == null) return slot.index >= TOP || (slot.index < CONTENT && slot.mayPickup(null));
        return slot.index >= TOP || (slot.index < CONTENT && editable(entryAt(slot.index)));
    }

    @Override
    public boolean canDragTo(Slot slot) {
        if (target == null) return slot.index >= TOP || (slot.index < CONTENT && slot.mayPlace(ItemStack.EMPTY));
        return slot.index >= TOP || (slot.index < CONTENT && editable(entryAt(slot.index)));
    }

    @Override
    public boolean stillValid(Player player) {
        if (target == null) return true;
        return !target.isRemoved() && target.connection != null && target.connection.isAcceptingMessages();
    }

    @Override
    public void broadcastChanges() {
        if (target == null) {
            super.broadcastChanges();
            return;
        }
        // The open backpack/shulker was moved or taken (by the player or here): go back, never write to a stale item.
        if (kind == Kind.CONTENTS && contentsCell != null && contentsCell.get() != contentsItem) navClick(5);
        // Curios slots can be added or removed and the info changes: refresh once a second.
        if (++ticks % 20 == 0) {
            if (kind != Kind.CONTENTS) rebuild();
            else nav[7] = info();
        }
        super.broadcastChanges();
    }

    /** One of the 54 top slots: content (45) or navigation (9). It reads and writes the target live. */
    private final class ViewSlot extends Slot {
        ViewSlot(int index) {
            super(dummy, index, 0, 0);
        }

        private Entry entry() {
            return index < CONTENT ? entryAt(index) : null;
        }

        @Override
        public ItemStack getItem() {
            if (index >= CONTENT) return nav[index - CONTENT];
            Entry e = entry();
            return e == null ? FILLER : e.shown();
        }

        @Override
        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            if (entry() instanceof Entry.Cell c && c.set() != null) {
                c.set().accept(stack);
                setChanged();
            }
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            set(stack);
        }

        @Override
        public ItemStack remove(int amount) {
            if (!(entry() instanceof Entry.Cell c) || c.set() == null) return ItemStack.EMPTY;
            ItemStack current = c.get().get();
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack rest = current.copy();
            ItemStack taken = rest.split(amount);
            c.set().accept(rest);
            return taken;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Entry e = entry();
            return editable(e) && ((Entry.Cell) e).valid().test(stack) && !isOpenContainer(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return editable(entry());
        }

        @Override
        public int getMaxStackSize() {
            return entry() instanceof Entry.Cell c ? c.max() : 0;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(getMaxStackSize(), stack.getMaxStackSize());
        }

        @Override
        public void setChanged() {
            target.getInventory().setChanged();
        }
    }

    /** Never put a backpack inside the contents page that shows that same backpack. */
    private boolean isOpenContainer(ItemStack stack) {
        if (kind != Kind.CONTENTS || contents == null || stack.isEmpty()) return false;
        IItemHandler h = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        return h != null && h == contents;
    }
}
