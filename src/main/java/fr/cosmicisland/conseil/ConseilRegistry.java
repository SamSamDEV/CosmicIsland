package fr.cosmicisland.conseil;

import fr.cosmicisland.CosmicIsland;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Objets du conseil : le collier d'immunite (a cacher ou donner ; les joueurs l'annoncent eux-memes). */
public final class ConseilRegistry {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CosmicIsland.MODID);

    public static final RegistryObject<Item> COLLIER = ITEMS.register("collier",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)) {
                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }
            });

    private ConseilRegistry() {}

    /** A appeler depuis le constructeur du mod avec le bus d'evenements du mod. */
    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ConseilRegistry::onCreativeTab);
        modBus.addListener(ConseilNetwork::onSetup);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(COLLIER);
        }
    }
}
