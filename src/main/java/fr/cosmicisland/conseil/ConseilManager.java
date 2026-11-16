package fr.cosmicisland.conseil;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import fr.cosmicisland.CosmicIsland;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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

    public enum Phase { IDLE, VOTING, CLOSED, COLLIER, REVEALING, REVEALED }

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
    private static final Set<UUID> collierPending = new LinkedHashSet<>(); // porteurs de collier qui n'ont pas encore repondu
    private static final Set<UUID> totemProtected = new LinkedHashSet<>();  // proteges par un totem (non votables)
    private static int collierTicks = 0;
    private static boolean collierUsed = false;

    private static int voteTicksLeft = -1;
    private static int voteTicksTotal = -1;
    private static ServerBossEvent bar;

    // Second vote apres une egalite : seuls les joueurs a egalite sont votables, et ils ne votent pas (comme a la tele)
    private static final Set<UUID> restrictTargets = new LinkedHashSet<>();
    private static final Set<UUID> nonVoters = new LinkedHashSet<>();
    private static List<UUID> tieLeaders = new ArrayList<>();

    private static UUID eliminated = null;
    private static String lastResult = ""; // eliminated, tie, noValid

    private ConseilManager() {}

    // ------------------------------------------------------------ cycle de vie

    /** Anciens conseil.json : les mots en dur deviennent des termes du vocabulaire (vocabulary.json). */
    private static String vocab(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("collier d'immunité", "{v_collier}").replace("Collier d'immunité", "{v_collier}")
                .replace("totem d'immunité", "{v_totem}").replace("TOTEM D'IMMUNITÉ", "{V_totem}")
                .replace("UTILISER LE COLLIER", "UTILISER {V_le_collier}")
                .replace("ton collier", "{v_le_collier}").replace("CONSEIL", "{V_conseil}")
                .replace("prochain conseil", "prochain {v_conseil}");
    }

    private static void migrateVocabulary(ConseilConfig cfg) {
        cfg.buttonCollierYes = vocab(cfg.buttonCollierYes);
        if (cfg.messages != null) {
            for (GameConfig.Msg msg : cfg.messages.values()) {
                msg.text = vocab(msg.text);
                msg.subtitle = vocab(msg.subtitle);
            }
        }
    }

    public static void load() {
        config = KLFiles.load("conseil.json", ConseilConfig.class, ConseilConfig::new);
        migrateVocabulary(config);
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
        restoreState();
    }

    /** Arret du serveur (ou fermeture du monde solo) : l'etat du conseil est sauvegarde et repris au prochain demarrage. */
    public static void shutdown() {
        if (server != null && phase != Phase.IDLE) {
            saveState();
            resetMemory();
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

    public static boolean isTotemProtected(UUID id) {
        return totemProtected.contains(id);
    }

    /** Ce joueur peut-il etre vote ce tour-ci (second vote : seulement les joueurs a egalite) ? */
    public static boolean isTargetAllowed(UUID id) {
        return restrictTargets.isEmpty() || restrictTargets.contains(id);
    }

    /** Ce joueur a-t-il le droit de voter ce tour-ci ? */
    public static boolean canVote(UUID id) {
        return candidates.containsKey(id) && !nonVoters.contains(id);
    }

    private static int expectedVoters() {
        int n = 0;
        for (UUID id : candidates.keySet()) {
            if (!nonVoters.contains(id)) {
                n++;
            }
        }
        return n;
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
            case IDLE -> "{v_conseil} : rien en cours";
            case VOTING -> "vote en cours";
            case CLOSED -> "vote clos";
            case COLLIER -> "choix {v_du_collier}";
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
            return "Il y a déjà {v_un_conseil} en cours (équipe " + teamName() + "). Utilise /ccis council cancel pour l'annuler.";
        }
        TeamManager.KLTeam t = TeamManager.get(team);
        if (t == null) {
            return "Équipe inconnue : " + team + " (équipes : " + String.join(", ", TeamManager.names()) + ")";
        }
        SpawnManager.Spawn council = SpawnManager.get("conseil");
        if (council == null) {
            return "Spawn {v_du_conseil} manquant : place-toi à l'endroit voulu et tape /ccis spawn set conseil.";
        }
        if (SpawnManager.get("elimination") == null) {
            return "Spawn {v_du_elimination} manquant : place-toi à l'endroit voulu et tape /ccis spawn set elimination.";
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
        totemProtected.clear();
        for (UUID id : candidates.keySet()) {
            ServerPlayer p = online(id);
            if (p != null && TotemManager.has(p)) {
                totemProtected.add(id);
                immune.add(id);
            }
        }
        startVoting();
        for (UUID id : totemProtected) {
            for (ServerPlayer p : audience()) {
                ConseilMessages.send(config.message("totemProtected"), p, Map.of("nom", candidates.get(id)));
            }
        }
        notifyStaff("{v_conseil} lancé pour l'équipe &f" + t.name + "&7 (&f" + candidates.size()
                + "&7 participants). Suis le déroulé avec &f/ccis council menu&7.");
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
                if (nonVoters.contains(id)) {
                    ConseilMessages.send(config.message("tieNoVote"), p, Map.of("equipe", teamName()));
                } else {
                    ConseilMessages.send(config.message("voteStart"), p, Map.of("equipe", teamName()));
                    ConseilMenus.openVote(p);
                }
            }
        }
        castFakeVotes();
        updateBar();
        saveState();
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
            if (nonVoters.contains(fake)) {
                continue;
            }
            List<UUID> options = new ArrayList<>();
            for (UUID id : all) {
                if (isTargetAllowed(id) && (config.allowSelfVote || !id.equals(fake))) {
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
            return "Tu ne participes pas à {v_au_conseil}.";
        }
        if (nonVoters.contains(id)) {
            return "Tu es à égalité : tu ne votes pas à ce tour.";
        }
        if (!candidates.containsKey(target)) {
            return "Ce joueur ne peut pas être voté.";
        }
        if (!isTargetAllowed(target)) {
            return "Seuls les joueurs à égalité peuvent être votés à ce tour.";
        }
        if (id.equals(target) && !config.allowSelfVote) {
            return "Tu ne peux pas voter pour toi-même.";
        }
        if (totemProtected.contains(target)) {
            return "Ce joueur est protégé par un {v_totem} : il ne peut pas être voté.";
        }
        boolean first = !votes.containsKey(id);
        votes.put(id, target);
        ConseilMessages.send(config.message("voteRecap"), voter, Map.of("cible", candidates.get(target)));
        notifyStaff("&f" + voter.getGameProfile().getName() + "&7 a voté (&f" + votes.size() + "&7/&f"
                + expectedVoters() + "&7)" + (first ? "" : " &8(vote modifié)"));
        if (votes.size() >= expectedVoters()) {
            notifyStaff("Tout le monde a voté : clos le vote avec &f/ccis council vote close&7 ou le menu.");
        }
        saveState();
        refreshAdminMenus();
        return null;
    }

    /** Cloture le vote (aucun vote supplementaire possible). */
    public static String closeVote() {
        if (phase != Phase.VOTING) {
            return phase == Phase.IDLE ? "{v_conseil} : rien en cours." : "Le vote est déjà clos.";
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
                + "&7 votes reçus. Lance &f/ccis council reveal&7 : les porteurs {v_du_collier} seront interrogés avant le dépouillement.");
        saveState();
        refreshAdminMenus();
        return null;
    }

    // ------------------------------------------------------------ immunite

    /** Active ou retire l'immunite d'un joueur (possible avant le debut du depouillement). */
    public static String toggleImmune(UUID id) {
        if (phase == Phase.IDLE) {
            return "{v_conseil} : rien en cours.";
        }
        if (phase != Phase.VOTING && phase != Phase.CLOSED) {
            return "Trop tard : le dépouillement a commencé.";
        }
        if (!candidates.containsKey(id)) {
            return "Ce joueur ne fait pas partie {v_du_conseil}.";
        }
        if (totemProtected.contains(id)) {
            return "Ce joueur est protégé par {v_un_totem} : son immunité ne se modifie pas.";
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
        saveState();
        refreshAdminMenus();
        return null;
    }

    // ------------------------------------------------------------ depouillement

    public static String reveal() {
        if (phase == Phase.IDLE) {
            return "{v_conseil} : rien en cours.";
        }
        if (phase == Phase.VOTING) {
            return "Clos d'abord le vote (/ccis council vote close).";
        }
        if (phase == Phase.COLLIER) {
            collierPending.clear(); // l'organisateur passe l'etape du collier
            beginReveal();
            return null;
        }
        if (phase != Phase.CLOSED) {
            return "Le dépouillement a déjà eu lieu (utilise /ccis council revote ou /ccis council close).";
        }
        if (config.collierPromptSeconds > 0 && startCollierPhase()) {
            return null;
        }
        beginReveal();
        return null;
    }

    /** Lance le depouillement. Tous les votes sont comptes ; un immunise ne peut simplement pas etre elimine. */
    private static void beginReveal() {
        List<RevealItem> items = new ArrayList<>();
        for (Map.Entry<UUID, UUID> e : votes.entrySet()) {
            items.add(new RevealItem(e.getKey(), e.getValue(), true));
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
        saveState();
        refreshAdminMenus();
    }

    // ------------------------------------------------------------ collier : choix avant le depouillement

    /** Interroge chaque porteur de collier (en ligne). Retourne false s'il n'y en a aucun. */
    private static boolean startCollierPhase() {
        collierPending.clear();
        collierUsed = false;
        for (UUID id : candidates.keySet()) {
            ServerPlayer p = online(id);
            if (p != null && !immune.contains(id) && TotemManager.hasCollier(p)) {
                collierPending.add(id);
            }
        }
        if (collierPending.isEmpty()) {
            return false;
        }
        phase = Phase.COLLIER;
        collierTicks = config.collierPromptSeconds * 20;
        for (UUID id : collierPending) {
            ServerPlayer p = online(id);
            if (p == null) {
                continue;
            }
            Component yes = Txt.of("&a&l" + config.buttonCollierYes).withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, fr.cosmicisland.CommandAliases.cmd("/ccis bracelet yes")))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Txt.of("&7Tu révèles ton immunité : les votes contre toi ne pourront pas t'éliminer."))));
            Component no = Txt.of("&c&l" + config.buttonCollierNo).withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, fr.cosmicisland.CommandAliases.cmd("/ccis bracelet no")))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Txt.of("&7Tu gardes {v_le_collier}."))));
            ConseilMessages.send(config.message("collierPrompt"), p, Map.of());
            p.sendSystemMessage(Component.empty().append(yes).append(Txt.lit("   ")).append(no));
        }
        notifyStaff("Porteurs de collier interrogés (&f" + collierPending.size() + "&7) : le dépouillement commence "
                + "après leur réponse (max &f" + config.collierPromptSeconds + "s&7). Relance &f/ccis council reveal&7 pour passer.");
        saveState();
        refreshAdminMenus();
        return true;
    }

    /** Reponse d'un joueur au choix du collier (clic dans le chat). */
    public static String answerCollier(ServerPlayer player, boolean use) {
        UUID id = player.getUUID();
        if (phase != Phase.COLLIER || !collierPending.contains(id)) {
            return "Aucun choix en attente pour toi.";
        }
        collierPending.remove(id);
        String name = candidates.getOrDefault(id, player.getGameProfile().getName());
        if (use) {
            if (config.collierConsume && !TotemManager.consumeCollier(player)) {
                return "Tu n'as plus de {v_collier}.";
            }
            immune.add(id);
            collierUsed = true;
            for (ServerPlayer p : audience()) {
                ConseilMessages.send(config.message("collierRevealed"), p, Map.of("nom", name));
            }
            notifyStaff("&f" + name + "&7 utilise {v_le_collier} : &eimmunisé&7.");
            if (fakes.isEmpty()) {
                fr.cosmicisland.SeasonHistory.log("collier", name + " utilise {v_le_collier} et devient immunisé.");
            }
        } else {
            ConseilMessages.send(config.message("collierKept"), player, Map.of());
            notifyStaff("&f" + name + "&7 garde {v_le_collier}.");
        }
        if (collierPending.isEmpty()) {
            // laisse le temps de lire l'annonce avant le titre du depouillement
            collierTicks = collierUsed ? 100 : 1;
        }
        saveState();
        refreshAdminMenus();
        return null;
    }

    private static void tickCollier() {
        if (collierTicks > 0) {
            collierTicks--;
        }
        if (collierTicks <= 0) {
            if (!collierPending.isEmpty()) {
                notifyStaff("Temps écoulé : &f" + collierPending.size() + "&7 porteur(s) {v_du_collier} n'ont pas répondu (collier gardé).");
                collierPending.clear();
            }
            beginReveal();
        }
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
        GameConfig.Msg msg = config.message(immune.contains(item.target()) ? "revealImmune" : "revealVote");
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
        int overall = 0;
        UUID overallLeader = null;
        for (Map.Entry<UUID, Integer> e : tally.entrySet()) {
            if (e.getValue() > overall) {
                overall = e.getValue();
                overallLeader = e.getKey();
            }
            if (immune.contains(e.getKey())) {
                continue; // un immunise compte les votes mais ne peut pas etre elimine
            }
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
            tieLeaders = new ArrayList<>(leaders);
        }
        phase = Phase.REVEALED;
        recordRound(false);
        if (lastResult.equals("tie") && fakes.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (UUID l : leaders) {
                names.add(candidates.getOrDefault(l, "?"));
            }
            fr.cosmicisland.SeasonHistory.log("egalite", "Égalité au conseil de l'équipe " + teamName() + " : " + String.join(", ", names) + ".");
        }
        if (overallLeader != null && immune.contains(overallLeader) && eliminated != null) {
            notifyStaff("&f" + candidates.getOrDefault(overallLeader, "?") + "&7 avait le plus de votes (" + overall
                    + ") mais est immunisé : le suivant est éliminé.");
        }
        saveState();

        String key = lastResult;
        String name = eliminated == null ? "" : candidates.getOrDefault(eliminated, "?");
        Map<String, String> vars = Map.of("cible", name, "equipe", teamName());
        for (ServerPlayer p : audience()) {
            ConseilMessages.send(config.message(key), p, vars);
        }
        if (eliminated != null) {
            notifyStaff("Résultat : &c" + name + "&7 est éliminé. Laisse-lui le temps de son discours, puis "
                    + "valide avec &f/ccis council close&7 (ou le menu).");
        } else {
            notifyStaff("Résultat : " + (lastResult.equals("tie") ? "&eégalité" : "&eaucun vote valide")
                    + "&7. Lance un nouveau vote avec &f/ccis council revote&7 (ou le menu).");
        }
        refreshAdminMenus();
    }

    // ------------------------------------------------------------ nouveau vote

    public static String revote() {
        if (phase == Phase.IDLE) {
            return "{v_conseil} : rien en cours.";
        }
        if (phase != Phase.CLOSED && phase != Phase.REVEALED) {
            return "Impossible pendant le vote ou le dépouillement.";
        }
        boolean tie = phase == Phase.REVEALED && lastResult.equals("tie") && tieLeaders.size() > 1;
        if (phase == Phase.CLOSED) {
            recordRound(true); // tour ecarte avant depouillement : on garde une trace dans le log
        }
        restrictTargets.clear();
        nonVoters.clear();
        if (tie) {
            // second vote : seulement entre les joueurs a egalite, qui ne votent pas ; en boucle tant qu'il y a egalite
            restrictTargets.addAll(tieLeaders);
            nonVoters.addAll(tieLeaders);
        }
        round++;
        startVoting();
        if (tie) {
            List<String> names = new ArrayList<>();
            for (UUID id : restrictTargets) {
                names.add(candidates.getOrDefault(id, "?"));
            }
            for (ServerPlayer p : audience()) {
                ConseilMessages.send(config.message("tieVote"), p, Map.of("noms", String.join(", ", names), "equipe", teamName()));
            }
            notifyStaff("Second vote (tour &f" + round + "&7) : seuls &f" + String.join(", ", names)
                    + "&7 sont votables, et ils ne votent pas. Les immunités sont conservées pour ce {v_conseil}.");
        } else {
            notifyStaff("Nouveau vote lancé (tour &f" + round + "&7). Les immunités sont conservées.");
        }
        return null;
    }

    // ------------------------------------------------------------ cloture

    /** Valide le resultat : l'elimine part (spectateur + point d'elimination), les autres retournent au camp. */
    public static String close() {
        if (phase == Phase.IDLE) {
            return "{v_conseil} : rien en cours.";
        }
        if (phase != Phase.REVEALED) {
            return "Le dépouillement n'est pas terminé (étapes : vote close, reveal, puis close).";
        }
        if (eliminated == null) {
            return "Pas d'éliminé (égalité ou aucun vote valide) : lance un nouveau vote avec /ccis council revote.";
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
        if (!test && config.totemConsumeAtClose) {
            for (UUID id : totemProtected) {
                ServerPlayer holder = online(id);
                if (holder != null) {
                    TotemManager.consume(holder);
                }
            }
        }
        if (!test) {
            int got = tally.getOrDefault(out, 0);
            fr.cosmicisland.SeasonHistory.log("elimination", plainName(outName) + " est éliminé (" + got + " vote" + (got > 1 ? "s" : "")
                    + ") au conseil de l'équipe " + teamName() + ".");
        }
        writeLog("closed", outName);
        notifyStaff("{v_conseil} clos : &f" + outName + (test
                ? "&7 est éliminé (TEST : aucun rôle modifié)."
                : "&7 est éliminé (passé en spectateur)."));
        reset();
        return null;
    }

    public static String cancel() {
        if (phase == Phase.IDLE) {
            return "{v_conseil} : rien en cours.";
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
        notifyStaff("{v_conseil} annulé.");
        reset();
        return null;
    }

    private static void reset() {
        resetMemory();
        deleteState();
    }

    private static void resetMemory() {
        sendTally(false);
        collierPending.clear();
        totemProtected.clear();
        collierTicks = 0;
        collierUsed = false;
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
        restrictTargets.clear();
        nonVoters.clear();
        tieLeaders = new ArrayList<>();
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
        if (phase == Phase.COLLIER) {
            tickCollier();
            return;
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
                o.addProperty("cible_immunisee", immune.contains(item.target()));
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

    // ------------------------------------------------------------ sauvegarde de l'etat (redemarrage serveur / monde solo)

    private static class SavedState {
        String phase = "IDLE";
        String teamId;
        String startedAt = "";
        int round = 1;
        int voteTicksLeft = -1;
        int voteTicksTotal = -1;
        String eliminated;
        String lastResult = "";
        Map<String, String> candidates = new LinkedHashMap<>();
        Map<String, String> votes = new LinkedHashMap<>();
        List<String> immune = new ArrayList<>();
        List<String> totem = new ArrayList<>();
        List<String> restrict = new ArrayList<>();
        List<String> nonVoters = new ArrayList<>();
        List<String> tieLeaders = new ArrayList<>();
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<String> rounds = new ArrayList<>();
    }

    private static final String STATE_FILE = "state/conseil.json";

    private static java.nio.file.Path stateFile() {
        return KLFiles.dir().resolve(STATE_FILE);
    }

    private static void deleteState() {
        try {
            java.nio.file.Files.deleteIfExists(stateFile());
        } catch (java.io.IOException ignored) {
            // pas bloquant
        }
    }

    /** Ecrit l'etat courant. Les conseils de test (joueurs fictifs) ne sont jamais sauvegardes. */
    private static void saveState() {
        if (phase == Phase.IDLE || !fakes.isEmpty() || server == null) {
            return;
        }
        SavedState st = new SavedState();
        // le depouillement recommence a zero apres un redemarrage
        st.phase = (phase == Phase.COLLIER || phase == Phase.REVEALING) ? Phase.CLOSED.name() : phase.name();
        st.teamId = teamId;
        st.startedAt = startedAt;
        st.round = round;
        st.voteTicksLeft = voteTicksLeft;
        st.voteTicksTotal = voteTicksTotal;
        st.eliminated = eliminated == null ? null : eliminated.toString();
        st.lastResult = lastResult;
        for (Map.Entry<UUID, String> e : candidates.entrySet()) {
            st.candidates.put(e.getKey().toString(), e.getValue());
        }
        for (Map.Entry<UUID, UUID> e : votes.entrySet()) {
            st.votes.put(e.getKey().toString(), e.getValue().toString());
        }
        for (UUID id : immune) {
            st.immune.add(id.toString());
        }
        for (UUID id : totemProtected) {
            st.totem.add(id.toString());
        }
        for (UUID id : restrictTargets) {
            st.restrict.add(id.toString());
        }
        for (UUID id : nonVoters) {
            st.nonVoters.add(id.toString());
        }
        for (UUID id : tieLeaders) {
            st.tieLeaders.add(id.toString());
        }
        if (phase == Phase.REVEALED) {
            for (Map.Entry<UUID, Integer> e : tally.entrySet()) {
                st.tally.put(e.getKey().toString(), e.getValue());
            }
        }
        for (JsonObject r : rounds) {
            st.rounds.add(r.toString());
        }
        KLFiles.save(STATE_FILE, st);
    }

    private static void restoreState() {
        if (!java.nio.file.Files.exists(stateFile())) {
            return;
        }
        SavedState st = KLFiles.load(STATE_FILE, SavedState.class, SavedState::new);
        try {
            Phase restored = Phase.valueOf(st.phase);
            if (restored == Phase.IDLE || st.candidates.isEmpty()) {
                return;
            }
            resetMemory();
            phase = restored;
            teamId = st.teamId;
            startedAt = st.startedAt == null ? "" : st.startedAt;
            round = Math.max(1, st.round);
            voteTicksLeft = st.voteTicksLeft;
            voteTicksTotal = st.voteTicksTotal;
            lastResult = st.lastResult == null ? "" : st.lastResult;
            eliminated = st.eliminated == null ? null : UUID.fromString(st.eliminated);
            for (Map.Entry<String, String> e : st.candidates.entrySet()) {
                candidates.put(UUID.fromString(e.getKey()), e.getValue());
            }
            for (Map.Entry<String, String> e : st.votes.entrySet()) {
                votes.put(UUID.fromString(e.getKey()), UUID.fromString(e.getValue()));
            }
            for (String id : st.immune) {
                immune.add(UUID.fromString(id));
            }
            for (String id : st.totem) {
                totemProtected.add(UUID.fromString(id));
            }
            for (String id : st.restrict) {
                restrictTargets.add(UUID.fromString(id));
            }
            for (String id : st.nonVoters) {
                nonVoters.add(UUID.fromString(id));
            }
            for (String id : st.tieLeaders) {
                tieLeaders.add(UUID.fromString(id));
            }
            for (Map.Entry<String, Integer> e : st.tally.entrySet()) {
                tally.put(UUID.fromString(e.getKey()), e.getValue());
            }
            for (String r : st.rounds) {
                rounds.add(com.google.gson.JsonParser.parseString(r).getAsJsonObject());
            }
            CosmicIsland.LOGGER.info("Conseil restaure apres redemarrage : equipe {}, phase {}", teamId, phase);
        } catch (Exception e) {
            CosmicIsland.LOGGER.error("Etat du conseil illisible : ignore", e);
            resetMemory();
        }
    }

    /** Connexion d'un joueur : reprise du conseil restaure (vote, barre, decompte) ou information du staff. */
    public static void onLogin(ServerPlayer player) {
        if (phase == Phase.IDLE) {
            return;
        }
        if (KLPermissions.levelOf(player) >= KLPermissions.ORGANISATEUR) {
            player.sendSystemMessage(Component.empty()
                    .append(Txt.lit("[{v_conseil}] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Txt.of("&7{v_conseil} en cours (équipe &f" + teamName() + "&7, " + phaseLabel()
                            + "). Suis-le avec &f/ccis council menu&7.")));
        }
        if (candidates.containsKey(player.getUUID())) {
            if (phase == Phase.VOTING) {
                ConseilMessages.send(config.message("voteStart"), player, Map.of("equipe", teamName()));
            }
            if (phase == Phase.REVEALED) {
                sendTally(true);
            }
        }
    }

    // ------------------------------------------------------------ utilitaires

    private static ServerPlayer online(UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static String plainName(String s) {
        return s == null ? "?" : s;
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
                .append(Txt.lit("[{v_conseil}] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
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
        out.add(Txt.lit("=== {v_conseil} ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (phase == Phase.IDLE) {
            out.add(Txt.lit("{v_conseil} : rien en cours.").withStyle(ChatFormatting.GRAY));
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
                .append(Txt.lit(label + " : ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
                .append(Txt.lit(value).withStyle(ChatFormatting.WHITE));
    }
}
