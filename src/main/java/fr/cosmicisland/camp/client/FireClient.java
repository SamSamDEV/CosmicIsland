package fr.cosmicisland.camp.client;

/** Etat de l'allumage vu par le client (mis a jour par le reseau, lu par le HUD et l'animation). */
public final class FireClient {
    /** Progression 0..1 envoyee par le serveur ; -1 = aucune. */
    public static volatile float progress = -1f;
    /** Le joueur maintient l'allume-feu sur un feu. */
    public static volatile boolean using = false;
    /** Dernier cote frotte (0 gauche, 1 droite), -1 = aucun. */
    public static int lastSide = -1;
    /** Position de la main : -1 (gauche) a +1 (droite), lissee. */
    public static float swing = 0f, swingPrev = 0f, swingTarget = 0f;
    /** Rythme des derniers coups : 2 bon, 1 moyen, 0 rate (le plus recent en dernier). */
    public static final java.util.ArrayDeque<Integer> history = new java.util.ArrayDeque<>();
    public static long lastStrokeTick = -100;

    private FireClient() {}
}
