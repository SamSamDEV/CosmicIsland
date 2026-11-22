package fr.cosmicisland.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/** Gourde : 10 gorgees, eau non purifiee (a remplir dans l'eau) ou purifiee (bouillie sur le feu de camp). */
public class GourdeItem extends Item {
    public static final String SIPS = "Sips";
    public static final String PURIFIED = "Purified";

    public GourdeItem(Properties properties) {
        super(properties);
    }

    public static int sips(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(SIPS);
    }

    public static boolean purified(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(PURIFIED) && sips(stack) > 0;
    }

    public static void setSips(ItemStack stack, int sips, boolean purified) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(SIPS, Math.max(0, sips));
        tag.putBoolean(PURIFIED, purified && sips > 0);
    }

    public static int maxSips() {
        return Math.max(1, ThirstManager.config().gourdeSips);
    }

    /** 0 = vide, 1 = eau non purifiee, 2 = eau purifiee (pour le modele de l'objet). */
    public static float state(ItemStack stack) {
        if (sips(stack) <= 0) {
            return 0f;
        }
        return purified(stack) ? 2f : 1f;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Regarde de l'eau : on remplit
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (level.getFluidState(pos).is(FluidTags.WATER) && level.mayInteract(player, pos)) {
                if (sips(stack) >= maxSips() && !purified(stack)) {
                    return InteractionResultHolder.pass(stack);
                }
                if (!level.isClientSide) {
                    setSips(stack, maxSips(), false);
                    level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        if (sips(stack) > 0 && ThirstManager.canDrinkNow(player)) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && sips(stack) > 0) {
            ThirstConfig c = ThirstManager.config();
            if (purified(stack)) {
                ThirstManager.drink(player, c.gourdePurifiedHydration, c.gourdePurifiedSaturation, 0, 0);
            } else {
                ThirstManager.drink(player, c.gourdeHydration, c.gourdeSaturation, c.gourdeEffectChance, c.gourdeEffectSeconds);
            }
            boolean wasPurified = purified(stack);
            setSips(stack, sips(stack) - 1, wasPurified);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return sips(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * sips(stack) / maxSips());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return purified(stack) ? 0x55E6FF : 0x3F76E4;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(fr.cosmicisland.Tr.c("tooltip.cosmicisland.gourde.sips", sips(stack), maxSips()).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (sips(stack) > 0) {
            tooltip.add(fr.cosmicisland.Tr.c(purified(stack) ? "tooltip.cosmicisland.gourde.purified" : "tooltip.cosmicisland.gourde.dirty")
                    .withStyle(purified(stack) ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GOLD));
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return fr.cosmicisland.Tr.c(getDescriptionId(stack));
    }
}
