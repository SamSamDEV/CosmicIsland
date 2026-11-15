package fr.cosmicisland.items.client;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.items.BandeauItems;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Teinte l'icone de l'objet selon sa couleur (comme le cuir teint). */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BandeauColors {
    private BandeauColors() {}

    @SubscribeEvent
    public static void onColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 && stack.getItem() instanceof DyeableLeatherItem dye
                        ? 0xFF000000 | dye.getColor(stack) : 0xFFFFFFFF,
                BandeauItems.BANDEAU, BandeauItems.BRASSARD);
    }
}
