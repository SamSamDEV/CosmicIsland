package fr.cosmicisland.survival.client;

/** Etat de la soif vu par le client (mis a jour par le paquet reseau, lu par le HUD). */
public final class ThirstClient {
    public static boolean active = false;
    public static int level = 20;
    public static int max = 20;

    private ThirstClient() {}

    public static void update(boolean isActive, int newLevel, int newMax) {
        active = isActive;
        level = newLevel;
        max = Math.max(1, newMax);
    }
}
