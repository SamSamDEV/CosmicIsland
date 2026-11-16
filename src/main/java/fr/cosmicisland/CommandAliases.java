package fr.cosmicisland;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Commandes dans la langue choisie : les noms anglais sont la reference ; en francais, des alias francais
 * (redirections) sont actifs a la place des noms anglais. Le changement est immediat (l'arbre de commandes
 * est renvoye aux joueurs a chaque changement de langue).
 */
public final class CommandAliases {
    private CommandAliases() {}

    /** {parent, anglais, francais} ; parent "ccis" = racine. */
    private static final String[][] MAP = {
            {"ccis", "council", "conseil"}, {"ccis", "bracelet", "collier"}, {"ccis", "merge", "fusion"},
            {"ccis", "reply", "orga"}, {"ccis", "giveup", "abandon"}, {"ccis", "season", "saison"},
            {"ccis", "history", "historique"}, {"ccis", "language", "langue"},
            {"council", "immunity", "immunite"}, {"season", "new", "nouvelle"},
            {"bracelet", "yes", "oui"}, {"bracelet", "no", "non"},
            {"ranking", "player", "joueur"}, {"ranking", "game", "jeu"}, {"spawn", "game", "jeu"},
    };

    /** Traduit une ligne de commande "/ccis ..." anglaise vers le francais (identique en anglais). */
    public static String cmd(String line) {
        if (!"fr".equals(Lang.language())) {
            return line;
        }
        String[] w = line.split(" ", -1);
        int i = 0;
        if (w.length > 0 && w[0].startsWith("/")) {
            i = 1;
        }
        String parent = "ccis";
        if (i < w.length && w[i].equals("ccis")) {
            i++;
        }
        while (i < w.length) {
            String next = null;
            for (String[] m : MAP) {
                if (m[0].equals(parent) && m[1].equals(w[i])) {
                    next = m[2];
                    break;
                }
            }
            parent = w[i];
            if (next != null) {
                w[i] = next;
            }
            i++;
            if (parent.startsWith("<") || parent.startsWith("[")) {
                break;
            }
        }
        return String.join(" ", w);
    }

    private static boolean fr() {
        return "fr".equals(Lang.language());
    }

    public static void install(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("ccis");
        if (root != null) {
            walk(root, "ccis");
        }
    }

    @SuppressWarnings("unchecked")
    private static void walk(CommandNode<CommandSourceStack> node, String name) {
        List<CommandNode<CommandSourceStack>> children = new ArrayList<>(node.getChildren());
        for (CommandNode<CommandSourceStack> child : children) {
            if (child instanceof LiteralCommandNode<CommandSourceStack> lit) {
                for (String[] m : MAP) {
                    if (m[0].equals(name) && m[1].equals(lit.getLiteral())) {
                        Predicate<CommandSourceStack> orig = lit.getRequirement();
                        LiteralArgumentBuilder<CommandSourceStack> alias = LiteralArgumentBuilder
                                .<CommandSourceStack>literal(m[2])
                                .requires(s -> fr() && orig.test(s))
                                .redirect(lit);
                        if (lit.getCommand() != null) {
                            alias.executes(lit.getCommand());
                        }
                        node.addChild(alias.build());
                        gate(lit, orig);
                    }
                }
            }
            walk(child, child instanceof LiteralCommandNode<CommandSourceStack> l ? l.getLiteral() : name);
        }
    }

    /** Le nom anglais n'est utilisable qu'en anglais. */
    private static void gate(CommandNode<CommandSourceStack> node, Predicate<CommandSourceStack> orig) {
        try {
            Field f = CommandNode.class.getDeclaredField("requirement");
            f.setAccessible(true);
            Predicate<CommandSourceStack> gated = s -> !fr() && orig.test(s);
            f.set(node, gated);
        } catch (Exception e) {
            CosmicIsland.LOGGER.warn("Commandes francaises : impossible de masquer le nom anglais ({})", e.toString());
        }
    }

    /** Renvoie l'arbre de commandes a tous les joueurs (apres un changement de langue). */
    public static void refresh(net.minecraft.server.MinecraftServer server) {
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(p);
        }
    }
}
