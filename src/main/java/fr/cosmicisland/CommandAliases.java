package fr.cosmicisland;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Les commandes n'existent qu'en anglais (/ccis council, /ccis merge...), quelle que soit la langue choisie.
 * Cette classe est conservee pour les appels existants : elle ne traduit plus rien.
 */
public final class CommandAliases {
    private CommandAliases() {}

    /** Les commandes sont identiques dans toutes les langues. */
    public static String cmd(String line) {
        return line;
    }

    public static void install(CommandDispatcher<CommandSourceStack> dispatcher) {
        // plus d'alias : rien a installer
    }

    /** Renvoie l'arbre de commandes a tous les joueurs. */
    public static void refresh(net.minecraft.server.MinecraftServer server) {
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(p);
        }
    }
}
