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
    public boolean requireLit = true;        // le feu doit etre ALLUME (briquet...) : sinon malus, et la jauge ne baisse pas
    public boolean extinguishAtZero = true;  // eteint le bloc quand la jauge tombe a 0 (jamais rallume de force)
    public boolean forceLit = false;         // true = le mod rallume lui-meme le bloc quand la jauge > 0 (deconseille avec Unlit Campfire)
    public boolean autoRegister = true;      // un joueur d'une equipe SANS feu pose un campfire : il devient le feu de l'equipe
    public double zoneRadius = 500.0;        // rayon (en blocs, a plat) de la zone de base autour du feu
    public String zoneMessages = "staff";    // qui recoit "vous entrez dans la zone" : staff | all | none
    public boolean teamBonuses = true;       // bonus / malus de faim selon la jauge du feu de l'equipe
    public double bonusAbovePercent = 90.0;  // au-dessus : bonus
    public double bonusHungerReductionPercent = 15.0; // -15 % de faim
    public double malusBelowPercent = 30.0;  // en dessous : malus
    public double malusHungerIncreasePercent = 10.0;  // +10 % de faim
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
        m.put("unlit", msg("&e&lLe feu n'est pas allumé !", "&7Allumez-le (briquet) pour éviter le malus", "title",
                "minecraft:block.fire.extinguish"));
        m.put("registered", msg("&6&lFeu de camp créé !", "&7Allumez-le (briquet) - zone de base : {radius} blocs", "title",
                "minecraft:block.campfire.crackle"));
        m.put("zoneEnter", msg("&e➤ Vous entrez dans la zone {tag}&e {team}", "", "actionbar", ""));
        m.put("zoneLeave", msg("&7➤ Vous quittez la zone {team}", "", "actionbar", ""));
        m.put("tierHigh", msg("&a&lLe feu rayonne !", "&7Bonus : moins de faim", "title", "minecraft:block.beacon.activate"));
        m.put("tierLow", msg("&c&lLe feu faiblit fortement", "&7Malus : plus de faim ({percent}%)", "title",
                "minecraft:block.beacon.deactivate"));
        m.put("tierNormal", msg("&7Le feu est revenu à un niveau normal ({percent}%)", "", "actionbar", ""));
        m.put("crafted", msg("&7Feu de camp fabriqué : posez-le au sol, puis allumez-le !", "", "actionbar", ""));
        m.put("reignited", msg("&a&lLe feu reprend !", "&7Le malus est levé", "title",
                "minecraft:item.flintandsteel.use"));
        return m;
    }
}
