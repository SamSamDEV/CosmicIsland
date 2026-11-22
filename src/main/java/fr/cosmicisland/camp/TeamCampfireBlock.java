package fr.cosmicisland.camp;

import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import fr.cosmicisland.conseil.ConseilMessages;
import fr.cosmicisland.game.GameManager;
import fr.cosmicisland.survival.SurvivalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Feu de camp d'equipe : bloc 3D (pierres, bûches, grill, flammes animees, marmite posee dessus).
 * Pose ETEINT, il devient le feu unique de l'equipe a 0 %. S'allume avec l'allume-feu (minijeu), clic droit = interface.
 * L'etat allume (lit) suit la jauge du feu ; la propriete "pot" montre la marmite sur le grill (0 aucune, 1 vide, 2 eau, 3 riz).
 */
public class TeamCampfireBlock extends Block implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final IntegerProperty POT = IntegerProperty.create("pot", 0, 3);
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 9.0, 16.0);

    public TeamCampfireBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.PODZOL)
                .strength(1.5f)
                .sound(SoundType.WOOD)
                .noOcclusion()
                .lightLevel(state -> state.getValue(LIT) ? 15 : 0));
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(POT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, POT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ------------------------------------------------------------ interaction

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LighterItem && !state.getValue(LIT)) {
            FireIgnition.begin(sp, sl, pos, hand);   // minijeu : on maintient le clic droit et on tapote
            return InteractionResult.CONSUME;
        }
        String team = CampfireManager.teamAt(sl, pos);
        boolean staff = KLPermissions.levelOf(sp) >= KLPermissions.ORGANISATEUR;
        if (team != null && !staff && !team.equals(TeamManager.teamIdOf(sp.getUUID()))) {
            ConseilMessages.send(CampfireManager.config().message("notYours"), sp, Map.of());
            return InteractionResult.CONSUME;
        }
        if (GameManager.isInGame(sp.getUUID())) {
            return InteractionResult.CONSUME;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TeamCampfireBlockEntity fire) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new FireMenu(id, inv, fire),
                    fr.cosmicisland.Tr.c("screen.cosmicisland.fire.title")), pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TeamCampfireBlockEntity fire) {
                Containers.dropContents(level, pos, fire.items());
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && state.getValue(LIT) && entity instanceof LivingEntity && !entity.fireImmune()) {
            entity.hurt(level.damageSources().inFire(), 1.0f);
        }
    }

    // ------------------------------------------------------------ ambiance

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (random.nextInt(10) == 0) {
            level.playLocalSound(x, y + 0.5, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5f + random.nextFloat(), random.nextFloat() * 0.7f + 0.6f, false);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + (random.nextDouble() - 0.5) * 0.4, y + 1.2,
                    z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.07, 0.0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.LAVA, x + (random.nextDouble() - 0.5) * 0.4, y + 0.6,
                    z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
        }
    }

    // ------------------------------------------------------------ bloc avec entite

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TeamCampfireBlockEntity(pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SurvivalRegistry.FEU_BE.get()) {
            return null;
        }
        BlockEntityTicker<TeamCampfireBlockEntity> ticker = TeamCampfireBlockEntity::serverTick;
        return (BlockEntityTicker<T>) ticker;
    }
}
