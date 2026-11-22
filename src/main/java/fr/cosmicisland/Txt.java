package fr.cosmicisland;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Txt {
    private Txt() {}

    /** Texte brut avec les termes du vocabulaire de la saison. */
    public static MutableComponent lit(String text) {
        return Component.literal(Vocab.apply(Lang.tr(text)));
    }

    /** Convertit les codes &c, &l, etc. en codes Minecraft. */
    public static MutableComponent of(String text) {
        return Component.literal(Vocab.apply(Lang.tr(text)).replace('&', '\u00a7'));
    }

    /** Reponse de commande reussie : coche verte. */
    public static MutableComponent ok(String text) {
        return Component.empty()
                .append(Component.literal("\u2714 ").withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD))
                .append(of(text).withStyle(net.minecraft.ChatFormatting.GREEN));
    }

    /** Reponse de commande en echec : croix rouge. */
    public static MutableComponent err(String text) {
        return Component.empty()
                .append(Component.literal("\u2718 ").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD))
                .append(of(text).withStyle(net.minecraft.ChatFormatting.RED));
    }

    /** Information neutre : puce doree. */
    public static MutableComponent info(String text) {
        return Component.empty()
                .append(Component.literal("\u25B8 ").withStyle(net.minecraft.ChatFormatting.GOLD))
                .append(of(text).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
