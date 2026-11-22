package fr.cosmicisland.survival;

import fr.cosmicisland.Tr;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Sac de riz ouvert pose au sol : on voit le niveau de riz. Clic droit avec une marmite d'eau pour la remplir de riz. */
public class RiceSackBlock extends Block {
    public static final int MAX_LEVEL = 4;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, MAX_LEVEL);
    private static final VoxelShape SHAPE = Block.box(1.5, 0.0, 1.5, 14.5, 15.0, 14.5);

    public RiceSackBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5f).sound(SoundType.WOOL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(LEVEL, MAX_LEVEL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Un sac vide ne rend plus de sac plein quand on le casse. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return state.getValue(LEVEL) <= 0 ? List.of() : super.getDrops(state, builder);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        int lvl = state.getValue(LEVEL);
        boolean infinite = ThirstManager.config().riceSackInfinite;
        if (held.getItem() instanceof MarmiteItem) {
            int st = MarmiteItem.state(held);
            if (st == MarmiteItem.RICE) {
                msg(level, player, "message.cosmicisland.sack.pot_has_rice");
            } else if (st != MarmiteItem.WATER) {
                msg(level, player, "message.cosmicisland.sack.need_water");
            } else if (lvl <= 0 && !infinite) {
                msg(level, player, "message.cosmicisland.sack.empty");
            } else {
                if (!level.isClientSide) {
                    MarmiteItem.setState(held, MarmiteItem.RICE);
                    if (!infinite) {
                        level.setBlock(pos, state.setValue(LEVEL, lvl - 1), 3);
                    }
                    level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
                    msg(level, player, "message.cosmicisland.sack.filled");
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.is(SurvivalRegistry.SAC_RIZ.get()) && lvl < MAX_LEVEL) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(LEVEL, MAX_LEVEL), 3);
                level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static void msg(Level level, Player player, String key) {
        if (!level.isClientSide) {
            player.displayClientMessage(Tr.c(key), true);
        }
    }
}
