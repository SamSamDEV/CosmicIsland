package fr.cosmicisland.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Acces aux informations de l'epreuve en cours, fourni aux jeux. */
public final class GameContext {

    GameContext() {}

    public MinecraftServer server() {
        return GameManager.server();
    }

    public GameConfig config() {
        return GameManager.config();
    }

    /** Participants actuellement connectes. */
    public List<ServerPlayer> participants() {
        return GameManager.onlineParticipants();
    }

    /** Termine normalement l'epreuve (restaure les inventaires et renvoie les joueurs). */
    public void end() {
        GameManager.finish(false);
    }
}
