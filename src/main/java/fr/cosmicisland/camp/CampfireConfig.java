package fr.cosmicisland.camp;

import fr.cosmicisland.game.GameConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reglages du feu de camp : config/cosmicisland/campfire.json */
public class CampfireConfig {

    /** Un effet de potion applique au malus (ex. minecraft:slowness, amplificateur 0 = niveau I). */
    public static class EffectEntry {
        public String effect = "minecraft:slowness";
        public int amplifier = 0;

        public EffectEntry() {}

        public EffectEntry(String effect, int amplifier) {
            this.effect = effect;
            this.amplifier = amplifier;
        }
    }

    public boolean enabled = true;
    public double maxFuel = 100.0;           // jauge maximale
    public double startFuel = 100.0;         // niveau d'un feu qu'on vient de definir
    public double drainPerMinute = 5.0;      // points perdus par minute (100 / 5 = 20 minutes)
    public double debuffBelow = 0.0;         // le malus s'applique quand le feu est a ce niveau ou moins (0 = eteint)
    public boolean showBar = true;           // barre de jauge visible pour l'equipe
    public double warnBelowPercent = 20.0;   // avertissement quand le feu passe sous ce pourcentage
    public boolean applyEffects = true;      // appliquer les effets de potion pendant les epreuves
    public List<EffectEntry> debuffEffects = new ArrayList<>(List.of(
            new EffectEntry("minecraft:weakness", 0),
            new EffectEntry("minecraft:slowness", 0)));

    /** Objets acceptes pour alimenter le feu et points de jauge gagnes (un objet par clic droit). */
    public Map<String, Double> fuelItems = defaultFuelItems();

    public Map<String, GameConfig.Msg> messages = defaultMessages();

    public GameConfig.Msg message(String key) {
        GameConfig.Msg msg = messages == null ? null : messages.get(key);
        if (msg == null) {
            msg = defaultMessages().get(key);
        }
        return msg;
    }

    private static Map<String, Double> defaultFuelItems() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"}) {
            m.put("minecraft:" + wood + "_log", 10.0);
        }
        m.put("minecraft:stick", 1.0);
        m.put("minecraft:coal", 12.0);
        m.put("minecraft:charcoal", 12.0);
        return m;
    }

    private static GameConfig.Msg msg(String text, String subtitle, String channel, String sound) {
        GameConfig.Msg m = new GameConfig.Msg(text, channel);
        m.subtitle = subtitle;
        m.sound = sound;
        return m;
    }

    public static Map<String, GameConfig.Msg> defaultMessages() {
        Map<String, GameConfig.Msg> m = new LinkedHashMap<>();
        m.put("fed", msg("&6Tu nourris le feu : &f+{points}&6 (feu à &f{percent}%&6)", "", "actionbar", ""));
        m.put("full", msg("&7Le feu est déjà au maximum.", "", "actionbar", ""));
        m.put("notYours", msg("&cCe n'est pas le feu de ton équipe.", "", "actionbar", ""));
        m.put("low", msg("&e&lLe feu faiblit...", "&7Pensez à l'alimenter ({percent}%)", "title",
                "minecraft:block.note_block.bass"));
        m.put("out", msg("&c&lLE FEU S'EST ÉTEINT", "&7Malus pendant les épreuves !", "title",
                "minecraft:block.fire.extinguish"));
        m.put("reignited", msg("&a&lLe feu reprend !", "&7Le malus est levé", "title",
                "minecraft:item.flintandsteel.use"));
        return m;
    }
}
