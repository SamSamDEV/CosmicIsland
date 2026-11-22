package fr.cosmicisland.survival;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Coquillage de riz cuit : un vrai repas (2 points de faim = 1 gigot). */
public class CookedRiceItem extends Item {
    public CookedRiceItem(Properties properties) {
        super(properties.food(new FoodProperties.Builder().nutrition(2).saturationMod(0.6f).build()));
    }

    @Override
    public Component getName(ItemStack stack) {
        return fr.cosmicisland.Tr.c(getDescriptionId(stack));
    }
}
