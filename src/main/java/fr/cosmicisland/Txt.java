package fr.cosmicisland;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Txt {
    private Txt() {}

    /** Texte brut avec les termes du vocabulaire de la saison. */
    public static MutableComponent lit(String text) {
        return Component.literal(Vocab.apply(text));
    }

    /** Convertit les codes &c, &l, etc. en codes Minecraft. */
    public static MutableComponent of(String text) {
        return Component.literal(Vocab.apply(text).replace('&', '\u00a7'));
    }
}
