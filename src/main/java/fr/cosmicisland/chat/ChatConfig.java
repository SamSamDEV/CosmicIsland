package fr.cosmicisland.chat;

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
}
