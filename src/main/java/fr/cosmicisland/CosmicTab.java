package fr.cosmicisland;

import fr.cosmicisland.conseil.ConseilRegistry;
import fr.cosmicisland.conseil.TotemRegistry;
import fr.cosmicisland.games.poteaux.PoteauxRegistry;
import fr.cosmicisland.items.BandeauItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Onglet creatif « Cosmic Island » : tous les objets et blocs du mod au meme endroit. */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CosmicTab {
    private CosmicTab() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(
                new ResourceLocation(CosmicIsland.MODID, "cosmicisland"),
                CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.cosmicisland"))
                        .icon(() -> new ItemStack(TotemRegistry.TOTEM_ITEM != null
                                ? TotemRegistry.TOTEM_ITEM : ConseilRegistry.COLLIER.get()))
                        .displayItems((params, output) -> {
                            output.accept(PoteauxRegistry.POTEAU_ITEM.get());
                            output.accept(ConseilRegistry.COLLIER.get());
                            if (TotemRegistry.TOTEM_ITEM != null) {
                                output.accept(TotemRegistry.TOTEM_ITEM);
                            }
                            for (Item band : new Item[] {BandeauItems.BANDEAU, BandeauItems.BRASSARD}) {
                                if (band == null) {
                                    continue;
                                }
                                for (int color : BandeauItems.FLASHY) {
                                    ItemStack stack = new ItemStack(band);
                                    stack.getOrCreateTagElement("display").putInt("color", color);
                                    output.accept(stack);
                                }
                            }
                        })
                        .build()));
    }
}
