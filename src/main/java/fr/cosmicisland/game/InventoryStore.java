package fr.cosmicisland.game;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.KLFiles;
import fr.cosmicisland.RoleManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Sauvegarde l'inventaire, la position et le mode de jeu d'un joueur avant une epreuve,
 * dans config/cosmicisland/saved/<uuid>.nbt. Le fichier survit a un crash du serveur :
 * a la reconnexion, le joueur retrouve ses affaires.
 */
public final class InventoryStore {

    private InventoryStore() {}

    private static Path file(UUID id) {
        return KLFiles.dir().resolve("saved").resolve(id + ".nbt");
    }

    public static boolean has(UUID id) {
        return Files.exists(file(id));
    }

    /** Sauvegarde le joueur. N'ecrase JAMAIS une sauvegarde existante (elle contient les vraies affaires). */
    public static void save(ServerPlayer player) {
        Path path = file(player.getUUID());
        if (Files.exists(path)) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        ListTag inventory = new ListTag();
        player.getInventory().save(inventory);
        tag.put("inventory", inventory);
        tag.putString("dimension", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yRot", player.getYRot());
        tag.putFloat("xRot", player.getXRot());
        tag.putString("gameMode", player.gameMode.getGameModeForPlayer().getName());
        try {
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(tag, path.toFile());
        } catch (IOException e) {
            CosmicIsland.LOGGER.error("Impossible de sauvegarder l'inventaire de {}", player.getGameProfile().getName(), e);
        }
    }

    /** Restaure inventaire, position et mode de jeu, puis supprime la sauvegarde. */
    public static boolean restore(MinecraftServer server, ServerPlayer player) {
        return restore(server, player, true);
    }

    /** teleportBack = false : le joueur n'est pas ramene a sa position d'origine (il sera place ailleurs). */
    public static boolean restore(MinecraftServer server, ServerPlayer player, boolean teleportBack) {
        Path path = file(player.getUUID());
        if (!Files.exists(path)) {
            return false;
        }
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(path.toFile());
        } catch (IOException e) {
            CosmicIsland.LOGGER.error("Sauvegarde illisible pour {}", player.getGameProfile().getName(), e);
            return false;
        }

        player.getInventory().clearContent();
        player.getInventory().load(tag.getList("inventory", Tag.TAG_COMPOUND));
        player.inventoryMenu.broadcastChanges();

        ServerLevel level = SpawnManager.level(server, tag.getString("dimension"));
        if (teleportBack && level != null) {
            player.teleportTo(level, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                    tag.getFloat("yRot"), tag.getFloat("xRot"));
        }
        player.setGameMode(GameType.byName(tag.getString("gameMode"), GameType.SURVIVAL));
        RoleManager.apply(player, RoleManager.effective(player));

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            CosmicIsland.LOGGER.error("Impossible de supprimer {}", path, e);
        }
        return true;
    }
}
