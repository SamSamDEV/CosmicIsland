package fr.cosmicisland.game;

import com.google.gson.JsonObject;
import fr.cosmicisland.TeamManager;
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
        config.settings.addProperty("awardWins", true); // test : tous les participants gagnent une victoire
        return config;
    }

    @Override
    public void onStart(GameContext ctx) {
        for (ServerPlayer player : ctx.participants()) {
            player.displayClientMessage(Txt.of("&eÉpreuve de test lancée ! Fin automatique dans "
                    + ctx.config().durationSeconds + " secondes."), true);
        }
    }

    @Override
    public GameResult buildResult(GameContext ctx, boolean cancelled) {
        GameResult result = new GameResult();
        result.teamMode = !TeamManager.isMerged();
        boolean award = true;
        JsonObject settings = ctx.config().settings;
        if (settings != null && settings.has("awardWins") && settings.get("awardWins").isJsonPrimitive()) {
            try {
                award = settings.get("awardWins").getAsBoolean();
            } catch (Exception ignored) {
                // valeur invalide : on garde true
            }
        }
        if (!cancelled && award) {
            for (ServerPlayer player : ctx.participants()) {
                result.winners.add(player.getUUID());
                result.placements.add(player.getUUID());
            }
        }
        return result;
    }
}
