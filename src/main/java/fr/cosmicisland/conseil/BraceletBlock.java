package fr.cosmicisland.conseil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bracelet d'Exemption pose dans le monde : colle sur n'importe quelle face d'un bloc (sol, mur, plafond).
 * Clic droit dessus (avec ou sans objet en main) pour le reprendre. FACING = face du bloc sur laquelle il est pose.
 */
public class BraceletBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape UP = Block.box(5, 0, 5, 11, 2, 11);
    private static final VoxelShape DOWN = Block.box(5, 14, 5, 11, 16, 11);
    private static final VoxelShape SOUTH = Block.box(5, 5, 0, 11, 11, 2);
    private static final VoxelShape NORTH = Block.box(5, 5, 14, 11, 11, 16);
    private static final VoxelShape EAST = Block.box(0, 5, 5, 2, 11, 11);
    private static final VoxelShape WEST = Block.box(14, 5, 5, 16, 11, 11);

    public BraceletBlock() {
        super(BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.METAL).noOcclusion().noCollission());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public net.minecraft.network.chat.MutableComponent getName() {
        return net.minecraft.network.chat.Component.literal(fr.cosmicisland.Vocab.nom("collier"));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> UP;
        };
    }

    /** Il tient tant que le bloc sur lequel il est pose existe. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos support = pos.relative(state.getValue(FACING).getOpposite());
        BlockState s = level.getBlockState(support);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Clic droit : on le reprend dans l'inventaire (quel que soit l'objet tenu). */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            ItemStack stack = new ItemStack(ConseilRegistry.COLLIER.get());
            level.removeBlock(pos, false);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.3f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ConseilRegistry.COLLIER.get());
    }
}
