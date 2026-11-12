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
}
