package fr.cosmicisland.game;

import fr.cosmicisland.KLFiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Registre des jeux disponibles. Les addons appellent GameRegistry.register(...) au chargement. */
public final class GameRegistry {
    private static final Map<String, KLGame> GAMES = new LinkedHashMap<>();
    private static final Map<String, GameConfig> CONFIGS = new LinkedHashMap<>();

    private GameRegistry() {}

    public static void register(KLGame game) {
        GAMES.put(game.id().toLowerCase(), game);
    }

    public static KLGame get(String id) {
        return GAMES.get(id.toLowerCase());
    }

    public static GameConfig config(String id) {
        return CONFIGS.get(id.toLowerCase());
    }

    public static Set<String> ids() {
        return GAMES.keySet();
    }

    /** (Re)charge le fichier games/<id>.json de chaque jeu ; le cree s'il n'existe pas. */
    public static void loadConfigs() {
        CONFIGS.clear();
        for (Map.Entry<String, KLGame> entry : GAMES.entrySet()) {
            String id = entry.getKey();
            KLGame game = entry.getValue();
            GameConfig config = KLFiles.load("games/" + id + ".json", GameConfig.class, () -> {
                GameConfig fresh = game.createDefaultConfig();
                if (fresh.displayName == null || fresh.displayName.isEmpty()) {
                    fresh.displayName = game.defaultDisplayName();
                }
                return fresh;
            });
            if (config.displayName == null || config.displayName.isEmpty()) {
                config.displayName = game.defaultDisplayName();
            }
            CONFIGS.put(id, config);
        }
    }

    public static void saveConfig(String id) {
        GameConfig config = config(id);
        if (config != null) {
            KLFiles.save("games/" + id.toLowerCase() + ".json", config);
        }
    }
}
