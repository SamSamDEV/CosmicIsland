package fr.cosmicisland.survival;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Sac de riz cru : se pose au sol (accroupi + clic droit) et se mange par desespoir (peu de faim, mais il assoiffe).
 * A cuire dans la marmite, sur le feu de camp.
 */
public class RawRiceItem extends BlockItem {
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand, ItemStack stack) {
                return net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }

    public RawRiceItem(Block block, Properties properties) {
        super(block, properties.food(new FoodProperties.Builder().nutrition(1).saturationMod(0.1f).alwaysEat().build()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return super.useOn(context);   // accroupi : on pose le sac
        }
        return InteractionResult.PASS;     // sinon : clic droit = manger
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            ThirstManager.drink(player, ThirstManager.config().rawRiceHydration, 0f, 0, 0);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(fr.cosmicisland.Tr.c("tooltip.cosmicisland.sac_riz").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public Component getName(ItemStack stack) {
        return fr.cosmicisland.Tr.c(getDescriptionId(stack));
    }
}
