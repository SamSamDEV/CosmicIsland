package fr.cosmicisland.items;

import fr.cosmicisland.CosmicIsland;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeableArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Bandeau (porte sur la tete, autour du front) et Brassard (porte a la place du plastron, autour du bras).
 * Teintables comme une armure en cuir : 16 couleurs dans l'onglet creatif, et n'importe quel colorant
 * dans une table de craft. Enregistrement automatique (pas besoin de toucher a la classe principale).
 */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BandeauItems {
    /** 16 couleurs vives (flashy) proposees dans l'onglet creatif. */
    public static final int[] FLASHY = {
            0xFFFFFF, 0xFF8A00, 0xFF00D4, 0x00C8FF, 0xFFE600, 0x3CFF00, 0xFF5CA8, 0x8C8C8C,
            0xD0D0D0, 0x00F0E0, 0xA020FF, 0x1E3CFF, 0x9C5A2B, 0x00B83C, 0xFF1A1A, 0x1A1A1A};

    public static Item BANDEAU;
    public static Item BRASSARD;

    private BandeauItems() {}

    /** Materiau sans protection : seul le visuel compte. Texture : textures/models/armor/bandeau_layer_1.png */
    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        public int getDurabilityForType(ArmorItem.Type type) { return 0; }
        public int getDefenseForType(ArmorItem.Type type) { return 0; }
        public int getEnchantmentValue() { return 0; }
        public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
        public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        public String getName() { return CosmicIsland.MODID + ":bandeau"; }
        public float getToughness() { return 0.0f; }
        public float getKnockbackResistance() { return 0.0f; }
    };

    public static class Band extends DyeableArmorItem {
        public Band(ArmorItem.Type type) {
            super(MATERIAL, type, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
        }

        /** Blanc par defaut (au lieu du marron du cuir). */
        @Override
        public int getColor(ItemStack stack) {
            CompoundTag display = stack.getTagElement("display");
            return display != null && display.contains("color", 99) ? display.getInt("color") : 0xFFFFFF;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            BANDEAU = new Band(ArmorItem.Type.HELMET);
            BRASSARD = new Band(ArmorItem.Type.CHESTPLATE);
            helper.register(new ResourceLocation(CosmicIsland.MODID, "headband"), BANDEAU);
            helper.register(new ResourceLocation(CosmicIsland.MODID, "armband"), BRASSARD);
        });
    }

}
