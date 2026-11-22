package fr.cosmicisland.games.poteaux;

import fr.cosmicisland.CosmicIsland;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Blocs et items du jeu des poteaux (cosmicisland:poteau). */
public final class PoteauxRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CosmicIsland.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CosmicIsland.MODID);

    public static final RegistryObject<Block> POTEAU = BLOCKS.register("pole",
            () -> new PoteauBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Item> POTEAU_ITEM = ITEMS.register("pole",
            () -> new BlockItem(POTEAU.get(), new Item.Properties()));

    private PoteauxRegistry() {}

    /** A appeler depuis le constructeur du mod avec le bus d'evenements du mod. */
    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(PoteauxNetwork::onSetup);
    }

}
