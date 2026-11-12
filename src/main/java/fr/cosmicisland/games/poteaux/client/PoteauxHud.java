package fr.cosmicisland.games.poteaux.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.cosmicisland.CosmicIsland;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Affichage a l'ecran pendant l'epreuve des poteaux (cote client uniquement). */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PoteauxHud {

    private PoteauxHud() {}

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("poteaux_hud", PoteauxHud::render);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!PoteauxClient.active || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        long now = System.currentTimeMillis();
        int cx = w / 2;
        int cy = h / 2 - 25;

        if (PoteauxClient.arrowDir >= 0) {
            drawArrow(g, cx, cy, 5, PoteauxClient.arrowDir, 0xFFFFFFFF);

            long elapsed = now - PoteauxClient.arrowStartMs;
            long window = PoteauxClient.arrowWindowMs;
            float remaining = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) elapsed / window));
            int barW = 120;
            int barX = cx - barW / 2;
            int barY = cy + 38;
            g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 7, 0xAA000000);
            int color = remaining > 0.5f ? 0xFF55FF55 : remaining > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
            g.fill(barX, barY, barX + (int) (barW * remaining), barY + 6, color);
            String time = String.format("%.2f s", Math.max(0L, window - elapsed) / 1000.0);
            g.drawCenteredString(font, time, cx, barY + 10, 0xFFFFFFFF);
        } else if (PoteauxClient.lastResult != 0 && now - PoteauxClient.lastResultMs < 500L) {
            boolean ok = PoteauxClient.lastResult == 1;
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(cx, cy, 0);
            pose.scale(3.0f, 3.0f, 1.0f);
            g.drawCenteredString(font, ok ? "BIEN !" : "RATÉ !", 0, -4, ok ? 0xFF55FF55 : 0xFFFF5555);
            pose.popPose();
        }

        drawBalance(g, font, cx, cy + 70);
    }

    /** Jauge d'equilibre : un segment par erreur autorisee. */
    private static void drawBalance(GuiGraphics g, Font font, int cx, int y0) {
        int segments = Math.max(1, PoteauxClient.maxErrors);
        int segW = Math.min(30, 150 / segments);
        int gap = 3;
        int totalW = segments * segW + (segments - 1) * gap;
        int x0 = cx - totalW / 2;
        int remaining = Math.max(0, segments - PoteauxClient.errors);

        g.drawCenteredString(font, "Équilibre", cx, y0 - 12, 0xFFFFFFFF);
        for (int i = 0; i < segments; i++) {
            int x = x0 + i * (segW + gap);
            int color;
            if (i >= remaining) {
                color = 0xFF444444;
            } else if (remaining * 3 > segments * 2) {
                color = 0xFF55FF55;
            } else if (remaining * 3 > segments) {
                color = 0xFFFFAA00;
            } else {
                color = 0xFFFF5555;
            }
            g.fill(x - 1, y0 - 1, x + segW + 1, y0 + 9, 0xAA000000);
            g.fill(x, y0, x + segW, y0 + 8, color);
        }
    }

    /** Fleche dessinee avec des rectangles : 0 haut, 1 bas, 2 gauche, 3 droite. u = taille d'une case en pixels. */
    private static void drawArrow(GuiGraphics g, int cx, int cy, int u, int dir, int color) {
        for (int k = 0; k < 6; k++) {
            int hw = k + 1;
            rect(g, cx, cy, u, dir, -hw, -6 + k, hw, -5 + k, color);
        }
        rect(g, cx, cy, u, dir, -2, 0, 2, 5, color);
    }

    private static void rect(GuiGraphics g, int cx, int cy, int u, int dir,
                             int x0, int y0, int x1, int y1, int color) {
        int ax = 0;
        int ay = 0;
        int bx = 0;
        int by = 0;
        switch (dir) {
            case 0 -> { ax = x0; ay = y0; bx = x1; by = y1; }
            case 1 -> { ax = x0; ay = -y0; bx = x1; by = -y1; }
            case 2 -> { ax = y0; ay = x0; bx = y1; by = x1; }
            default -> { ax = -y0; ay = x0; bx = -y1; by = x1; }
        }
        g.fill(cx + Math.min(ax, bx) * u, cy + Math.min(ay, by) * u,
                cx + Math.max(ax, bx) * u, cy + Math.max(ay, by) * u, color);
    }
}
