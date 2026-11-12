package fr.cosmicisland.conseil.client;

import java.util.ArrayList;
import java.util.List;

/** Decompte des votes vu par le client. Mis a jour par le paquet reseau, lu par le HUD. */
public final class ConseilClient {
    public static boolean active = false;
    public static List<String> names = new ArrayList<>();
    public static List<Integer> counts = new ArrayList<>();

    private ConseilClient() {}

    public static void onTally(boolean isActive, List<String> newNames, List<Integer> newCounts) {
        active = isActive;
        names = new ArrayList<>(newNames);
        counts = new ArrayList<>(newCounts);
    }
}
