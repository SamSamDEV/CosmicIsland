package fr.cosmicisland.admin.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cosmicisland.CosmicIsland;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Etat cote client de la fenetre d'administration. */
public final class AdminClient {
    public static JsonObject state = new JsonObject();
    public static Component feedback = Component.empty();
    public static long feedbackAt = 0;

    private AdminClient() {}

    /** Appele a la reception de l'etat du serveur (ouvre la fenetre si demande). */
    public static void onState(String json, boolean open) {
        try {
            state = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            CosmicIsland.LOGGER.error("Etat d'administration illisible", e);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (open && !(mc.screen instanceof AdminScreen)) {
            mc.setScreen(new AdminScreen());
        }
    }

    public static void setFeedback(Component text) {
        feedback = text;
        feedbackAt = System.currentTimeMillis();
    }
}
