package fr.cosmicisland.camp.client;

import net.minecraft.client.Minecraft;

/** Ouvre les ecrans du feu cote client (appele par le reseau). */
public final class FireScreens {
    private FireScreens() {}

    public static void openIgnite() {
        FireClient.progress = 0f;
        Minecraft.getInstance().setScreen(new FireIgniteScreen());
    }
}
