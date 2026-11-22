package fr.cosmicisland.survival.client;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.survival.SurvivalRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Rangee de gouttes d'eau au-dessus de la barre de faim. */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ThirstHud {
    private static final ResourceLocation ICONS = new ResourceLocation(CosmicIsland.MODID, "textures/gui/thirst.png");

    private ThirstHud() {}

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "thirst", ThirstHud::render);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!ThirstClient.active || mc.options.hideGui || mc.player == null || mc.gameMode == null
                || !mc.gameMode.canHurtPlayer()) {
            return;
        }
        int y = h - gui.rightHeight;
        gui.rightHeight += 10;
        int level = ThirstClient.level;
        int perIcon = Math.max(1, ThirstClient.max / 10);
        boolean shake = level <= 4 && mc.player.tickCount % 6 < 3;
        // Teinte verdatre pendant la Deshydratation (eau sale)
        boolean sick = mc.player.hasEffect(SurvivalRegistry.DEHYDRATION.get());
        int v = sick ? 9 : 0;
        // Comme la barre de faim : ancree a droite, elle se vide de gauche a droite
        for (int i = 0; i < 10; i++) {
            int x = w / 2 + 91 - i * 8 - 9;
            int iy = shake && i % 2 == 0 ? y + 1 : y;
            int slot = (i + 1) * perIcon;
            int u;
            if (level >= slot) {
                u = 18;                 // pleine
            } else if (level > slot - perIcon && level >= slot - perIcon / 2) {
                u = 9;                  // moitie
            } else {
                u = 0;                  // vide
            }
            g.blit(ICONS, x, iy, u, v, 9, 9, 27, 18);
        }
    }
}
