package es.jonaykb.kb_moderation.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/** The marker added to vanished players' names for staff: name tag and tab list. */
public final class VanishMark {
    private static final TextColor CYAN = TextColor.fromRgb(0x4FD1E8);

    private VanishMark() {
    }

    public static Component mark(Component name) {
        return Component.literal("◎ ").withStyle(st -> st.withColor(CYAN).withBold(false)).append(name);
    }
}
