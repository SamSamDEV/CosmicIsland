package fr.cosmicisland.conseil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import fr.cosmicisland.KLFiles;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.KLRole;
import fr.cosmicisland.RoleManager;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import fr.cosmicisland.game.GameConfig;
import fr.cosmicisland.game.SpawnManager;
import fr.cosmicisland.gui.KLMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Conseil d'elimination. Etapes : start -> VOTING -> (vote close) CLOSED -> (reveal) REVEALING -> REVEALED
 * -> (close) fin. Un nouveau vote (revote) est possible depuis CLOSED ou REVEALED.
 * Tout est declenche a la main par l'organisation (commandes ou menu admin du conseil).
 */
public final class ConseilManager {

    public enum Phase { IDLE, VOTING, CLOSED, REVEALING, REVEALED }

    private record RevealItem(UUID voter, UUID target, boolean valid) {}

    private static ConseilConfig config = new ConseilConfig();
    private static MinecraftServer server;
    private static Phase phase = Phase.IDLE;

    private static String teamId = null;
    private static String startedAt = "";
    private static int round = 1;

    private static final Map<UUID, String> candidates = new LinkedHashMap<>(); // uuid -> pseudo
    private static final Map<UUID, UUID> votes = new LinkedHashMap<>();        // votant -> cible (tour en cours)
    private static final Set<UUID> immune = new LinkedHashSet<>();
    private static final Set<UUID> fakes = new LinkedHashSet<>();               // joueurs fictifs (mode test)
    private static final Map<UUID, Integer> tally = new LinkedHashMap<>();     // votes reveles valides
    private static final List<JsonObject> rounds = new ArrayList<>();

    private static List<RevealItem> queue = new ArrayList<>();
    private static int revealIndex = 0;
    private static int revealTicks = 0;
    private static boolean finishing = false;

    private static int voteTicksLeft = -1;
    private static int voteTicksTotal = -1;
    private static ServerBossEvent bar;

    private static UUID eliminated = null;
    private static String lastResult = ""; // eliminated, tie, noValid

    private ConseilManager() {}

    // ------------------------------------------------------------ cycle de vie

    public static void load() {
        config = KLFiles.load("conseil.json", ConseilConfig.class, ConseilConfig::new);
        // Anciens fichiers : les commandes /kl et /cmis ont ete renommees /ccis
        if (config.messages != null) {
            for (GameConfig.Msg msg : config.messages.values()) {
                if (msg.text != null) {
                    msg.text = msg.text.replace("/kl ", "/ccis ").replace("/cmis ", "/ccis ");
                }
                if (msg.subtitle != null) {
                    msg.subtitle = msg.subtitle.replace("/kl ", "/ccis ").replace("/cmis ", "/ccis ");
                }
            }
        }
    }

    public static void init(MinecraftServer s) {
        server = s;
    }

    public static void shutdown() {
        if (server != null && phase != Phase.IDLE) {
            writeLog("interrupted", null);
            reset();
        }
        server = null;
    }

    // ------------------------------------------------------------ acces

    public static ConseilConfig config() {
        return config;
    }

    public static Phase phase() {
        return phase;
    }

    public static Map<UUID, String> candidates() {
        return candidates;
    }

    public static boolean isCandidate(UUID id) {
        return candidates.containsKey(id);
    }

    public static UUID voteOf(UUID voter) {
        return votes.get(voter);
    }

    public static boolean hasVoted(UUID voter) {
        return votes.containsKey(voter);
    }

    public static boolean isImmune(UUID id) {
        return immune.contains(id);
    }

    /** Votes recus par un joueur dans le tour en cours (comptage brut, immunite non appliquee). */
    public static int votesReceived(UUID target) {
        int count = 0;
        for (UUID t : votes.values()) {
            if (t.equals(target)) {
                count++;
            }
        }
        return count;
    }

    public static int votesCount() {
        return votes.size();
    }

    public static int round() {
        return round;
    }

    public static String phaseLabel() {
        return switch (phase) {
            case IDLE -> "aucun conseil";
            case VOTING -> "vote en cours";
            case CLOSED -> "vote clos";
            case REVEALING -> "dépouillement en cours";
            case REVEALED -> "résultat affiché (en attente de clôture)";
        };
    }

    public static String resultLabel() {
        if (phase != Phase.REVEALED) {
            return "-";
        }
        return switch (lastResult) {
            case "eliminated" -> "éliminé : " + candidates.getOrDefault(eliminated, "?");
            case "tie" -> "égalité (nouveau vote nécessaire)";
            case "noValid" -> "aucun vote valide (nouveau vote nécessaire)";
            default -> "-";
        };
    }

    /** Le joueur (connecte) a-t-il le collier d'immunite dans son inventaire ? Indication pour l'organisateur. */
    public static boolean hasCollier(UUID id) {
        ServerPlayer p = online(id);
        if (p == null) {
            return false;
        }
        Inventory inv = p.getInventory();
        for (ItemStack s : inv.items) {
            if (s.is(ConseilRegistry.COLLIER.get())) {
                return true;
            }
        }
        for (ItemStack s : inv.offhand) {
            if (s.is(ConseilRegistry.COLLIER.get())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ demarrage

    /** Lance un conseil pour une equipe : teleporte ses participants et ouvre le vote. */
    public static String start(String team) {
        return start(team, 0);
    }

    /** Comme start, avec des joueurs fictifs (test en solo) qui votent au hasard. */
    public static String start(String team, int fakeCount) {
        if (server == null) {
            return "Le serveur n'est pas prêt.";
        }
        if (phase != Phase.IDLE) {
            return "Un conseil est déjà en cours (équipe " + teamName() + "). Utilise /ccis conseil cancel pour l'annuler.";
        }
        TeamManager.KLTeam t = TeamManager.get(team);
        if (t == null) {
            return "Équipe inconnue : " + team + " (équipes : " + String.join(", ", TeamManager.ids()) + ")";
        }
        SpawnManager.Spawn council = SpawnManager.get("conseil");
        if (council == null) {
            return "Spawn du conseil manquant : place-toi à l'endroit voulu et tape /ccis spawn set conseil.";
        }
        if (SpawnManager.get("elimination") == null) {
            return "Spawn d'élimination manquant : place-toi à l'endroit voulu et tape /ccis spawn set elimination.";
        }

        Map<UUID, String> found = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : t.members.entrySet()) {
            try {
                UUID id = UUID.fromString(e.getKey());
                if (RoleManager.get(id) == KLRole.PARTICIPANT) {
                    found.put(id, e.getValue());
                }
            } catch (IllegalArgumentException ignored) {
                // identifiant invalide dans teams.json : ignore
            }
        }
        Set<UUID> newFakes = new LinkedHashSet<>();
        for (int i = 1; i <= fakeCount; i++) {
            UUID fakeId = UUID.nameUUIDFromBytes(
                    ("cosmicisland-test-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            found.put(fakeId, "Testeur" + i);
            newFakes.add(fakeId);
        }
        if (found.size() < Math.max(1, config.minParticipants)) {
            return "Pas assez de participants dans l'équipe " + t.name + " (" + found.size()
                    + " trouvé(s), minimum " + Math.max(1, config.minParticipants)
                    + " : voir minParticipants dans conseil.json).";
        }

        reset();
        fakes.addAll(newFakes);
        teamId = team.toLowerCase();
        candidates.putAll(found);
        round = 1;
        startedAt = LocalDateTime.now().withNano(0).toString();

        for (UUID id : candidates.keySet()) {
            ServerPlayer p = online(id);
            if (p != null) {
                SpawnManager.teleport(server, p, council);
            }
        }
        startVoting();
        notifyStaff("Conseil lancé pour l'équipe &f" + t.name + "&7 (&f" + candidates.size()
                + "&7 participants). Suis le déroulé avec &f/ccis conseil menu&7.");
        return null;
    }

    private static void startVoting() {
        phase = Phase.VOTING;
        votes.clear();
        tally.clear();
        queue = new ArrayList<>();
        revealIndex = 0;
        revealTicks = 0;
        finishing = false;
        eliminated = null;
        lastResult = "";
        voteTicksTotal = config.voteSeconds > 0 ? config.voteSeconds * 20 : -1;
        voteTicksLeft = voteTicksTotal;
        sendTally(false);
        for (UUID id : candidates.keySet()) {
            ServerPlayer p = online(id);
            if (p != null) {
                ConseilMessages.send(config.message("voteStart"), p, Map.of("equipe", teamName()));
                ConseilMenus.openVote(p);
            }
        }
        castFakeVotes();
        updateBar();
        refreshAdminMenus();
    }

    /** Mode test : les joueurs fictifs votent au hasard au debut de chaque tour. */
    private static void castFakeVotes() {
        if (fakes.isEmpty()) {
            return;
        }
        List<UUID> all = new ArrayList<>(candidates.keySet());
        Random random = new Random();
        for (UUID fake : fakes) {
            List<UUID> options = new ArrayList<>();
            for (UUID id : all) {
                if (config.allowSelfVote || !id.equals(fake)) {
                    options.add(id);
                }
            }
            if (!options.isEmpty()) {
                votes.put(fake, options.get(random.nextInt(options.size())));
            }
        }
        notifyStaff("TEST : &f" + fakes.size() + "&7 joueurs fictifs ont voté au hasard.");
    }

    // ------------------------------------------------------------ vote

    public static String castVote(ServerPlayer voter, UUID target) {
        if (phase != Phase.VOTING) {
            return "Le vote n'est pas ouvert.";
        }
        UUID id = voter.getUUID();
        if (!candidates.containsKey(id)) {
            return "Tu ne participes pas à ce conseil.";
        }
        if (!candidates.containsKey(target)) {
            return "Ce joueur ne peut pas être voté.";
        }
        if (id.equals(target) && !config.allowSelfVote) {
            return "Tu ne peux pas voter pour toi-même.";
        }
        boolean first = !votes.containsKey(id);
        votes.put(id, target);
        ConseilMessages.send(config.message("voteRecap"), voter, Map.of("cible", candidates.get(target)));
        notifyStaff("&f" + voter.getGameProfile().getName() + "&7 a voté (&f" + votes.size() + "&7/&f"
                + candidates.size() + "&7)" + (first ? "" : " &8(vote modifié)"));
        if (votes.size() >= candidates.size()) {
            notifyStaff("Tout le monde a voté : clos le vote avec &f/ccis conseil vote close&7 ou le menu.");
        }
        refreshAdminMenus();
        return null;
    }

    /** Cloture le vote (aucun vote supplementaire possible). */
    public static String closeVote() {
        if (phase != Phase.VOTING) {
            return phase == Phase.IDLE ? "Aucun conseil en cours." : "Le vote est déjà clos.";
        }
        phase = Phase.CLOSED;
        removeBar();
        for (UUID id : candidates.keySet()) {
            ServerPlayer p = online(id);
            if (p != null) {
                closeVoteMenus(p);
                ConseilMessages.send(config.message("voteClosed"), p, Map.of("equipe", teamName()));
            }
        }
        notifyStaff("Vote clos : &f" + votes.size() + "&7/&f" + candidates.size()
                + "&7 votes reçus. Marque les immunités puis lance &f/ccis conseil reveal&7.");
        refreshAdminMenus();
        return null;
    }

    // ------------------------------------------------------------ immunite

    /** Active ou retire l'immunite d'un joueur (possible avant le debut du depouillement). */
    public static String toggleImmune(UUID id) {
        if (phase == Phase.IDLE) {
            return "Aucun conseil en cours.";
        }
        if (phase != Phase.VOTING && phase != Phase.CLOSED) {
            return "Trop tard : le dépouillement a commencé.";
        }
        if (!candidates.containsKey(id)) {
            return "Ce joueur ne fait pas partie du conseil.";
        }
        boolean now;
        if (immune.contains(id)) {
            immune.remove(id);
            now = false;
        } else {
            immune.add(id);
            now = true;
        }
        notifyStaff("Immunité de &f" + candidates.get(id) + "&7 : " + (now ? "&aOUI" : "&cNON"));
        refreshAdminMenus();
        return null;
    }

    // ------------------------------------------------------------ depouillement

    public static String reveal() {
        if (phase == Phase.IDLE) {
            return "Aucun conseil en cours.";
        }
        if (phase == Phase.VOTING) {
            return "Clos d'abord le vote (/ccis conseil vote close).";
        }
        if (phase != Phase.CLOSED) {
            return "Le dépouillement a déjà eu lieu (utilise /ccis conseil revote ou /ccis conseil close).";
        }
        List<RevealItem> items = new ArrayList<>();
        for (Map.Entry<UUID, UUID> e : votes.entrySet()) {
            items.add(new RevealItem(e.getKey(), e.getValue(), !immune.contains(e.getValue())));
        }
        Collections.shuffle(items);
        tally.clear();
        for (UUID id : candidates.keySet()) {
            tally.put(id, 0);
        }
        queue = items;
        revealIndex = 0;
        finishing = false;
        phase = Phase.REVEALING;
        revealTicks = Math.max(1, config.revealStartDelaySeconds) * 20;
        for (ServerPlayer p : audience()) {
            ConseilMessages.send(config.message("revealStart"), p, Map.of("equipe", teamName()));
        }
        sendTally(true);
        refreshAdminMenus();
        return null;
    }

    private static void revealStep() {
        if (finishing) {
            finalizeResult();
            return;
        }
        if (revealIndex >= queue.size()) {
            finishing = true;
            revealTicks = 20;
            return;
        }
        RevealItem item = queue.get(revealIndex++);
        String target = candidates.getOrDefault(item.target(), "?");
        if (item.valid()) {
            tally.merge(item.target(), 1, Integer::sum);
        }
        Map<String, String> vars = Map.of(
                "cible", target,
                "n", String.valueOf(revealIndex),
                "total", String.valueOf(queue.size()));
        GameConfig.Msg msg = config.message(item.valid() ? "revealVote" : "revealVoid");
        for (ServerPlayer p : audience()) {
            ConseilMessages.send(msg, p, vars);
        }
        sendTally(true);
        if (revealIndex >= queue.size()) {
            finishing = true;
        }
        revealTicks = Math.max(1, config.revealIntervalSeconds) * 20;
    }

    private static void finalizeResult() {
        int best = 0;
        List<UUID> leaders = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : tally.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                leaders.clear();
                leaders.add(e.getKey());
            } else if (e.getValue() == best && best > 0) {
                leaders.add(e.getKey());
            }
        }
        if (best == 0) {
            lastResult = "noValid";
            eliminated = null;
        } else if (leaders.size() == 1) {
            lastResult = "eliminated";
            eliminated = leaders.get(0);
        } else {
            lastResult = "tie";
            eliminated = null;
        }
        phase = Phase.REVEALED;
        recordRound(false);

        String key = lastResult;
        String name = eliminated == null ? "" : candidates.getOrDefault(eliminated, "?");
        Map<String, String> vars = Map.of("cible", name, "equipe", teamName());
        for (ServerPlayer p : audience()) {
            ConseilMessages.send(config.message(key), p, vars);
        }
        if (eliminated != null) {
            notifyStaff("Résultat : &c" + name + "&7 est éliminé. Laisse-lui le temps de son discours, puis "
                    + "valide avec &f/ccis conseil close&7 (ou le menu).");
        } else {
            notifyStaff("Résultat : " + (lastResult.equals("tie") ? "&eégalité" : "&eaucun vote valide")
                    + "&7. Lance un nouveau vote avec &f/ccis conseil revote&7 (ou le menu).");
        }
        refreshAdminMenus();
    }

    // ------------------------------------------------------------ nouveau vote

    public static String revote() {
        if (phase == Phase.IDLE) {
            return "Aucun conseil en cours.";
        }
        if (phase != Phase.CLOSED && phase != Phase.REVEALED) {
            return "Impossible pendant le vote ou le dépouillement.";
        }
        if (phase == Phase.CLOSED) {
            recordRound(true); // tour ecarte avant depouillement : on garde une trace dans le log
        }
        round++;
        startVoting();
        notifyStaff("Nouveau vote lancé (tour &f" + round + "&7). Les immunités sont conservées.");
        return null;
    }

    // ------------------------------------------------------------ cloture

    /** Valide le resultat : l'elimine part (spectateur + point d'elimination), les autres retournent au camp. */
    public static String close() {
        if (phase == Phase.IDLE) {
            return "Aucun conseil en cours.";
        }
        if (phase != Phase.REVEALED) {
            return "Le dépouillement n'est pas terminé (étapes : vote close, reveal, puis close).";
        }
        if (eliminated == null) {
            return "Pas d'éliminé (égalité ou aucun vote valide) : lance un nouveau vote avec /ccis conseil revote.";
        }
        UUID out = eliminated;
        String outName = candidates.getOrDefault(out, "?");

        boolean test = !fakes.isEmpty(); // en mode test, aucun role n'est modifie
        if (!test) {
            RoleManager.set(new GameProfile(out, outName), KLRole.SPECTATEUR);
        }
        ServerPlayer outPlayer = online(out);
        if (outPlayer != null) {
            if (!test) {
                RoleManager.apply(outPlayer, KLRole.SPECTATEUR);
            }
            SpawnManager.Spawn elimination = SpawnManager.get("elimination");
            if (elimination != null) {
                SpawnManager.teleport(server, outPlayer, elimination);
            }
            ConseilMessages.send(config.message("farewell"), outPlayer, Map.of("equipe", teamName()));
        }

        SpawnManager.Spawn camp = teamId == null ? null : SpawnManager.baseSpawn(teamId);
        for (UUID id : candidates.keySet()) {
            if (id.equals(out)) {
                continue;
            }
            ServerPlayer p = online(id);
            if (p != null) {
                closeVoteMenus(p);
                if (camp != null) {
                    SpawnManager.teleport(server, p, camp);
                }
            }
        }
        writeLog("closed", outName);
        notifyStaff("Conseil clos : &f" + outName + (test
                ? "&7 est éliminé (TEST : aucun rôle modifié)."
                : "&7 est éliminé (passé en spectateur)."));
        reset();
        return null;
    }

    public static String cancel() {
        if (phase == Phase.IDLE) {
            return "Aucun conseil en cours.";
        }
        SpawnManager.Spawn camp = teamId == null ? null : SpawnManager.baseSpawn(teamId);
        for (UUID id : candidates.keySet()) {
            ServerPlayer p = online(id);
            if (p != null) {
                closeVoteMenus(p);
                if (camp != null) {
                    SpawnManager.teleport(server, p, camp);
                }
            }
        }
        writeLog("cancelled", null);
        notifyStaff("Conseil annulé.");
        reset();
        return null;
    }

    private static void reset() {
        sendTally(false);
        removeBar();
        phase = Phase.IDLE;
        teamId = null;
        candidates.clear();
        votes.clear();
        immune.clear();
        fakes.clear();
        tally.clear();
        rounds.clear();
        queue = new ArrayList<>();
        revealIndex = 0;
        revealTicks = 0;
        finishing = false;
        round = 1;
        voteTicksLeft = -1;
        voteTicksTotal = -1;
        eliminated = null;
        lastResult = "";
    }

    // ------------------------------------------------------------ tick

    public static void tick() {
        if (phase == Phase.IDLE || server == null) {
            return;
        }
        if (phase == Phase.VOTING && voteTicksLeft > 0) {
            voteTicksLeft--;
            if (voteTicksLeft <= 0) {
                notifyStaff("Temps de vote écoulé.");
                closeVote();
                return;
            }
        }
        if (server.getTickCount() % 20 == 0) {
            if (phase == Phase.VOTING) {
                updateBar();
            }
            refreshAdminMenus();
        }
        if (phase == Phase.REVEALING) {
            revealTicks--;
            if (revealTicks <= 0) {
                revealStep();
            }
        }
    }

    private static void updateBar() {
        if (phase != Phase.VOTING) {
            removeBar();
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS);
        }
        String text;
        float progress;
        if (voteTicksTotal > 0) {
            int seconds = Math.max(0, (voteTicksLeft + 19) / 20);
            text = "&6&lVote en cours : &f" + String.format("%02d:%02d", seconds / 60, seconds % 60);
            progress = Math.max(0.0f, Math.min(1.0f, (float) voteTicksLeft / voteTicksTotal));
        } else {
            text = "&6&lVote en cours";
            progress = 1.0f;
        }
        bar.setName(Txt.of(text));
        bar.setProgress(progress);
        for (UUID id : candidates.keySet()) {
            ServerPlayer p = online(id);
            if (p != null) {
                bar.addPlayer(p);
            }
        }
    }

    private static void removeBar() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
    }

    private static void refreshAdminMenus() {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.containerMenu instanceof KLMenu menu && "conseil-admin".equals(menu.getTag())) {
                ConseilAdminMenu.fill(menu.getContainer());
            }
        }
    }

    // ------------------------------------------------------------ HUD et public

    /** Joueurs qui assistent au depouillement : participants du conseil, staff, et spectateurs (si active). */
    private static List<ServerPlayer> audience() {
        List<ServerPlayer> list = new ArrayList<>();
        if (server == null) {
            return list;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean candidate = candidates.containsKey(p.getUUID());
            boolean staff = KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR;
            boolean spectator = config.revealToSpectators && RoleManager.effective(p) == KLRole.SPECTATEUR;
            if (candidate || staff || spectator) {
                list.add(p);
            }
        }
        return list;
    }

    /** Envoie le decompte des votes (HUD). active = false : masque le HUD chez tous les joueurs. */
    private static void sendTally(boolean active) {
        if (server == null) {
            return;
        }
        if (!active) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ConseilNetwork.toPlayer(p, new ConseilNetwork.TallyPacket(false, new ArrayList<>(), new ArrayList<>()));
            }
            return;
        }
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Map.Entry<UUID, String> e : candidates.entrySet()) {
            names.add(e.getValue());
            counts.add(tally.getOrDefault(e.getKey(), 0));
        }
        for (ServerPlayer p : audience()) {
            ConseilNetwork.toPlayer(p, new ConseilNetwork.TallyPacket(true, names, counts));
        }
    }

    private static void closeVoteMenus(ServerPlayer p) {
        if (p.containerMenu instanceof KLMenu menu
                && ("conseil-vote".equals(menu.getTag()) || "conseil-confirm".equals(menu.getTag()))) {
            p.closeContainer();
        }
    }

    // ------------------------------------------------------------ logs : log_conseil/

    private static void recordRound(boolean discarded) {
        JsonObject r = new JsonObject();
        r.addProperty("tour", round);
        JsonArray list = new JsonArray();
        if (discarded) {
            for (Map.Entry<UUID, UUID> e : votes.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("votant", candidates.getOrDefault(e.getKey(), "?"));
                o.addProperty("cible", candidates.getOrDefault(e.getValue(), "?"));
                o.addProperty("valide", !immune.contains(e.getValue()));
                list.add(o);
            }
            r.addProperty("resultat", "ecarte_avant_depouillement");
        } else {
            for (RevealItem item : queue) {
                JsonObject o = new JsonObject();
                o.addProperty("votant", candidates.getOrDefault(item.voter(), "?"));
                o.addProperty("cible", candidates.getOrDefault(item.target(), "?"));
                o.addProperty("valide", item.valid());
                list.add(o);
            }
            JsonObject counts = new JsonObject();
            for (Map.Entry<UUID, Integer> e : tally.entrySet()) {
                counts.addProperty(candidates.getOrDefault(e.getKey(), "?"), e.getValue());
            }
            r.add("decompte", counts);
            r.addProperty("resultat", lastResult);
            if (eliminated != null) {
                r.addProperty("elimine", candidates.getOrDefault(eliminated, "?"));
            }
        }
        r.add("votes", list);
        rounds.add(r);
    }

    private static void writeLog(String status, String eliminatedName) {
        JsonObject o = new JsonObject();
        o.addProperty("debut", startedAt);
        o.addProperty("fin", LocalDateTime.now().withNano(0).toString());
        o.addProperty("equipe", teamId == null ? "" : teamId);
        o.addProperty("statut", status);
        if (!fakes.isEmpty()) {
            o.addProperty("test", true);
        }
        if (eliminatedName != null) {
            o.addProperty("elimine", eliminatedName);
        }
        JsonArray names = new JsonArray();
        for (String n : candidates.values()) {
            names.add(n);
        }
        o.add("participants", names);
        JsonArray immunes = new JsonArray();
        for (UUID id : immune) {
            immunes.add(candidates.getOrDefault(id, "?"));
        }
        o.add("immunises", immunes);
        JsonArray allRounds = new JsonArray();
        for (JsonObject r : rounds) {
            allRounds.add(r);
        }
        o.add("tours", allRounds);
        if (phase == Phase.VOTING || phase == Phase.CLOSED || phase == Phase.REVEALING) {
            JsonArray current = new JsonArray();
            for (Map.Entry<UUID, UUID> e : votes.entrySet()) {
                JsonObject v = new JsonObject();
                v.addProperty("votant", candidates.getOrDefault(e.getKey(), "?"));
                v.addProperty("cible", candidates.getOrDefault(e.getValue(), "?"));
                current.add(v);
            }
            o.add("tourInacheve", current);
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        KLFiles.save("log_conseil/conseil_" + stamp + "_" + (teamId == null ? "equipe" : teamId) + ".json", o);
    }

    // ------------------------------------------------------------ utilitaires

    private static ServerPlayer online(UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static String teamName() {
        if (teamId == null) {
            return "";
        }
        TeamManager.KLTeam t = TeamManager.get(teamId);
        return t == null ? teamId : t.name;
    }

    /** Message aux organisateurs/admins connectes et a la console. */
    private static void notifyStaff(String text) {
        if (server == null) {
            return;
        }
        Component msg = Component.empty()
                .append(Component.literal("[Conseil] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Txt.of(text).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR) {
                p.sendSystemMessage(msg);
            }
        }
        server.sendSystemMessage(msg);
    }

    public static List<Component> statusLines() {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal("=== Conseil ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (phase == Phase.IDLE) {
            out.add(Component.literal("Aucun conseil en cours.").withStyle(ChatFormatting.GRAY));
            return out;
        }
        out.add(row("Équipe", teamName()));
        out.add(row("Phase", phaseLabel()));
        out.add(row("Tour", String.valueOf(round)));
        out.add(row("Votes reçus", votes.size() + "/" + candidates.size()));
        List<String> immuneNames = new ArrayList<>();
        for (UUID id : immune) {
            immuneNames.add(candidates.getOrDefault(id, "?"));
        }
        out.add(row("Immunisés", immuneNames.isEmpty() ? "aucun" : String.join(", ", immuneNames)));
        if (phase == Phase.REVEALED) {
            out.add(row("Résultat", resultLabel()));
        }
        return out;
    }

    private static Component row(String label, String value) {
        return Component.empty()
                .append(Component.literal(label + " : ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }
}
