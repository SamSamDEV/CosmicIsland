package fr.cosmicisland.game;

import fr.cosmicisland.KLFiles;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Points de spawn des equipes (rouge, jaune, blanche...), stockes dans config/cosmicisland/spawns.json. */
public final class SpawnManager {

    public static class Spawn {
        public String dimension = "minecraft:overworld";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
    }

    private static class Store {
        Map<String, Spawn> spawns = new LinkedHashMap<>();
    }

    private static Store store = new Store();

    private SpawnManager() {}

    public static void load() {
        store = KLFiles.load("spawns.json", Store.class, Store::new);
    }

    private static void save() {
        KLFiles.save("spawns.json", store);
    }

    public static Spawn get(String teamId) {
        return store.spawns.get(teamId.toLowerCase());
    }

    public static void set(String teamId, ServerPlayer player) {
        Spawn spawn = new Spawn();
        spawn.dimension = player.level().dimension().location().toString();
        spawn.x = player.getX();
        spawn.y = player.getY();
        spawn.z = player.getZ();
        spawn.yaw = player.getYRot();
        spawn.pitch = player.getXRot();
        store.spawns.put(teamId.toLowerCase(), spawn);
        save();
    }

    /** Monde correspondant a un nom de dimension ("minecraft:overworld"), ou null s'il est introuvable. */
    public static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static boolean teleport(MinecraftServer server, ServerPlayer player, Spawn spawn) {
        ServerLevel level = level(server, spawn.dimension);
        if (level == null) {
            return false;
        }
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
        return true;
    }
}
