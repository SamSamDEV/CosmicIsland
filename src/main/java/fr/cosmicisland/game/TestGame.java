package fr.cosmicisland.game;

import fr.cosmicisland.Txt;
import net.minecraft.server.level.ServerPlayer;

/** Jeu de test : ne fait rien, sert a verifier invitations, decompte, flags et restauration. */
public class TestGame implements KLGame {

    @Override
    public String id() {
        return "test";
    }

    @Override
    public String defaultDisplayName() {
        return "Épreuve de test";
    }

    @Override
    public GameConfig createDefaultConfig() {
        GameConfig config = new GameConfig();
        config.durationSeconds = 30;
        config.reminderSeconds = 10;
        return config;
    }

    @Override
    public void onStart(GameContext ctx) {
        for (ServerPlayer player : ctx.participants()) {
            player.displayClientMessage(Txt.of("&eÉpreuve de test lancée ! Fin automatique dans "
                    + ctx.config().durationSeconds + " secondes."), true);
        }
    }
}
