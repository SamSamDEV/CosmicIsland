package fr.cosmicisland.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/** Marmite : vide, remplie d'eau (clic droit sur une source) ou remplie de riz (en cuisson dans le feu de camp). */
public class MarmiteItem extends Item {
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand, ItemStack stack) {
                return net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }

    public static final String STATE = "State";
    public static final int EMPTY = 0;
    public static final int WATER = 1;
    public static final int RICE = 2;

    public MarmiteItem(Properties properties) {
        super(properties);
    }

    public static int state(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? EMPTY : tag.getInt(STATE);
    }

    public static void setState(ItemStack stack, int state) {
        stack.getOrCreateTag().putInt(STATE, state);
    }

    /** Clic droit sur un bloc : pose la marmite au sol (elle garde son contenu). */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        net.minecraft.world.item.context.BlockPlaceContext place = new net.minecraft.world.item.context.BlockPlaceContext(context);
        BlockPos pos = place.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        net.minecraft.world.level.block.state.BlockState st = SurvivalRegistry.MARMITE_BLOCK.get().defaultBlockState()
                .setValue(MarmiteBlock.STATE, Math.max(0, Math.min(2, state(stack))));
        if (!place.canPlace() || !st.canSurvive(level, pos) || !level.getFluidState(pos).isEmpty()) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, st, 3);
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (state(stack) == EMPTY && level.getFluidState(pos).is(FluidTags.WATER) && level.mayInteract(player, pos)) {
                if (!level.isClientSide) {
                    setState(stack, WATER);
                    level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        String key = switch (state(stack)) {
            case WATER -> "tooltip.cosmicisland.marmite.water";
            case RICE -> "tooltip.cosmicisland.marmite.rice";
            default -> "tooltip.cosmicisland.marmite.empty";
        };
        tooltip.add(fr.cosmicisland.Tr.c(key).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public Component getName(ItemStack stack) {
        return fr.cosmicisland.Tr.c(getDescriptionId(stack));
    }
}
