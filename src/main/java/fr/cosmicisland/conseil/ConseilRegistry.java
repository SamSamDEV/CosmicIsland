package fr.cosmicisland.conseil;

import fr.cosmicisland.CosmicIsland;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Objets du conseil : le collier d'immunite (a cacher ou donner ; les joueurs l'annoncent eux-memes). */
public final class ConseilRegistry {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CosmicIsland.MODID);

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CosmicIsland.MODID);

    /** Le Bracelet d'Exemption pose dans le monde (clic droit sur une face de bloc, clic droit dessus pour le reprendre). */
    public static final RegistryObject<Block> BRACELET_BLOCK = BLOCKS.register("placed_bracelet", BraceletBlock::new);

    public static final RegistryObject<Item> COLLIER = ITEMS.register("exemption_bracelet",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)) {
                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }

                @Override
                public net.minecraft.network.chat.Component getName(ItemStack stack) {
                    return net.minecraft.network.chat.Component.literal(fr.cosmicisland.Vocab.nom("collier"));
                }

                /** Clic droit sur n'importe quelle face d'un bloc : le bracelet se pose, colle a cette face. */
                @Override
                public InteractionResult useOn(UseOnContext ctx) {
                    Level level = ctx.getLevel();
                    Direction face = ctx.getClickedFace();
                    BlockPos target = ctx.getClickedPos().relative(face);
                    BlockState existing = level.getBlockState(target);
                    if (!(existing.isAir() || (existing.canBeReplaced() && existing.getFluidState().isEmpty()))) {
                        return InteractionResult.PASS;
                    }
                    BlockState placed = BRACELET_BLOCK.get().defaultBlockState().setValue(BraceletBlock.FACING, face);
                    if (!placed.canSurvive(level, target)) {
                        return InteractionResult.PASS;
                    }
                    if (!level.isClientSide) {
                        level.setBlock(target, placed, 3);
                        level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 1.3f);
                        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                            ctx.getItemInHand().shrink(1);
                        }
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            });

    private ConseilRegistry() {}

    /** A appeler depuis le constructeur du mod avec le bus d'evenements du mod. */
    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(ConseilNetwork::onSetup);
    }

}
