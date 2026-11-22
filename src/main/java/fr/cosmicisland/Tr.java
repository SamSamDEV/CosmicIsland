package fr.cosmicisland;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Textes avec cle (interface, infobulles, noms d'objets, saison) : ils suivent la langue du MOD (settings.json du serveur,
 * synchronisee aux joueurs), et non la langue de Minecraft. Les textes viennent de lang/fr_fr.json, lang/en_us.json,
 * ou lang/<code>.json pour une langue ajoutee.
 */
public final class Tr {
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

    private Tr() {}

    @SuppressWarnings("unchecked")
    private static Map<String, String> read(String file) {
        try (InputStream in = Tr.class.getResourceAsStream("/assets/" + CosmicIsland.MODID + "/lang/" + file)) {
            if (in != null) {
                Map<String, String> m = new com.google.gson.Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Map.class);
                return m == null ? new HashMap<>() : m;
            }
        } catch (Exception e) {
            CosmicIsland.LOGGER.error("Lang illisible : " + file, e);
        }
        return null;
    }

    /** Textes d'une langue : lang/<code>.json, ou fr_fr.json / en_us.json pour fr et en, ou lang/<code>_<code>.json. */
    private static Map<String, String> language(String code) {
        return CACHE.computeIfAbsent(code, c -> {
            String[] names = switch (c) {
                case "fr" -> new String[]{"fr_fr.json"};
                case "en" -> new String[]{"en_us.json"};
                default -> new String[]{c + ".json", c + "_" + c + ".json"};
            };
            for (String n : names) {
                Map<String, String> m = read(n);
                if (m != null) {
                    return m;
                }
            }
            return new HashMap<>();
        });
    }

    public static String s(String key, Object... args) {
        Map<String, String> fr = language("fr");
        String text = language(Lang.effective()).get(key);
        if (text == null) {
            text = fr.getOrDefault(key, key);
        }
        if (args.length == 0) {
            return text;
        }
        Object[] plain = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            plain[i] = args[i] instanceof Component c ? c.getString() : args[i];
        }
        try {
            return String.format(text, plain);
        } catch (Exception e) {
            return text;
        }
    }

    public static MutableComponent c(String key, Object... args) {
        return Component.literal(s(key, args));
    }
}
