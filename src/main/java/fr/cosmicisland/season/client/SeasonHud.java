package fr.cosmicisland.season.client;

import fr.cosmicisland.CosmicIsland;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Remplace le numero de niveau d'XP par "Jour X de l'aventure" au-dessus de la barre. */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, value = Dist.CLIENT)
public final class SeasonHud {
    private static int savedLevel = -1;

    private SeasonHud() {}

    private static boolean isXp(RenderGuiOverlayEvent e) {
        return e.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type();
    }

    /** Niveau a 0 pendant le dessin de la barre : le numero vanilla n'apparait pas. */
    @SubscribeEvent
    public static void onPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (isXp(event) && mc.player != null && SeasonClient.day > 0) {
            savedLevel = mc.player.experienceLevel;
            mc.player.experienceLevel = 0;
        }
    }

    @SubscribeEvent
    public static void onPost(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!isXp(event) || mc.player == null || savedLevel < 0) {
            return;
        }
        mc.player.experienceLevel = savedLevel;
        savedLevel = -1;
        Component text = fr.cosmicisland.Tr.c("hud.cosmicisland.day", SeasonClient.day);
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        final int barW = 81;          // largeur exacte de la ligne de coeurs (10 coeurs)
        int rawW = Math.max(1, mc.font.width(text));
        float scale = Math.min(0.85f, (barW - 6) / (float) rawW);   // le texte rentre toujours dans la barre
        int x = w / 2 - 91;           // bord gauche des coeurs
        int y = h - 49;               // rangee au-dessus des coeurs
        var g = event.getGuiGraphics();
        g.fill(x, y, x + barW, y + 9, 0xB0000000);
        g.fill(x, y + 9, x + barW, y + 10, 0xFFFFD966);
        float th = 8 * scale;
        g.pose().pushPose();
        g.pose().translate(x + (barW - rawW * scale) / 2f, y + (9 - th) / 2f + 0.5f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(mc.font, text, 0, 0, 0xFFFFFF, true);
        g.pose().popPose();
    }
}
