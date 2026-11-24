package es.jonaykb.kb_moderation.invsee;

import es.jonaykb.kb_moderation.KbModeration;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, KbModeration.MOD_ID);
    public static final RegistryObject<MenuType<InvseeMenu>> INVSEE = MENUS.register("invsee", () -> IForgeMenuType.create(InvseeMenu::client));

    private ModMenus() {
    }
}
