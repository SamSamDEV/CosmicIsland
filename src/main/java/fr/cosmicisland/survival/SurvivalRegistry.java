package fr.cosmicisland.survival;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.camp.LighterItem;
import fr.cosmicisland.camp.TeamCampfireBlock;
import fr.cosmicisland.camp.TeamCampfireBlockEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Objets et effet de survie : gourde, marmite, riz cru, coquillage de riz cuit, Deshydratation. */
public final class SurvivalRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CosmicIsland.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CosmicIsland.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CosmicIsland.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, CosmicIsland.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, CosmicIsland.MODID);

    public static final RegistryObject<Item> GOURDE = ITEMS.register("canteen", () -> new GourdeItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MARMITE = ITEMS.register("cooking_pot", () -> new MarmiteItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Block> SAC_RIZ_BLOCK = BLOCKS.register("raw_rice_bag", RiceSackBlock::new);
    public static final RegistryObject<Block> MARMITE_BLOCK = BLOCKS.register("placed_pot", MarmiteBlock::new);
    public static final RegistryObject<Block> FEU_BLOCK = BLOCKS.register("team_campfire", TeamCampfireBlock::new);
    public static final RegistryObject<BlockEntityType<TeamCampfireBlockEntity>> FEU_BE = BLOCK_ENTITIES.register("feu_de_camp",
            () -> BlockEntityType.Builder.of(TeamCampfireBlockEntity::new, FEU_BLOCK.get()).build(null));

    public static final RegistryObject<MenuType<fr.cosmicisland.camp.FireMenu>> FIRE_MENU = MENUS.register("feu_de_camp",
            () -> IForgeMenuType.create(fr.cosmicisland.camp.FireMenu::new));

    public static final RegistryObject<Item> SAC_RIZ = ITEMS.register("raw_rice_bag",
            () -> new RawRiceItem(SAC_RIZ_BLOCK.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FEU_ITEM = ITEMS.register("team_campfire",
            () -> new BlockItem(FEU_BLOCK.get(), new Item.Properties()) {
                @Override
                public net.minecraft.network.chat.Component getName(net.minecraft.world.item.ItemStack stack) {
                    return fr.cosmicisland.Tr.c(getDescriptionId(stack));
                }

                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
                                            java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.add(fr.cosmicisland.Tr.c("tooltip.cosmicisland.feu")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });
    public static final RegistryObject<Item> ALLUME_FEU = ITEMS.register("lighter", () -> new LighterItem(new Item.Properties().durability(20)));
    public static final RegistryObject<Item> COQUILLAGE_RIZ = ITEMS.register("rice_shell",
            () -> new CookedRiceItem(new Item.Properties().stacksTo(16).rarity(Rarity.COMMON)));

    public static final RegistryObject<MobEffect> DEHYDRATION = EFFECTS.register("dehydration", DehydrationEffect::new);

    private SurvivalRegistry() {}

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(ThirstNetwork::onSetup);
        modBus.addListener(fr.cosmicisland.camp.FireNetwork::onSetup);
    }
}
