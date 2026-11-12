package fr.cosmicisland.game;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Configuration d'un jeu : un fichier config/cosmicisland/games/<id>.json par jeu. */
public class GameConfig {

    public static class KitItem {
        public String item = "minecraft:stick";
        public int count = 1;

        public KitItem() {}

        public KitItem(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    /** true = autorise pendant l'epreuve, false = bloque. */
    public static class Flags {
        public boolean pvp = false;
        public boolean breakBlocks = false;
        public boolean placeBlocks = false;
        public boolean hunger = false;
        public boolean damage = false;
        public boolean dropItems = false;
        public boolean interact = false;
        public boolean mobs = false;
    }

    /** Un message : texte (&c, {joueur}, {equipe}, {jeu}), canal(aux), son optionnel. */
    public static class Msg {
        public String text = "";
        public String subtitle = "";
        public String channel = "chat"; // chat, title, actionbar (combinables : "chat+title")
        public String sound = "";

        public Msg() {}

        public Msg(String text, String channel) {
            this.text = text;
            this.channel = channel;
        }
    }

    public boolean enabled = true;
    public String displayName = "";
    public int inviteSeconds = 60;
    public int countdownSeconds = 3;
    public int durationSeconds = 0;  // 0 = pas de limite de temps
    public int reminderSeconds = 0;  // 0 = pas de rappel
    public List<String> allowedTeams = new ArrayList<>(List.of("rouge", "jaune", "blanche"));
    public String gameMode = "survival";
    public boolean clearInventoryOnJoin = true;
    public List<KitItem> kit = new ArrayList<>();
    public Flags flags = new Flags();

    public String buttonJoin = "[REJOINDRE]";
    public String buttonDecline = "[NE PAS REJOINDRE]";
    public String countdownFormat = "&6&l{n}";
    public String countdownSound = "minecraft:block.dispenser.dispense";
    public String goSound = "minecraft:block.note_block.pling";

    public Map<String, Msg> messages = defaultMessages();

    /** Reglages propres a un jeu (utilises par les jeux eux-memes). */
    public JsonObject settings = new JsonObject();

    public static Map<String, Msg> defaultMessages() {
        Map<String, Msg> m = new LinkedHashMap<>();
        m.put("invite", new Msg("&6&l{jeu} &7: une épreuve se prépare ! Rejoins-tu ?", "chat"));
        m.put("joined", new Msg("&aTu as rejoint &l{jeu}&a. Équipe : &f&l{equipe}&a. Attends le top départ !", "chat"));
        m.put("declined", new Msg("&cTu ne participes pas à &l{jeu}&c.", "chat"));
        m.put("start", new Msg("&a&lGO !", "title"));
        m.put("reminder", new Msg("&eRappel : &l{jeu}&e est en cours !", "actionbar"));
        m.put("victory", new Msg("&a&lVICTOIRE !", "title"));
        m.put("defeat", new Msg("&c&lDÉFAITE", "title"));
        m.put("end", new Msg("&6&l{jeu}&6 est terminé !", "chat"));
        m.put("stop", new Msg("&c&l{jeu}&c a été arrêté par l'organisation.", "chat"));
        return m;
    }

    /** Message de la config, ou celui par defaut si la cle manque dans le fichier. */
    public Msg message(String key) {
        Msg msg = messages == null ? null : messages.get(key);
        if (msg == null) {
            msg = defaultMessages().get(key);
        }
        return msg;
    }
}
