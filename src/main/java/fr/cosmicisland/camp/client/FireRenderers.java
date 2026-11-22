package fr.cosmicisland.camp.client;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.survival.SurvivalRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Enregistre le rendu des aliments sur le grill du feu de camp. */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FireRenderers {
    private FireRenderers() {}

    @SubscribeEvent
    public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SurvivalRegistry.FEU_BE.get(), TeamCampfireRenderer::new);
    }
}
