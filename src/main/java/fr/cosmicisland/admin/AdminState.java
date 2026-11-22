package fr.cosmicisland.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cosmicisland.FinaleManager;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.Lang;
import fr.cosmicisland.RoleManager;
import fr.cosmicisland.SeasonHistory;
import fr.cosmicisland.SeasonManager;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.game.GameRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Photo de l'etat du jeu envoyee a la fenetre d'administration (JSON). */
public final class AdminState {
    private AdminState() {}

    public static String build(ServerPlayer viewer) {
        JsonObject o = new JsonObject();
        o.addProperty("level", KLPermissions.levelOf(viewer));
        o.addProperty("lang", Lang.effective());
        o.add("season", SeasonManager.snapshot());
        o.add("finale", FinaleManager.snapshot());
        o.addProperty("merged", TeamManager.isMerged());

        JsonArray teams = new JsonArray();
        for (java.util.Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            TeamManager.KLTeam t = e.getValue();
            JsonObject to = new JsonObject();
            to.addProperty("id", e.getKey());
            to.addProperty("name", t.name);
            to.addProperty("color", t.color);
            JsonArray members = new JsonArray();
            for (String n : t.members.values()) {
                members.add(n);
            }
            to.add("members", members);
            teams.add(to);
        }
        o.add("teams", teams);

        JsonArray players = new JsonArray();
        Set<String> seen = new HashSet<>();
        MinecraftServer server = viewer.getServer();
        Set<String> online = new HashSet<>();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                online.add(p.getUUID().toString());
            }
        }
        for (String[] r : RoleManager.registered()) {
            seen.add(r[2]);
            players.add(playerJson(r[0], r[1], r[2], online.contains(r[2])));
        }
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String id = p.getUUID().toString();
                if (!seen.contains(id)) {
                    players.add(playerJson(p.getGameProfile().getName(), RoleManager.effective(p).id(), id, true));
                }
            }
        }
        o.add("players", players);

        JsonArray games = new JsonArray();
        for (String g : GameRegistry.ids()) {
            games.add(g);
        }
        o.add("games", games);

        JsonArray langs = new JsonArray();
        for (String l : Lang.available()) {
            langs.add(l);
        }
        o.add("langs", langs);

        JsonArray archives = new JsonArray();
        for (String a : SeasonHistory.archives()) {
            archives.add(a);
        }
        o.add("archives", archives);
        return o.toString();
    }

    private static JsonObject playerJson(String name, String role, String uuid, boolean online) {
        JsonObject p = new JsonObject();
        p.addProperty("n", name);
        p.addProperty("r", role);
        String team = "";
        try {
            String id = TeamManager.teamIdOf(UUID.fromString(uuid));
            team = id == null ? "" : id;
        } catch (IllegalArgumentException ignored) {
            // uuid invalide : pas d'equipe
        }
        p.addProperty("t", team);
        p.addProperty("on", online);
        return p;
    }
}
