package fr.cosmicisland;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.cosmicisland.camp.CampfireManager;
import fr.cosmicisland.chat.ChatManager;
import fr.cosmicisland.conseil.ConseilManager;
import fr.cosmicisland.game.GameRegistry;
import fr.cosmicisland.game.RankingManager;
import fr.cosmicisland.game.SpawnManager;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;

public final class KLFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private KLFiles() {}

    /** Dossier de config du mod : <serveur>/config/cosmicisland/ */
    public static Path dir() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(CosmicIsland.MODID);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            CosmicIsland.LOGGER.error("Impossible de creer le dossier {}", dir, e);
        }
        return dir;
    }

    // ------------------------------------------------------------ donnees PAR MONDE
    // Roles, equipes, saison, classement, feux, spawns, historique, etats : <monde>/cosmicisland/
    // Reglages (langue, soif, vocabulaire, admins, conseil, chat, jeux) : config/cosmicisland/ (communs a tous les mondes)

    private static Path worldDir = null;
    private static final Set<String> WORLD_FILES = Set.of("players.json", "teams.json", "spawns.json", "ranking.json",
            "season.json", "campfires.json");
    private static final String[] WORLD_PREFIXES = {"history/", "log_conseil/", "state/", "backups/", "saved/"};

    /** Appele au demarrage du monde, avant le chargement des fichiers. */
    public static void setWorld(Path worldRoot) {
        worldDir = worldRoot == null ? null : worldRoot.toAbsolutePath().normalize().resolve(CosmicIsland.MODID);
        if (worldDir != null) {
            try {
                Files.createDirectories(worldDir);
            } catch (IOException e) {
                CosmicIsland.LOGGER.error("Impossible de creer le dossier du monde {}", worldDir, e);
            }
            CosmicIsland.LOGGER.info("Cosmic Island - donnees du monde : {}", worldDir);
        }
    }

    private static boolean perWorld(String file) {
        if (worldDir == null) {
            return false;
        }
        if (WORLD_FILES.contains(file)) {
            return true;
        }
        for (String prefix : WORLD_PREFIXES) {
            if (file.startsWith(prefix) || (prefix.equals(file + "/"))) {
                return true;
            }
        }
        return false;
    }

    /** Chemin reel d'un fichier du mod : dans le dossier du monde (donnees de partie) ou dans la config (reglages). */
    public static Path path(String file) {
        return (perWorld(file) ? worldDir : dir()).resolve(file);
    }

    /**
     * Charge un fichier JSON. S'il n'existe pas, il est cree avec les valeurs par defaut.
     * Si le fichier est casse (JSON invalide), on NE l'ecrase PAS : on utilise les valeurs
     * par defaut en memoire et on log l'erreur.
     */
    public static <T> T load(String file, Class<T> type, Supplier<T> defaults) {
        Path path = path(file);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                T value = GSON.fromJson(reader, type);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                CosmicIsland.LOGGER.error("Fichier {} illisible : valeurs par defaut utilisees, fichier NON ecrase", file, e);
                return defaults.get();
            }
        }
        T value = defaults.get();
        save(file, value);
        return value;
    }

    public static void save(String file, Object data) {
        Path path = path(file);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            CosmicIsland.LOGGER.error("Impossible de creer le dossier de {}", path, e);
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            CosmicIsland.LOGGER.error("Impossible d'ecrire {}", path, e);
        }
    }

    /** Recharge tous les fichiers du mod (utilise au demarrage et par /ccis reload). */
    public static void loadAll() {
        CosmicIsland.LOGGER.info("Cosmic Island - dossier de config : {}", dir().toAbsolutePath());
        Lang.load();
        fr.cosmicisland.survival.ThirstManager.load();
        fr.cosmicisland.Vocab.load();
        KLPermissions.load();
        RoleManager.load();
        TeamManager.load();
        SpawnManager.load();
        RankingManager.load();
        SeasonHistory.load();
        SeasonManager.load();
        ConseilManager.load();
        ChatManager.load();
        CampfireManager.load();
        GameRegistry.loadConfigs();
    }
}
