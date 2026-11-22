package fr.cosmicisland.conseil;

import fr.cosmicisland.CosmicIsland;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Totem d'immunite : bloc 3D que l'on peut poser et recuperer a la main. Donne au vainqueur d'une epreuve solo.
 * Se declare tout seul (aucune modification de la classe principale necessaire).
 */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TotemRegistry {
    public static Block TOTEM_BLOCK;
    public static Item TOTEM_ITEM;

    private TotemRegistry() {}

    public static class TotemBlock extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

        @Override
        public net.minecraft.network.chat.MutableComponent getName() {
            return net.minecraft.network.chat.Component.literal(fr.cosmicisland.Vocab.nom("totem"));
        }

        public TotemBlock() {
            super(BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.WOOD).noOcclusion());
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            TOTEM_BLOCK = new TotemBlock();
            helper.register(new ResourceLocation(CosmicIsland.MODID, "immunity_globe"), TOTEM_BLOCK);
        });
        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            if (TOTEM_BLOCK == null) {
                TOTEM_BLOCK = new TotemBlock();
            }
            TOTEM_ITEM = new BlockItem(TOTEM_BLOCK, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }

                @Override
                public net.minecraft.network.chat.Component getName(ItemStack stack) {
                    return net.minecraft.network.chat.Component.literal(fr.cosmicisland.Vocab.nom("totem"));
                }
            };
            helper.register(new ResourceLocation(CosmicIsland.MODID, "immunity_globe"), TOTEM_ITEM);
        });
    }

}
