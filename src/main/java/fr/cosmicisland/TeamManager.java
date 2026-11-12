package fr.cosmicisland;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Equipes stockees dans config/cosmicisland/teams.json.
 * Le tag [ROUGE] et la couleur sont appliques via les equipes vanilla du scoreboard
 * (tab, chat, nom au-dessus de la tete).
 */
public final class TeamManager {
    /** Equipe qui recoit tout le monde lors de la fusion. */
    public static final String MERGED_ID = "blanche";

    public static class KLTeam {
        public String name = "";
        public String tag = "";
        public String color = "white";
        public Map<String, String> members = new LinkedHashMap<>(); // uuid -> pseudo

        public KLTeam() {}

        public KLTeam(String name, String tag, ChatFormatting color) {
            this.name = name;
            this.tag = tag;
            this.color = color.getName();
        }

        public ChatFormatting format() {
            ChatFormatting f = ChatFormatting.getByName(color);
            return f == null ? ChatFormatting.WHITE : f;
        }
    }

    private static class Store {
        boolean merged = false;
        Map<String, KLTeam> teams = new LinkedHashMap<>();
    }

    private static Store store = new Store();
    private static MinecraftServer server;

    private TeamManager() {}

    private static Store defaults() {
        Store s = new Store();
        s.teams.put("rouge", new KLTeam("Rouge", "[ROUGE]", ChatFormatting.RED));
        s.teams.put("jaune", new KLTeam("Jaune", "[JAUNE]", ChatFormatting.YELLOW));
        s.teams.put("blanche", new KLTeam("Blanche", "[BLANCHE]", ChatFormatting.WHITE));
        return s;
    }

    public static void load() {
        store = KLFiles.load("teams.json", Store.class, TeamManager::defaults);
    }

    private static void save() {
        KLFiles.save("teams.json", store);
    }

    public static Set<String> ids() {
        return store.teams.keySet();
    }

    public static Map<String, KLTeam> all() {
        return store.teams;
    }

    public static KLTeam get(String id) {
        return store.teams.get(id.toLowerCase());
    }

    public static boolean isMerged() {
        return store.merged;
    }

    public static String teamIdOf(UUID playerId) {
        for (Map.Entry<String, KLTeam> e : store.teams.entrySet()) {
            if (e.getValue().members.containsKey(playerId.toString())) {
                return e.getKey();
            }
        }
        return null;
    }

    public static void assign(String teamId, GameProfile profile) {
        removeFromAll(profile.getId().toString());
        store.teams.get(teamId).members.put(profile.getId().toString(), profile.getName());
        changed();
    }

    public static void remove(GameProfile profile) {
        removeFromAll(profile.getId().toString());
        changed();
    }

    private static void removeFromAll(String uuid) {
        for (KLTeam team : store.teams.values()) {
            team.members.remove(uuid);
        }
    }

    /** Fusion : tout le monde passe dans l'equipe "blanche". Retourne false si elle n'existe pas. */
    public static boolean merge() {
        KLTeam target = store.teams.get(MERGED_ID);
        if (target == null) {
            return false;
        }
        for (KLTeam team : store.teams.values()) {
            if (team != target) {
                target.members.putAll(team.members);
                team.members.clear();
            }
        }
        store.merged = true;
        changed();
        return true;
    }

    /** A appeler apres toute modification (sauvegarde + mise a jour du scoreboard). */
    public static void changed() {
        save();
        if (server != null) {
            sync(server);
        }
    }

    /** Synchronise les equipes du mod avec les equipes vanilla du scoreboard. */
    public static void sync(MinecraftServer srv) {
        server = srv;
        Scoreboard board = srv.getScoreboard();
        for (Map.Entry<String, KLTeam> entry : store.teams.entrySet()) {
            String boardName = "kl_" + entry.getKey();
            KLTeam team = entry.getValue();

            PlayerTeam pt = board.getPlayerTeam(boardName);
            if (pt == null) {
                pt = board.addPlayerTeam(boardName);
            }
            pt.setDisplayName(Txt.of(team.name));
            pt.setPlayerPrefix(Txt.of(team.tag + " "));
            pt.setColor(team.format());

            Set<String> wanted = new HashSet<>(team.members.values());
            for (String playerName : new ArrayList<>(pt.getPlayers())) {
                if (!wanted.contains(playerName)) {
                    board.removePlayerFromTeam(playerName, pt);
                }
            }
            for (String playerName : wanted) {
                if (!pt.getPlayers().contains(playerName)) {
                    board.addPlayerToTeam(playerName, pt);
                }
            }
        }
    }
}
