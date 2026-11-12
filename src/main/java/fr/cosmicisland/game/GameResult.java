package fr.cosmicisland.game;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Resultat d'une epreuve, fourni par le jeu au moteur (qui enregistre les victoires et les logs). */
public final class GameResult {
    /** Vrai si l'epreuve s'est jouee par equipes (equipes non fusionnees). */
    public boolean teamMode = false;

    /** Equipe victorieuse (mode equipes), sinon null. */
    public String winnerTeam = null;

    /** Joueurs qui gagnent une victoire (en mode equipes : tous les membres de l'equipe gagnante ayant participe). */
    public final List<UUID> winners = new ArrayList<>();

    /** Classement complet du meilleur au moins bon (optionnel). */
    public final List<UUID> placements = new ArrayList<>();

    /** Details propres au jeu, copies tels quels dans le log. */
    public final JsonObject details = new JsonObject();
}
