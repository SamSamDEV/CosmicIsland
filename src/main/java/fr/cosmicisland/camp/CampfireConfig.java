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
    public double debuffBelow = 0.0;         // niveau de jauge sous lequel le feu est considere eteint
    public boolean showBar = true;           // barre de jauge visible pour l'equipe
    public int msgVersion = 0;               // migration des messages
    public double warnBelowPercent = 10.0;   // avertissement quand le feu passe sous ce pourcentage
    public boolean requireLit = true;        // le feu doit etre ALLUME (briquet...) : sinon malus, et la jauge ne baisse pas
    public boolean extinguishAtZero = true;  // eteint le bloc quand la jauge tombe a 0 (jamais rallume de force)
    public boolean forceLit = false;         // true = le mod rallume lui-meme le bloc quand la jauge > 0 (deconseille avec Unlit Campfire)
    public boolean disableOtherCampfires = true; // les feux de camp vanilla / d'autres mods ne peuvent pas etre poses (sauf par le staff)
    public double fastCookAbovePercent = 90.0; // au-dessus de ce niveau de jauge, la cuisson va plus vite
    public double fastCookBonus = 0.3;         // +30 % de vitesse (0 = desactive)
    public double fillSpeedFactor = 4.0;     // vitesse de remplissage quand un combustible brule = vitesse de baisse x ce facteur (4 = la jauge monte nettement)
    public double igniteStartPercent = 25.0; // niveau de la jauge juste apres l'allumage
    public double igniteGainPerTap = 0.07;   // part de la jauge d'allumage gagnee a chaque appui (1.0 = pleine)
    public double igniteDecayPerSecond = 0.15; // part perdue chaque seconde quand on ne tape pas assez vite
    public double igniteRange = 5.0;         // distance max au feu pendant l'allumage
    public double cookSeconds = 60.0;        // duree de cuisson du riz
    public int riceOutput = 16;              // coquillages de riz cuit obtenus par cuisson
    public double grillTimeFactor = 1.0;     // 1.0 = duree de cuisson d'un feu de camp vanilla (30 s), 0.5 = deux fois plus vite
    public double boilSeconds = 20.0;        // duree de purification d'une gourde
    public boolean rainExtinguish = true;    // la pluie eteint le feu d'equipe s'il n'est pas couvert
    public int rainCoverSize = 3;            // largeur (en blocs) de la zone a couvrir au-dessus du feu (3 = 3x3)
    public boolean protectFromOtherMods = true; // rallume le feu s'il s'eteint sans pluie ni eau alors qu'il reste du carburant
    public boolean autoRegister = true;      // un joueur d'une equipe SANS feu pose un campfire : il devient le feu de l'equipe
    public double barRadius = 30.0;          // la barre du feu n'apparait que dans ce rayon (en blocs, a plat) autour du feu ; 0 = partout
    public double zoneRadius = 500.0;        // rayon (en blocs, a plat) de la zone de base autour du feu
    public String zoneMessages = "staff";    // qui recoit "vous entrez dans la zone" : staff | all | none

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
        m.put("minecraft:coal", 15.0);
        m.put("minecraft:charcoal", 15.0);
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
        m.put("low", msg("&e&lLe feu faiblit...", "&7Il reste {percent}% : alimentez-le", "title",
                "minecraft:block.note_block.bass"));
        m.put("out", msg("&c&lLE FEU S'EST ÉTEINT", "", "title", "minecraft:block.fire.extinguish"));
        m.put("unlit", msg("&c&lLE FEU S'EST ÉTEINT", "&7Rallumez-le avec l'allume-feu", "title", "minecraft:block.fire.extinguish"));
        m.put("registered", msg("&6Feu de camp posé : allume-le avec l'allume-feu (zone de base : {radius} blocs)", "", "actionbar", ""));
        m.put("alreadyHave", msg("&cTon équipe a déjà un feu de camp.", "", "actionbar", ""));
        m.put("otherDisabled", msg("&cLes feux de camp classiques sont désactivés : fabrique le feu de camp d'équipe.", "", "actionbar", ""));
        m.put("needLit", msg("&cLe feu est éteint : allume-le d'abord avec l'allume-feu.", "", "actionbar", ""));
        m.put("noTeamFire", msg("&cCe feu n'appartient à aucune équipe.", "", "actionbar", ""));
        m.put("zoneEnter", msg("&e➤ Vous entrez dans la zone {tag}&e {team}", "", "actionbar", ""));
        m.put("zoneLeave", msg("&7➤ Vous quittez la zone {team}", "", "actionbar", ""));
        m.put("crafted", msg("&7Feu de camp fabriqué : posez-le au sol, puis allumez-le avec l'allume-feu !", "", "actionbar", ""));
        m.put("reignited", msg("&a&lLe feu est allumé !", "", "title",
                "minecraft:item.flintandsteel.use"));
        return m;
    }
}
