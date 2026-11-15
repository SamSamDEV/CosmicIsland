package fr.cosmicisland;

import com.mojang.authlib.GameProfile;
import fr.cosmicisland.chat.ChatConfig;
import fr.cosmicisland.chat.ChatManager;
import fr.cosmicisland.conseil.ConseilMessages;
import fr.cosmicisland.game.SpawnManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
        public boolean bold = true; // tag en gras
        public boolean autoTag = true; // le tag suit le nom : [NOM] (desactive des qu'un tag est fixe a la main)
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
        boolean hideNames = true; // pseudos caches au-dessus de la tete
        KLTeam organizer = new KLTeam("Organisateur", "[ORGANISATEUR]", ChatFormatting.GOLD); // tout en bas du TAB
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

    /** Tag automatique d'un nom d'equipe : "Mars" -> "[MARS]". */
    public static String autoTagFor(String name) {
        return "[" + plain(name).toUpperCase() + "]";
    }

    /** Retire les codes couleur (&c, §c) d'un nom. */
    private static String plain(String text) {
        return text == null ? "" : text.replaceAll("[&\u00a7][0-9a-fk-orA-FK-OR]", "").trim();
    }

    /**
     * Identifiant interne (rouge, jaune...) d'une equipe a partir de son identifiant OU de son nom actuel
     * (casse ignoree, codes couleur ignores). Retourne null si rien ne correspond.
     */
    public static String resolveId(String idOrName) {
        if (idOrName == null) {
            return null;
        }
        String key = idOrName.trim().toLowerCase();
        if (store.teams.containsKey(key)) {
            return key;
        }
        for (Map.Entry<String, KLTeam> e : store.teams.entrySet()) {
            String name = plain(e.getValue().name);
            if (name.equalsIgnoreCase(idOrName.trim()) || name.replace(' ', '_').equalsIgnoreCase(idOrName.trim())) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Equipe par identifiant ou par nom actuel. */
    public static KLTeam get(String idOrName) {
        String id = resolveId(idOrName);
        return id == null ? null : store.teams.get(id);
    }

    /** Noms actuels des equipes (sans codes couleur), proposes en completion des commandes. */
    public static List<String> names() {
        List<String> list = new ArrayList<>();
        for (KLTeam t : store.teams.values()) {
            list.add(plain(t.name).replace(' ', '_'));
        }
        return list;
    }

    /** La liste contient-elle cette equipe (ses elements peuvent etre des identifiants ou des noms) ? */
    public static boolean listContains(List<String> list, String teamId) {
        for (String entry : list) {
            if (teamId.equals(resolveId(entry))) {
                return true;
            }
        }
        return false;
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
        announceFusion();
        return true;
    }

    /** Retour aux equipes separees (les joueurs ne sont pas deplaces : il faut les reassigner). */
    public static void unmerge() {
        store.merged = false;
        changed();
    }

    /** Annonce de la fusion a tous les joueurs, et rappel au staff si l'equipe de fusion n'a pas ses spawns. */
    private static void announceFusion() {
        if (server == null) {
            return;
        }
        ChatConfig cfg = ChatManager.config();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ConseilMessages.send(cfg.fusionTitle, p, Map.of());
            ConseilMessages.send(cfg.fusionChat, p, Map.of());
        }
        List<String> missing = new ArrayList<>();
        if (SpawnManager.get("base:" + MERGED_ID) == null) {
            missing.add("base (/ccis spawn base set " + MERGED_ID + ")");
        }
        if (SpawnManager.get(MERGED_ID) == null) {
            missing.add("zone de jeu (/ccis spawn set " + MERGED_ID + ")");
        }
        if (missing.isEmpty()) {
            return;
        }
        Component warning = Txt.of("&6[{v_reunification}] &7Il manque pour l'équipe " + MERGED_ID + " : &f" + String.join(", ", missing));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR) {
                p.sendSystemMessage(warning);
            }
        }
    }

    /** A appeler apres toute modification (sauvegarde + mise a jour du scoreboard). */
    public static void changed() {
        save();
        if (server != null) {
            sync(server);
        }
    }

    /** Equipe "Organisateur" (affichee tout en bas du TAB). */
    public static KLTeam organizer() {
        if (store.organizer == null) {
            store.organizer = new KLTeam("Organisateur", "[ORGANISATEUR]", ChatFormatting.GOLD);
        }
        return store.organizer;
    }

    /**
     * Synchronise les equipes du mod avec les equipes vanilla du scoreboard.
     * Le TAB trie par nom d'equipe : les equipes du scoreboard sont donc nommees kl_00_<id>, kl_01_<id>...
     * dans l'ordre de teams.json, et l'equipe des organisateurs kl_zz_orga tout en dernier.
     * Pseudos au-dessus de la tete : caches (hideNames dans teams.json).
     */
    public static void sync(MinecraftServer srv) {
        server = srv;
        Scoreboard board = srv.getScoreboard();
        Set<String> desired = new HashSet<>();
        Team.Visibility nameTags = store.hideNames ? Team.Visibility.NEVER : Team.Visibility.ALWAYS;

        int index = 0;
        for (Map.Entry<String, KLTeam> entry : store.teams.entrySet()) {
            String boardName = String.format("kl_%02d_%s", index++, entry.getKey());
            desired.add(boardName);
            applyBoardTeam(board, boardName, entry.getValue(), new HashSet<>(entry.getValue().members.values()), nameTags);
        }

        // Organisateurs : joueurs connectes au role organisateur qui ne sont dans aucune equipe de jeu
        Set<String> organizers = new HashSet<>();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (teamIdOf(p.getUUID()) == null && RoleManager.effective(p) == KLRole.ORGANISATEUR) {
                organizers.add(p.getGameProfile().getName());
            }
        }
        desired.add("kl_zz_orga");
        applyBoardTeam(board, "kl_zz_orga", organizer(), organizers, nameTags);

        // Nettoyage des anciennes equipes (ancien nommage kl_<id>, equipes supprimees)
        for (PlayerTeam old : new ArrayList<>(board.getPlayerTeams())) {
            if (old.getName().startsWith("kl_") && !desired.contains(old.getName())) {
                board.removePlayerTeam(old);
            }
        }
    }

    private static void applyBoardTeam(Scoreboard board, String boardName, KLTeam team, Set<String> wanted,
                                       Team.Visibility nameTags) {
        PlayerTeam pt = board.getPlayerTeam(boardName);
        if (pt == null) {
            pt = board.addPlayerTeam(boardName);
        }
        pt.setDisplayName(Txt.of(team.name));
        // Grade / tag d'equipe : couleur de l'equipe (en gras) ; pseudo : toujours gris clair
        MutableComponent prefix = Txt.of(team.tag).withStyle(team.format());
        if (team.bold) {
            prefix.withStyle(ChatFormatting.BOLD);
        }
        pt.setPlayerPrefix(Component.empty().append(prefix).append(Txt.lit(" ")));
        pt.setColor(ChatFormatting.GRAY);
        pt.setNameTagVisibility(nameTags);

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
