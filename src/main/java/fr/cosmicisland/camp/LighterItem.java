package fr.cosmicisland.camp;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Allume-feu : maintenir le clic droit sur un feu de camp eteint, puis tapoter la touche (Espace par defaut)
 * pour remplir la jauge d'allumage (voir TeamCampfireBlock et FireIgnition).
 */
public class LighterItem extends Item {
    public LighterItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(fr.cosmicisland.Tr.c("tooltip.cosmicisland.allume_feu").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public Component getName(ItemStack stack) {
        return fr.cosmicisland.Tr.c(getDescriptionId(stack));
    }
}
