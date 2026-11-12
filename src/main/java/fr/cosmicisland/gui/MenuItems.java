package fr.cosmicisland.gui;

import fr.cosmicisland.Txt;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/** Objets pour les menus : noms et descriptions avec codes couleur &, tetes de joueurs. */
public final class MenuItems {

    private MenuItems() {}

    private static MutableComponent plain(String text) {
        return Txt.of(text).withStyle(style -> style.withItalic(false));
    }

    public static ItemStack named(Item item, String name, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(plain(name));
        setLore(stack, lore);
        return stack;
    }

    /** Tete du joueur (la texture est chargee par le client a partir du pseudo). */
    public static ItemStack head(String playerName, String displayName, List<String> lore) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.getOrCreateTag().putString("SkullOwner", playerName);
        stack.setHoverName(plain(displayName));
        setLore(stack, lore);
        return stack;
    }

    /** Effet de brillance (sans afficher l'enchantement). */
    public static void glow(ItemStack stack) {
        stack.enchant(Enchantments.UNBREAKING, 1);
        stack.getOrCreateTag().putInt("HideFlags", 1);
    }

    private static void setLore(ItemStack stack, List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (String line : lore) {
            list.add(StringTag.valueOf(Component.Serializer.toJson(plain(line))));
        }
        stack.getOrCreateTagElement("display").put("Lore", list);
    }
}
