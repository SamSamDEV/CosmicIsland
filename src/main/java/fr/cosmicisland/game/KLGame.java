package fr.cosmicisland.game;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * API d'un jeu Cosmic Island. Un jeu (integre ou venant d'un addon) implemente cette interface
 * puis s'enregistre avec GameRegistry.register(new MonJeu()).
 */
public interface KLGame {

    /** Identifiant unique en minuscules (ex. "poteaux"), utilise dans les commandes et le fichier de config. */
    String id();

    /** Nom affiche par defaut (modifiable ensuite dans le fichier de config du jeu). */
    String defaultDisplayName();

    /** Config creee automatiquement la premiere fois (permet de definir des valeurs par defaut propres au jeu). */
    default GameConfig createDefaultConfig() {
        return new GameConfig();
    }

    /** Appele au GO, juste apres le decompte. */
    default void onStart(GameContext ctx) {}

    /** Appele a chaque tick serveur pendant que le jeu tourne. */
    default void onTick(GameContext ctx) {}

    /** Appele a la fin du jeu (cancelled = true si arrete par /ccis stop). */
    default void onStop(GameContext ctx, boolean cancelled) {}

    /** Appele par /ccis go avant le decompte. Retourne un message d'erreur pour refuser le depart, ou null. */
    default String canStart(GameContext ctx) {
        return null;
    }

    /** Commandes propres au jeu, ajoutees sous /ccis (ex. Commands.literal("poteaux")...). null = aucune. */
    default LiteralArgumentBuilder<CommandSourceStack> commands() {
        return null;
    }

    /**
     * Appele par le moteur a la fin d'une epreuve qui a demarre (avant onStop). Le jeu retourne son resultat
     * (vainqueurs, classement, details) pour enregistrer victoires et logs. null = aucun resultat.
     */
    default GameResult buildResult(GameContext ctx, boolean cancelled) {
        return null;
    }
}
