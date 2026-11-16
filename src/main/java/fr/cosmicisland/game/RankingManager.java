package fr.cosmicisland.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.KLFiles;
import fr.cosmicisland.RoleManager;
import fr.cosmicisland.TeamManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classement des victoires (config/cosmicisland/ranking.json) et logs detailles par jeu
 * (config/cosmicisland/logs/log_result_<jeu>.json).
 */
public final class RankingManager {

    public static class PlayerStats {
        public String name = "";
        public int wins = 0;
        public int played = 0;
        public Map<String, Integer> perGame = new LinkedHashMap<>();
    }

    private static class Store {
        Map<String, PlayerStats> players = new LinkedHashMap<>(); // uuid -> stats
    }

    private static class LogStore {
        List<JsonObject> results = new ArrayList<>();
    }

    public record Row(UUID id, String name, int wins) {}

    public record ResetResult(String target, String backup) {}

    private static Store store = new Store();

    private static String pendingTarget = null;
    private static String pendingBy = null;
    private static long pendingUntil = 0L;

    private RankingManager() {}

    // ------------------------------------------------------------ fichier

    public static void load() {
        store = KLFiles.load("ranking.json", Store.class, Store::new);
    }

    private static void save() {
        KLFiles.save("ranking.json", store);
    }

    private static PlayerStats stats(UUID id, String name) {
        PlayerStats s = store.players.computeIfAbsent(id.toString(), k -> new PlayerStats());
        if (name != null && !name.isEmpty()) {
            s.name = name;
        }
        return s;
    }

    public static PlayerStats statsOf(UUID id) {
        return store.players.get(id.toString());
    }

    // ------------------------------------------------------------ consultation

    /** Victoires d'un joueur : total (gameId = null) ou pour un jeu. */
    public static int wins(UUID id, String gameId) {
        PlayerStats s = statsOf(id);
        if (s == null) {
            return 0;
        }
        return gameId == null ? s.wins : s.perGame.getOrDefault(gameId.toLowerCase(), 0);
    }

    /** Classement : tous les participants enregistres (meme a 0 victoire) + ceux deja au classement. */
    public static List<Row> top(String gameId, int limit) {
        String game = gameId == null ? null : gameId.toLowerCase();
        Map<UUID, Row> rows = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerStats> e : store.players.entrySet()) {
            UUID id;
            try {
                id = UUID.fromString(e.getKey());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            PlayerStats s = e.getValue();
            int wins = game == null ? s.wins : s.perGame.getOrDefault(game, 0);
            rows.put(id, new Row(id, s.name, wins));
        }
        for (Map.Entry<UUID, String> e : RoleManager.participants().entrySet()) {
            rows.putIfAbsent(e.getKey(), new Row(e.getKey(), e.getValue(), 0));
        }
        List<Row> list = new ArrayList<>(rows.values());
        list.sort(Comparator.comparingInt(Row::wins).reversed()
                .thenComparing(r -> r.name() == null ? "" : r.name().toLowerCase()));
        if (limit > 0 && list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    // ------------------------------------------------------------ enregistrement d'une epreuve

    /**
     * Enregistre la fin d'une epreuve : victoires (sauf si annulee) et entree dans le log du jeu.
     * participants = tous les joueurs qui ont rejoint l'epreuve (uuid -> pseudo).
     */
    public static void record(String gameId, GameConfig cfg, GameResult result, boolean cancelled,
                              int durationSeconds, Map<UUID, String> participants) {
        String game = gameId.toLowerCase();
        if (!cancelled) {
            for (Map.Entry<UUID, String> e : participants.entrySet()) {
                stats(e.getKey(), e.getValue()).played++;
            }
            if (result != null) {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (UUID id : result.winners) {
                    names.add(participants.getOrDefault(id, "?"));
                }
                if (!names.isEmpty()) {
                    String who = result.teamMode && result.winnerTeam != null ? "l'équipe " + result.winnerTeam : String.join(", ", names);
                    fr.cosmicisland.SeasonHistory.log("epreuve", cfg.displayName + " : victoire de " + who + ".");
                }
                for (UUID id : result.winners) {
                    PlayerStats s = stats(id, participants.get(id));
                    s.wins++;
                    s.perGame.merge(game, 1, Integer::sum);
                }
            }
            save();
        }
        writeLog(game, cfg, result, cancelled, durationSeconds, participants);
    }

    private static void writeLog(String game, GameConfig cfg, GameResult result, boolean cancelled,
                                 int durationSeconds, Map<UUID, String> participants) {
        JsonObject o = new JsonObject();
        o.addProperty("date", LocalDateTime.now().withNano(0).toString());
        o.addProperty("game", game);
        o.addProperty("displayName", cfg.displayName);
        o.addProperty("cancelled", cancelled);
        o.addProperty("durationSeconds", durationSeconds);
        o.addProperty("teamMode", result != null && result.teamMode);
        if (result != null && result.winnerTeam != null) {
            o.addProperty("winnerTeam", result.winnerTeam);
        }

        JsonArray winners = new JsonArray();
        if (!cancelled && result != null) {
            for (UUID id : result.winners) {
                winners.add(participants.getOrDefault(id, id.toString()));
            }
        }
        o.add("winners", winners);

        JsonArray parts = new JsonArray();
        for (Map.Entry<UUID, String> e : participants.entrySet()) {
            JsonObject p = new JsonObject();
            p.addProperty("player", e.getValue());
            String team = TeamManager.teamIdOf(e.getKey());
            p.addProperty("team", team == null ? "" : team);
            parts.add(p);
        }
        o.add("participants", parts);

        if (result != null && !result.placements.isEmpty()) {
            JsonArray ranking = new JsonArray();
            int rank = 1;
            for (UUID id : result.placements) {
                JsonObject r = new JsonObject();
                r.addProperty("rank", rank++);
                r.addProperty("player", participants.getOrDefault(id, id.toString()));
                String team = TeamManager.teamIdOf(id);
                r.addProperty("team", team == null ? "" : team);
                ranking.add(r);
            }
            o.add("ranking", ranking);
        }
        if (result != null) {
            o.add("details", result.details);
        }

        String file = "logs/log_result_" + game + ".json";
        LogStore log = KLFiles.load(file, LogStore.class, LogStore::new);
        log.results.add(o);
        KLFiles.save(file, log);
    }

    // ------------------------------------------------------------ corrections manuelles

    /** Ajoute (ou retire, si n est negatif) des victoires a un joueur pour un jeu. */
    public static void add(GameProfile profile, String gameId, int n) {
        String game = gameId.toLowerCase();
        PlayerStats s = stats(profile.getId(), profile.getName());
        int current = s.perGame.getOrDefault(game, 0);
        int next = Math.max(0, current + n);
        s.wins = Math.max(0, s.wins + (next - current));
        if (next == 0) {
            s.perGame.remove(game);
        } else {
            s.perGame.put(game, next);
        }
        save();
    }

    // ------------------------------------------------------------ remise a zero (avec confirmation)

    /** Demande une remise a zero ; elle doit etre confirmee dans les 30 secondes. */
    public static void requestReset(String by, String target) {
        pendingBy = by;
        pendingTarget = target.toLowerCase();
        pendingUntil = System.currentTimeMillis() + 30_000L;
    }

    /** Confirme la remise a zero en attente. Retourne null si rien n'est en attente ou si le delai est depasse. */
    public static ResetResult confirmReset(String by) {
        if (pendingTarget == null || pendingBy == null || !pendingBy.equals(by)
                || System.currentTimeMillis() > pendingUntil) {
            pendingTarget = null;
            pendingBy = null;
            return null;
        }
        String target = pendingTarget;
        pendingTarget = null;
        pendingBy = null;

        String backup = backup();
        if (target.equals("all")) {
            for (PlayerStats s : store.players.values()) {
                s.wins = 0;
                s.played = 0;
                s.perGame.clear();
            }
        } else {
            for (PlayerStats s : store.players.values()) {
                int inGame = s.perGame.getOrDefault(target, 0);
                s.wins = Math.max(0, s.wins - inGame);
                s.perGame.remove(target);
            }
        }
        save();
        return new ResetResult(target, backup);
    }

    /** Copie de securite de ranking.json dans backups/. Retourne le chemin relatif ou null. */
    private static String backup() {
        save();
        Path source = KLFiles.dir().resolve("ranking.json");
        if (!Files.exists(source)) {
            return null;
        }
        String name = "ranking_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
        Path dir = KLFiles.dir().resolve("backups");
        try {
            Files.createDirectories(dir);
            Files.copy(source, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            return "backups/" + name;
        } catch (IOException e) {
            CosmicIsland.LOGGER.error("Impossible de sauvegarder le classement avant la remise a zero", e);
            return null;
        }
    }
}
