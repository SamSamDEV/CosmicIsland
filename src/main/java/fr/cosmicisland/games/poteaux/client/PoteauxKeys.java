package fr.cosmicisland.games.poteaux.client;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.games.poteaux.PoteauxNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Touches fleches (haut, bas, gauche, droite) pressees pendant qu'une fleche est affichee. */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID, value = Dist.CLIENT)
public final class PoteauxKeys {

    private PoteauxKeys() {}

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (!PoteauxClient.active || PoteauxClient.arrowDir < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            return;
        }
        int dir = switch (event.getKey()) {
            case GLFW.GLFW_KEY_UP -> 0;
            case GLFW.GLFW_KEY_DOWN -> 1;
            case GLFW.GLFW_KEY_LEFT -> 2;
            case GLFW.GLFW_KEY_RIGHT -> 3;
            default -> -1;
        };
        if (dir < 0) {
            return;
        }
        // La fleche est consommee : une seule reponse par fleche
        PoteauxClient.arrowDir = -1;
        PoteauxNetwork.CHANNEL.sendToServer(new PoteauxNetwork.KeyPacket(dir));
    }
}
