package fr.cosmicisland.games.poteaux.client;

/** Etat du jeu des poteaux vu par le client. Mis a jour par les paquets reseau, lu par le HUD et les touches. */
public final class PoteauxClient {
    public static boolean active = false;
    public static int errors = 0;
    public static int maxErrors = 3;

    /** Direction de la fleche affichee (0 haut, 1 bas, 2 gauche, 3 droite), -1 = aucune. */
    public static int arrowDir = -1;
    public static long arrowStartMs = 0L;
    public static long arrowWindowMs = 1L;

    /** Dernier resultat (1 reussi, 2 rate) et moment ou il est arrive, pour le petit flash a l'ecran. */
    public static int lastResult = 0;
    public static long lastResultMs = 0L;

    private PoteauxClient() {}

    public static void onState(boolean isActive, int newErrors, int newMaxErrors, int result) {
        active = isActive;
        errors = newErrors;
        maxErrors = Math.max(1, newMaxErrors);
        if (!isActive) {
            arrowDir = -1;
        }
        if (result != 0) {
            lastResult = result;
            lastResultMs = System.currentTimeMillis();
            arrowDir = -1;
        }
    }

    public static void onArrow(int direction, int windowMs) {
        if (direction < 0) {
            arrowDir = -1;
            return;
        }
        arrowDir = direction;
        arrowStartMs = System.currentTimeMillis();
        arrowWindowMs = Math.max(1, windowMs);
    }
}
