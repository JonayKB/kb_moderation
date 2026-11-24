package es.jonaykb.kb_moderation.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import top.theillusivec4.curios.api.CuriosApi;

/** Only loaded when Curios is installed: the empty-slot icon each Curios slot type has. */
final class CuriosIcons {
    private CuriosIcons() {
    }

    static TextureAtlasSprite icon(String id) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        ResourceLocation icon = CuriosApi.getSlot(id, level).map(t -> t.getIcon()).orElse(null);
        if (icon == null) return null;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(icon);
        // A missing icon resolves to the purple/black "missing" sprite: better show the name then.
        return sprite.contents().name().getPath().equals("missingno") ? null : sprite;
    }
}
