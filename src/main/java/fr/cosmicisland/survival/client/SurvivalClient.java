package fr.cosmicisland.survival.client;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.survival.GourdeItem;
import fr.cosmicisland.survival.MarmiteItem;
import fr.cosmicisland.survival.SurvivalRegistry;
import fr.cosmicisland.survival.ThirstNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Cote client : apparence des objets selon leur etat, et demande de boire dans l'eau. */
public final class SurvivalClient {
    private SurvivalClient() {}

    @Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                net.minecraft.client.gui.screens.MenuScreens.register(SurvivalRegistry.FIRE_MENU.get(),
                        fr.cosmicisland.camp.client.FireScreen::new);
                ResourceLocation state = new ResourceLocation(CosmicIsland.MODID, "state");
                ItemProperties.register(SurvivalRegistry.GOURDE.get(), new ResourceLocation(CosmicIsland.MODID, "drinking"),
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0f : 0.0f);
                ItemProperties.register(SurvivalRegistry.GOURDE.get(), state, (stack, level, entity, seed) -> GourdeItem.state(stack));
                ItemProperties.register(SurvivalRegistry.MARMITE.get(), state, (stack, level, entity, seed) -> MarmiteItem.state(stack));
            });
        }
    }

    @Mod.EventBusSubscriber(modid = CosmicIsland.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (!event.isUseItem() || mc.player == null || mc.level == null || !mc.player.getMainHandItem().isEmpty()) {
                return;
            }
            HitResult hit = mc.player.pick(4.5, 1.0f, true);
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
                    && mc.level.getFluidState(bhr.getBlockPos()).is(FluidTags.WATER)) {
                ThirstNetwork.CHANNEL.sendToServer(new ThirstNetwork.DrinkPacket());
            }
        }
    }
}
