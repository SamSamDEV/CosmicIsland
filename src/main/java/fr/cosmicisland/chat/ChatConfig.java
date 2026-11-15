package fr.cosmicisland.chat;

import fr.cosmicisland.game.GameConfig;

import java.util.ArrayList;
import java.util.List;

/** Reglages du chat : config/cosmicisland/chat.json */
public class ChatConfig {
    /** Chat separe par equipe : un joueur n'est lu que par son equipe, le staff et les spectateurs. */
    public boolean teamChat = true;

    /** Les organisateurs et admins lisent tous les chats. */
    public boolean staffReadsAll = true;

    /** Les spectateurs lisent tous les chats. */
    public boolean spectatorsReadAll = true;

    /** Les messages d'un spectateur ne vont qu'aux spectateurs et au staff (pas aux joueurs encore en jeu). */
    public boolean spectatorChat = true;

    public String spectatorPrefix = "&8[SPEC] ";

    /** Commandes vanilla interdites aux joueurs (le staff n'est pas concerne). Sans le "/". */
    public List<String> blockedCommands = new ArrayList<>(List.of("msg", "tell", "w", "me"));

    public String blockedMessage = "&cCette commande est désactivée pendant l'aventure.";

    /** Annonce de la fusion des equipes : titre a l'ecran et message dans le chat, pour tous les joueurs. */
    public GameConfig.Msg fusionTitle = title("&6&l{V_le_reunification}", "&7Les équipes ne forment plus qu'une seule tribu",
            "minecraft:ui.toast.challenge_complete");
    public GameConfig.Msg fusionChat = new GameConfig.Msg(
            "&6&l{V_reunification} &7: toutes les équipes sont réunies. Désormais, c'est &fchacun pour soi&7 !", "chat");

    private static GameConfig.Msg title(String text, String subtitle, String sound) {
        GameConfig.Msg msg = new GameConfig.Msg(text, "title");
        msg.subtitle = subtitle;
        msg.sound = sound;
        msg.stay = 80;
        return msg;
    }
}
