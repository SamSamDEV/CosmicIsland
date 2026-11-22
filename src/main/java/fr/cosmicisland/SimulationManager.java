package fr.cosmicisland;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * /ccis season simulate run [joueurs] [equipes] [fusion] : joue une saison complete avec de faux participants (Test1, Test2...)
 * en utilisant les VRAIES commandes (tirage, fusion, finale...), puis verifie les regles : jury = seulement les joueurs de l'equipe
 * de fusion elimines apres la fusion, troisieme finaliste exclu, jury impair, un vote par juge.
 */
public final class SimulationManager {
    private SimulationManager() {}

    private static String t(String key, Object... args) {
        return Tr.s(key, args);
    }

    private static void cmd(CommandSourceStack silent, String line) {
        silent.getServer().getCommands().performPrefixedCommand(silent, line);
    }

    private static boolean fakeName(String name) {
        return name != null && name.matches("Test[0-9]+");
    }

    private static void say(CommandSourceStack src, String text) {
        src.sendSystemMessage(Txt.of(text));
    }

    private static void check(CommandSourceStack src, boolean ok, String key, Object... args) {
        say(src, (ok ? "&a✔ " : "&c✘ ") + t(key, args));
    }

    /** Elimination "comme au conseil" : spectateur, jury si dans l'equipe de fusion, alertes de la saison. */
    private static void eliminate(MinecraftServer server, UUID id, String name) {
        RoleManager.set(new GameProfile(id, name), KLRole.SPECTATEUR);
        SeasonHistory.log("elimination", name + " est éliminé (simulation).");
        FinaleManager.addJuror(id, name);
        SeasonManager.onElimination(server);
    }

    private static Map.Entry<UUID, String> randomAlive(Random rnd) {
        List<Map.Entry<UUID, String>> alive = new ArrayList<>(RoleManager.participants().entrySet());
        return alive.get(rnd.nextInt(alive.size()));
    }

    // ------------------------------------------------------------ nettoyage

    private static void cleanup(CommandSourceStack silent) {
        cmd(silent, "ccis finale simulate stop");
        if (TeamManager.isMerged()) {
            TeamManager.unmerge();
        }
        RoleManager.clearFakes();
        cmd(silent, "ccis season reset confirm");
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        cleanup(ctx.getSource().withSuppressedOutput());
        say(ctx.getSource(), t("sim.cleaned"));
        return 1;
    }

    // ------------------------------------------------------------ simulation

    private static int run(CommandContext<CommandSourceStack> ctx, int players, int teams, int mergeAt) {
        CommandSourceStack src = ctx.getSource();
        CommandSourceStack silent = src.withSuppressedOutput();
        MinecraftServer server = src.getServer();
        if (mergeAt < 4 || mergeAt >= players || players < teams) {
            src.sendFailure(Txt.err(t("sim.err.params", players, teams, mergeAt)));
            return 0;
        }
        for (String name : RoleManager.participants().values()) {
            if (!fakeName(name)) {
                src.sendFailure(Txt.err(t("sim.err.real", name, name)));
                return 0;
            }
        }
        Random rnd = new Random();
        say(src, t("sim.start", players, teams, mergeAt, mergeAt - 3));

        cleanup(silent);
        cmd(silent, "ccis season fake " + players);
        cmd(silent, "ccis season setup " + teams + " " + mergeAt);
        cmd(silent, "ccis team draw all");
        cmd(silent, "ccis season start");
        check(src, RoleManager.participants().size() == players, "sim.chk.players", RoleManager.participants().size(), players);

        // avant la fusion
        Set<String> beforeMerge = new HashSet<>();
        while (RoleManager.participants().size() > mergeAt) {
            Map.Entry<UUID, String> e = randomAlive(rnd);
            beforeMerge.add(e.getValue());
            eliminate(server, e.getKey(), e.getValue());
        }
        cmd(silent, "ccis team merge Fusion");
        check(src, TeamManager.isMerged() && SeasonManager.phase() == SeasonManager.Phase.POST_MERGE, "sim.chk.merge", mergeAt);

        // apres la fusion jusqu'aux 3 derniers
        Set<String> expectedJury = new HashSet<>();
        while (RoleManager.participants().size() > 3) {
            Map.Entry<UUID, String> e = randomAlive(rnd);
            expectedJury.add(e.getValue());
            eliminate(server, e.getKey(), e.getValue());
        }

        // finale
        List<String> last = new ArrayList<>(RoleManager.participants().values());
        cmd(silent, "ccis finale start");
        String winner = last.get(rnd.nextInt(3));
        cmd(silent, "ccis finale winner " + winner);
        List<String> others = new ArrayList<>(last);
        others.remove(winner);
        String opponent = others.get(rnd.nextInt(2));
        String excluded = others.get(0).equals(opponent) ? others.get(1) : others.get(0);
        cmd(silent, "ccis finale pick " + opponent);
        cmd(silent, "ccis finale vote start");
        int filled = FinaleManager.fillMissingVotes();

        // controles
        Set<String> jury = new HashSet<>(FinaleManager.juryMap().values());
        List<String> finalists = FinaleManager.finalistNames();
        say(src, t("sim.finale", winner, opponent, excluded));
        check(src, jury.equals(expectedJury), "sim.chk.jury", jury.size(), expectedJury.size());
        Set<String> leaked = new HashSet<>(jury);
        leaked.retainAll(beforeMerge);
        check(src, leaked.isEmpty(), "sim.chk.premerge", beforeMerge.size());
        check(src, !jury.contains(excluded), "sim.chk.excluded", excluded);
        check(src, finalists.size() == 2 && finalists.contains(winner) && finalists.contains(opponent), "sim.chk.finalists");
        check(src, jury.size() % 2 == 1, "sim.chk.odd", jury.size());
        check(src, FinaleManager.voteCount() == jury.size() && filled == jury.size(), "sim.chk.votes", FinaleManager.voteCount(), jury.size());
        check(src, jury.size() == mergeAt - 3, "sim.chk.size", jury.size(), mergeAt - 3);

        cmd(silent, "ccis finale reveal");
        say(src, t("sim.reveal"));
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("simulate")
                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                .executes(ctx -> {
                    say(ctx.getSource(), t("sim.help"));
                    return 1;
                })
                .then(Commands.literal("stop").executes(SimulationManager::stop))
                .then(Commands.literal("run")
                        .executes(ctx -> run(ctx, 12, 2, 10))
                        .then(Commands.argument("players", IntegerArgumentType.integer(5, 40))
                                .then(Commands.argument("teams", IntegerArgumentType.integer(2, 8))
                                        .then(Commands.argument("mergeat", IntegerArgumentType.integer(4, 40))
                                                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "players"),
                                                        IntegerArgumentType.getInteger(ctx, "teams"),
                                                        IntegerArgumentType.getInteger(ctx, "mergeat")))))));
    }
}
