package fr.cosmicisland.conseil.client;

import fr.cosmicisland.CosmicIsland;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Affiche en haut a droite le decompte des votes reveles pendant le depouillement (client uniquement). */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ConseilHud {

    private ConseilHud() {}

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("conseil_hud", ConseilHud::render);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        List<String> names = ConseilClient.names;
        List<Integer> counts = ConseilClient.counts;
        if (!ConseilClient.active || mc.options.hideGui || names.isEmpty() || names.size() != counts.size()) {
            return;
        }
        Font font = mc.font;

        // Joueurs tries par nombre de votes decroissant
        List<Integer> order = new ArrayList<>();
        int max = 0;
        for (int i = 0; i < names.size(); i++) {
            order.add(i);
            max = Math.max(max, counts.get(i));
        }
        order.sort((a, b) -> Integer.compare(counts.get(b), counts.get(a)));

        int y = 28;
        String title = "VOTES";
        g.drawString(font, title, w - 8 - font.width(title), y, 0xFFFFAA00, true);
        y += 12;
        for (int idx : order) {
            int count = counts.get(idx);
            String line = names.get(idx) + "  " + count;
            int color = (max > 0 && count == max) ? 0xFFFF5555 : 0xFFFFFFFF;
            g.drawString(font, line, w - 8 - font.width(line), y, color, true);
            y += 10;
        }
    }
}
