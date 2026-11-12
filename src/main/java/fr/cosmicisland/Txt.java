package fr.cosmicisland;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Txt {
    private Txt() {}

    /** Convertit les codes &c, &l, etc. en codes Minecraft. */
    public static MutableComponent of(String text) {
        return Component.literal(text.replace('&', '\u00a7'));
    }
}
