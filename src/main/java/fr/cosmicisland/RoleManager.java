package fr.cosmicisland;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Roles stockes dans config/cosmicisland/players.json.
 * Un joueur sans role enregistre est spectateur (ou organisateur s'il est staff).
 */
public final class RoleManager {
    private static class Entry {
        String name;
        KLRole role;
    }

    private static class Store {
        Map<String, Entry> players = new LinkedHashMap<>();
    }

    private static Store store = new Store();

    private RoleManager() {}

    public static void load() {
        store = KLFiles.load("players.json", Store.class, Store::new);
    }

    private static void save() {
        KLFiles.save("players.json", store);
    }

    /** Role explicitement enregistre, ou null. */
    public static KLRole get(UUID id) {
        Entry entry = store.players.get(id.toString());
        return entry == null ? null : entry.role;
    }

    /** Role effectif, y compris la valeur par defaut. */
    public static KLRole effective(ServerPlayer player) {
        KLRole role = get(player.getUUID());
        if (role != null) {
            return role;
        }
        return KLPermissions.levelOf(player) > KLPermissions.NONE ? KLRole.ORGANISATEUR : KLRole.SPECTATEUR;
    }

    public static void set(GameProfile profile, KLRole role) {
        Entry entry = new Entry();
        entry.name = profile.getName();
        entry.role = role;
        store.players.put(profile.getId().toString(), entry);
        save();
    }

    /** Applique les consequences du role sur le joueur connecte. */
    public static void apply(ServerPlayer player, KLRole role) {
        if (role == KLRole.SPECTATEUR) {
            player.setGameMode(GameType.SPECTATOR);
        } else if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    public static void onJoin(ServerPlayer player) {
        apply(player, effective(player));
    }

    private static UUID fakeId(String name) {
        return UUID.nameUUIDFromBytes(("fake-participant-" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** TEST : cree n faux participants (Test1, Test2...). Retourne le nombre cree. */
    public static int addFakes(int n) {
        int created = 0;
        for (int i = 1; i <= n; i++) {
            String name = "Test" + i;
            UUID id = fakeId(name);
            if (!store.players.containsKey(id.toString())) {
                created++;
            }
            Entry entry = new Entry();
            entry.name = name;
            entry.role = KLRole.PARTICIPANT;
            store.players.put(id.toString(), entry);
        }
        save();
        return created;
    }

    /** TEST : supprime tous les faux participants (aussi de leur equipe). Retourne le nombre supprime. */
    public static int clearFakes() {
        int removed = 0;
        java.util.Iterator<Map.Entry<String, Entry>> it = store.players.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            if (e.getValue().name != null && e.getValue().name.matches("Test[0-9]+")
                    && e.getKey().equals(fakeId(e.getValue().name).toString())) {
                TeamManager.remove(new GameProfile(UUID.fromString(e.getKey()), e.getValue().name));
                it.remove();
                removed++;
            }
        }
        save();
        return removed;
    }

    /** Tous les joueurs enregistres : [{n: pseudo, r: role, u: uuid}] (fenetre d'administration). */
    public static java.util.List<String[]> registered() {
        java.util.List<String[]> list = new java.util.ArrayList<>();
        for (Map.Entry<String, Entry> e : store.players.entrySet()) {
            if (e.getValue().name != null && e.getValue().role != null) {
                list.add(new String[]{e.getValue().name, e.getValue().role.id(), e.getKey()});
            }
        }
        return list;
    }

    /** Joueurs enregistres avec le role participant (uuid -> pseudo). */
    public static Map<UUID, String> participants() {
        Map<UUID, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : store.players.entrySet()) {
            if (e.getValue().role == KLRole.PARTICIPANT) {
                try {
                    map.put(UUID.fromString(e.getKey()), e.getValue().name);
                } catch (IllegalArgumentException ignored) {
                    // cle invalide dans players.json : ignoree
                }
            }
        }
        return map;
    }
}
