package fr.cosmicisland;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.cosmicisland.game.GameMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deroulement de la saison : phase (avant fusion / apres fusion / finale / terminee), jour courant
 * (reglable a la main ou suivant les jours du monde, compatible avec un mod qui allonge les jours),
 * etape de l'episode (vie au camp, recompense, immunite, conseil) et tableau de bord organisateur.
 * Fichier : config/cosmicisland/season.json
 */
public final class SeasonManager {

    public enum Phase {
        PRE_MERGE, POST_MERGE, FINALE, ENDED;

        public String label() {
            return Tr.s("season.phase." + name().toLowerCase());
        }
    }

    public enum Step {
        CAMP, REWARD, IMMUNITY, COUNCIL;

        public String label() {
            return Tr.s("season.step." + name().toLowerCase());
        }
    }

    public static class Data {
        public String phase = Phase.PRE_MERGE.name();
        public String step = Step.CAMP.name();
        public int day = 1;
        /** Le jour avance tout seul a chaque lever de soleil du monde (le mod qui allonge les jours reste compatible). */
        public boolean autoDay = false;
        /** Dernier jour du monde constate (pour detecter le passage au jour suivant). */
        public long lastWorldDay = -1;
        /** Nombre de joueurs au depart (fixe au lancement de la saison). */
        public int startPlayers = 0;
        /** Nombre de joueurs restants a partir duquel on previent le staff qu'il faut fusionner (0 = aucune alerte). */
        public int mergeAt = 0;
        /** Nombre de joueurs restants a partir duquel on previent le staff que c'est la finale (0 = aucune alerte). */
        public int finaleAt = 0;
        public boolean mergeAlerted = false;
        public boolean finaleAlerted = false;
        /** Saison en pause : le jour automatique ne bouge plus. */
        public boolean paused = false;
        /** Jury : joueurs elimines au conseil apres la fusion (uuid -> pseudo). Ils ne votent qu'a la fin de la finale. */
        public java.util.Map<String, String> jury = new java.util.LinkedHashMap<>();
        /** Etape de la finale : NONE, POTEAUX, CHOICE, READY, VOTING, REVEAL, DONE. */
        public String finaleStage = "NONE";
        /** Gagnant de l'epreuve des poteaux (uuid), qui choisit son adversaire. */
        public String potWinner = "";
        /** Les deux finalistes (uuid -> pseudo). */
        public java.util.Map<String, String> finalists = new java.util.LinkedHashMap<>();
        /** Votes secrets du jury : uuid du juré -> uuid du finaliste. */
        public java.util.Map<String, String> votes = new java.util.LinkedHashMap<>();
    }

    private static Data data = new Data();

    private SeasonManager() {}

    private static String t(String key, Object... args) {
        return Tr.s(key, args);
    }

    public static void load() {
        data = KLFiles.load("season.json", Data.class, Data::new);
    }

    static void save() {
        KLFiles.save("season.json", data);
        syncAll();
    }

    /** Envoie le jour a tous les joueurs connectes. */
    public static void syncAll() {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            fr.cosmicisland.season.SeasonNetwork.sendAll(server.getPlayerList().getPlayers(), data.day);
        }
    }

    public static void sync(ServerPlayer player) {
        fr.cosmicisland.season.SeasonNetwork.send(player, data.day);
    }

    public static Data data() {
        return data;
    }

    public static Phase phase() {
        try {
            return Phase.valueOf(data.phase);
        } catch (IllegalArgumentException e) {
            return Phase.PRE_MERGE;
        }
    }

    public static Step step() {
        try {
            return Step.valueOf(data.step);
        } catch (IllegalArgumentException e) {
            return Step.CAMP;
        }
    }

    public static int day() {
        return data.day;
    }

    // ------------------------------------------------------------ comptage

    /** Joueurs encore en jeu (role participant). */
    /** Etat de la saison pour la fenetre d'administration. */
    public static com.google.gson.JsonObject snapshot() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("day", data.day);
        o.addProperty("phase", phase().label());
        o.addProperty("phaseId", data.phase);
        o.addProperty("step", step().label());
        o.addProperty("auto", data.autoDay);
        o.addProperty("paused", data.paused);
        o.addProperty("alive", alive());
        o.addProperty("start", data.startPlayers);
        o.addProperty("mergeAt", data.mergeAt);
        o.addProperty("finaleAt", data.finaleAt);
        return o;
    }

    public static int alive() {
        return RoleManager.participants().size();
    }

    private static int aliveIn(String teamId) {
        TeamManager.KLTeam team = TeamManager.get(teamId);
        if (team == null) {
            return 0;
        }
        Map<UUID, String> alive = RoleManager.participants();
        int n = 0;
        for (String uuid : team.members.keySet()) {
            try {
                if (alive.containsKey(UUID.fromString(uuid))) {
                    n++;
                }
            } catch (IllegalArgumentException ignored) {
                // uuid invalide : ignore
            }
        }
        return n;
    }

    // ------------------------------------------------------------ actions

    /** Demarre la saison : fige le nombre de joueurs de depart, jour 1, avant fusion. */
    public static void start(MinecraftServer server) {
        data.startPlayers = alive();
        data.day = 1;
        data.phase = Phase.PRE_MERGE.name();
        data.step = Step.CAMP.name();
        data.mergeAlerted = false;
        data.finaleAlerted = false;
        data.paused = false;
        data.lastWorldDay = worldDay(server);
        save();
        SeasonHistory.log("jour", "La saison commence avec " + data.startPlayers + " joueurs.");
        announceDay(server);
    }

    public static void setDay(MinecraftServer server, int day, boolean announce) {
        data.day = Math.max(1, day);
        save();
        SeasonHistory.log("jour", "Jour " + data.day + ".");
        if (announce) {
            announceDay(server);
        }
    }

    public static void setPhase(Phase phase) {
        if (phase() != phase) {
            data.phase = phase.name();
            save();
            SeasonHistory.log("phase", "Phase : " + phase.label() + ".");
        }
    }

    private static void broadcastTitle(MinecraftServer server, String title, String sub, String sound) {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            GameMessages.showTitle(p, Txt.of(title), Txt.of(sub), 10, 60, 20);
            GameMessages.playSound(p, sound, 1.0f);
        }
    }

    /** Met la saison en pause : le jour ne progresse plus (le temps en pause n'est pas compte au retour). */
    public static void pause(MinecraftServer server) {
        data.paused = true;
        save();
        SeasonHistory.log("phase", "Saison en pause (jour " + data.day + ").");
        broadcastTitle(server, t("season.pause.title"), t("season.pause.sub"), "minecraft:block.note_block.chime");
    }

    public static void resume(MinecraftServer server) {
        data.paused = false;
        data.lastWorldDay = worldDay(server);
        save();
        SeasonHistory.log("phase", "Reprise de la saison (jour " + data.day + ").");
        broadcastTitle(server, t("season.resume.title"), t("season.resume.sub", data.day), "minecraft:block.bell.use");
    }

    /** Fin de la saison : phase terminee, annonce a tous. */
    public static void end(MinecraftServer server) {
        data.paused = false;
        data.phase = Phase.ENDED.name();
        save();
        SeasonHistory.log("phase", "Fin de la saison au jour " + data.day + ".");
        broadcastTitle(server, t("season.end.title"), t("season.end.sub", data.day), "minecraft:ui.toast.challenge_complete");
    }

    public static void toggleAuto(MinecraftServer server) {
        data.autoDay = !data.autoDay;
        data.lastWorldDay = worldDay(server);
        save();
    }

    public static void setStep(Step step) {
        data.step = step.name();
        save();
    }

    /** Etape suivante de l'episode : camp -> recompense -> immunite -> conseil -> camp (jour suivant si demande). */
    public static Step nextStep() {
        return switch (step()) {
            case CAMP -> Step.REWARD;
            case REWARD -> Step.IMMUNITY;
            case IMMUNITY -> Step.COUNCIL;
            case COUNCIL -> Step.CAMP;
        };
    }

    /** Appele par la fusion manuelle. */
    public static void onMerge() {
        if (phase() == Phase.PRE_MERGE) {
            setPhase(Phase.POST_MERGE);
        }
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        warnJuryParity(server, alive());
    }

    /** Jury = joueurs de la fusion elimines avant les 3 derniers (N - 3) : il doit etre impair, donc N pair. */
    static void warnJuryParity(net.minecraft.server.MinecraftServer server, int players) {
        if (server == null || players < 4) {
            return;
        }
        int jury = players - 3;
        if (jury % 2 == 0) {
            notifyStaff(server, t("season.alert.jury", players, jury, players - 1, jury - 1, players + 1, jury + 1));
        }
    }

    /** Appele quand la fusion est annulee. */
    public static void onUnmerge() {
        if (phase() == Phase.POST_MERGE) {
            setPhase(Phase.PRE_MERGE);
        }
    }

    /** Appele apres chaque elimination : previent le staff quand il faut fusionner ou lancer la finale. */
    public static void onElimination(MinecraftServer server) {
        int left = alive();
        if (data.mergeAt > 0 && !data.mergeAlerted && phase() == Phase.PRE_MERGE && left <= data.mergeAt) {
            data.mergeAlerted = true;
            save();
            notifyStaff(server, t("season.alert.merge", left));
            warnJuryParity(server, left);
        }
        if (data.finaleAt > 0 && !data.finaleAlerted && phase() != Phase.FINALE && left <= data.finaleAt) {
            data.finaleAlerted = true;
            save();
            notifyStaff(server, t("season.alert.finale", left));
        }
    }

    // ------------------------------------------------------------ jour du monde

    private static long worldDay(MinecraftServer server) {
        ServerLevel overworld = server == null ? null : server.getLevel(Level.OVERWORLD);
        return overworld == null ? -1 : overworld.getDayTime() / 24000L;
    }

    /** Verifie une fois par seconde si le monde est passe au jour suivant. */
    private static void checkWorldDay(MinecraftServer server) {
        if (!data.autoDay || data.paused || phase() == Phase.ENDED) {
            return;
        }
        long wd = worldDay(server);
        if (wd < 0) {
            return;
        }
        if (data.lastWorldDay < 0) {
            data.lastWorldDay = wd;
            save();
            return;
        }
        if (wd > data.lastWorldDay) {
            data.day += (int) (wd - data.lastWorldDay);
            data.lastWorldDay = wd;
            data.step = Step.CAMP.name();
            save();
            SeasonHistory.log("jour", "Jour " + data.day + ".");
            announceDay(server);
        } else if (wd < data.lastWorldDay) {
            data.lastWorldDay = wd; // le temps du monde a ete reculé : on se recale sans toucher au jour
            save();
        }
    }

    // ------------------------------------------------------------ messages

    private static void announceDay(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            GameMessages.showTitle(p, Txt.of(t("season.title.day", data.day)), Txt.of("&7" + phase().label()), 10, 50, 20);
            GameMessages.playSound(p, "minecraft:block.bell.use", 0.8f);
        }
    }

    private static void notifyStaff(MinecraftServer server, String text) {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (KLPermissions.levelOf(p) > KLPermissions.NONE) {
                p.sendSystemMessage(Txt.of(t("season.staff_prefix") + text));
            }
        }
    }

    // ------------------------------------------------------------ tableau de bord

    /** Conseil donne a l'organisateur pour la suite. */
    public static String nextAction() {
        return switch (phase()) {
            case ENDED -> t("season.next.ended");
            case FINALE -> t("season.next.finale");
            default -> t("season.next." + step().name().toLowerCase());
        };
    }

    public static List<Component> dashboard() {
        List<Component> out = new ArrayList<>();
        out.add(Txt.of(t("season.dash.title", data.day, phase().label())));
        out.add(Txt.of(t(data.paused ? "season.dash.paused" : "season.dash.running")));
        out.add(Txt.of(t("season.dash.step", step().label(), t(data.autoDay ? "season.dash.auto" : "season.dash.manual"))));
        int left = alive();
        String start = data.startPlayers > 0 ? "&7/&f" + data.startPlayers : "";
        out.add(Txt.of(t("season.dash.alive", left, start)));
        if (phase() == Phase.PRE_MERGE && data.mergeAt > 0) {
            int toGo = left - data.mergeAt;
            out.add(Txt.of(t("season.dash.merge", data.mergeAt,
                    toGo > 0 ? t("season.dash.merge.left", toGo) : t("season.dash.merge.now"))));
        }
        if (phase() != Phase.FINALE && phase() != Phase.ENDED && data.finaleAt > 0) {
            out.add(Txt.of(t("season.dash.finale", data.finaleAt)));
        }
        for (Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            TeamManager.KLTeam team = e.getValue();
            out.add(Txt.of(t("season.dash.team", team.format().getChar(), team.name, aliveIn(e.getKey()))));
        }
        out.add(Txt.of("&e➤ &7" + nextAction()));
        return out;
    }

    public static void sendDashboard(CommandSourceStack source) {
        for (Component line : dashboard()) {
            source.sendSystemMessage(line);
        }
    }

    // ------------------------------------------------------------ commande /ccis season

    private static int reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Txt.ok(text), false);
        return 1;
    }

    private static final String[][] SETUP_TEAMS = {
            {"red", "red"}, {"blue", "blue"}, {"yellow", "yellow"}, {"green", "green"},
            {"orange", "gold"}, {"purple", "light_purple"}, {"aqua", "aqua"}, {"pink", "dark_red"}};

    /** /ccis season setup <equipes> <joueurs_a_la_fusion> : prepare les equipes et le seuil de fusion en une fois. */
    private static int setup(CommandContext<CommandSourceStack> ctx) {
        int teams = IntegerArgumentType.getInteger(ctx, "teams");
        int mergeAt = IntegerArgumentType.getInteger(ctx, "players");
        int players = alive();
        if (players < teams) {
            ctx.getSource().sendFailure(Txt.err(t("season.setup.fail.players", players, teams)));
            return 0;
        }
        if (mergeAt >= players) {
            ctx.getSource().sendFailure(Txt.err(t("season.setup.fail.merge", mergeAt, players)));
            return 0;
        }
        int created = 0;
        for (String[] def : SETUP_TEAMS) {
            if (TeamManager.gameTeamCount() >= teams) {
                break;
            }
            net.minecraft.ChatFormatting color = net.minecraft.ChatFormatting.getByName(def[1]);
            if (color != null && TeamManager.createTeam(def[0], color) == null) {
                created++;
            }
        }
        data.mergeAt = mergeAt;
        data.mergeAlerted = false;
        data.finaleAt = 3;
        data.finaleAlerted = false;
        save();
        int jury = mergeAt - 3;
        final String summary = t("season.setup.ok", players, TeamManager.gameTeamCount(), created,
                (int) Math.ceil(players / (double) teams), mergeAt, jury);
        ctx.getSource().sendSuccess(() -> Txt.ok(summary), false);
        if (jury % 2 == 0) {
            ctx.getSource().sendSystemMessage(Txt.of(t("season.alert.jury", mergeAt, jury, mergeAt - 1, jury - 1, mergeAt + 1, jury + 1)));
        }
        if (players % teams != 0) {
            int lower = players / teams * teams;
            ctx.getSource().sendSystemMessage(Txt.of(t("team.draw.uneven", players, teams, lower, lower + teams)));
        }
        if (TeamManager.gameTeamCount() > teams) {
            ctx.getSource().sendSystemMessage(Txt.of(t("season.setup.extra", TeamManager.gameTeamCount(), teams)));
        }
        ctx.getSource().sendSystemMessage(Txt.of(t("season.setup.next")));
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("season")
                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                .executes(ctx -> {
                    sendDashboard(ctx.getSource());
                    return 1;
                })
                .then(Commands.literal("start").executes(ctx -> {
                    start(ctx.getSource().getServer());
                    return reply(ctx, t("season.cmd.started", data.startPlayers));
                }))
                .then(Commands.literal("jury").executes(ctx -> {
                    FinaleManager.showJury(ctx.getSource());
                    return 1;
                }))
                .then(Commands.literal("reset")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(Txt.info(t("season.cmd.reset_ask")));
                            return 1;
                        })
                        .then(Commands.literal("confirm").executes(ctx -> {
                            data = new Data();
                            data.lastWorldDay = worldDay(ctx.getSource().getServer());
                            save();
                            SeasonHistory.clear();
                            return reply(ctx, t("season.cmd.reset"));
                        })))
                .then(Commands.literal("pause").executes(ctx -> {
                    pause(ctx.getSource().getServer());
                    return reply(ctx, t("season.cmd.paused"));
                }))
                .then(Commands.literal("resume").executes(ctx -> {
                    resume(ctx.getSource().getServer());
                    return reply(ctx, t("season.cmd.resumed", data.day));
                }))
                .then(Commands.literal("end").executes(ctx -> {
                    end(ctx.getSource().getServer());
                    return reply(ctx, t("season.cmd.ended"));
                }))
                .then(Commands.literal("day")
                        .then(Commands.literal("next").executes(ctx -> {
                            setDay(ctx.getSource().getServer(), data.day + 1, true);
                            data.step = Step.CAMP.name();
                            save();
                            return reply(ctx, t("season.cmd.day", data.day));
                        }))
                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 999)).executes(ctx -> {
                            setDay(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "day"), true);
                            return reply(ctx, t("season.cmd.day", data.day));
                        })))
                .then(Commands.literal("auto")
                        .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                            data.autoDay = BoolArgumentType.getBool(ctx, "enabled");
                            data.lastWorldDay = worldDay(ctx.getSource().getServer());
                            save();
                            return reply(ctx, t(data.autoDay ? "season.cmd.auto_on" : "season.cmd.auto_off"));
                        })))
                .then(Commands.literal("phase")
                        .then(Commands.argument("phase", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        List.of("pre_merge", "post_merge", "finale", "ended"), b))
                                .executes(ctx -> {
                                    String key = StringArgumentType.getString(ctx, "phase").toUpperCase();
                                    try {
                                        setPhase(Phase.valueOf(key));
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendFailure(Txt.err(t("season.cmd.phase_unknown")));
                                        return 0;
                                    }
                                    return reply(ctx, t("season.cmd.phase", phase().label()));
                                })))
                .then(Commands.literal("step")
                        .then(Commands.literal("next").executes(ctx -> {
                            setStep(nextStep());
                            return reply(ctx, t("season.cmd.step", step().label()));
                        }))
                        .then(Commands.argument("step", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        List.of("camp", "reward", "immunity", "council"), b))
                                .executes(ctx -> {
                                    try {
                                        setStep(Step.valueOf(StringArgumentType.getString(ctx, "step").toUpperCase()));
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendFailure(Txt.err(t("season.cmd.step_unknown")));
                                        return 0;
                                    }
                                    return reply(ctx, t("season.cmd.step", step().label()));
                                })))
                .then(SimulationManager.command())
                .then(Commands.literal("fake")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.literal("clear").executes(ctx ->
                                reply(ctx, t("season.fake.cleared", RoleManager.clearFakes()))))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 40)).executes(ctx -> {
                            int n = IntegerArgumentType.getInteger(ctx, "count");
                            RoleManager.addFakes(n);
                            return reply(ctx, t("season.fake.added", n, alive()));
                        })))
                .then(Commands.literal("setup")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.argument("teams", IntegerArgumentType.integer(2, 8))
                                .then(Commands.argument("players", IntegerArgumentType.integer(4, 200)).executes(SeasonManager::setup))))
                .then(Commands.literal("mergeat")
                        .then(Commands.argument("players", IntegerArgumentType.integer(0, 200)).executes(ctx -> {
                            data.mergeAt = IntegerArgumentType.getInteger(ctx, "players");
                            data.mergeAlerted = false;
                            save();
                            return reply(ctx, data.mergeAt == 0 ? t("season.cmd.mergeat_off") : t("season.cmd.mergeat_on", data.mergeAt));
                        })))
                .then(Commands.literal("finaleat")
                        .then(Commands.argument("players", IntegerArgumentType.integer(0, 200)).executes(ctx -> {
                            data.finaleAt = IntegerArgumentType.getInteger(ctx, "players");
                            data.finaleAlerted = false;
                            save();
                            return reply(ctx, data.finaleAt == 0 ? t("season.cmd.finaleat_off") : t("season.cmd.finaleat_on", data.finaleAt));
                        })));
    }

    // ------------------------------------------------------------ evenements

    /** A enregistrer sur le bus Forge. */
    public static final class Events {
        private int ticks;

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || ++ticks < 20) {
                return;
            }
            ticks = 0;
            checkWorldDay(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
            FinaleManager.tick(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        }
    }
}
