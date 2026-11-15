package fr.cosmicisland.conseil;

import fr.cosmicisland.game.GameConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reglages du conseil : config/cosmicisland/conseil.json */
public class ConseilConfig {
    public int voteSeconds = 120;            // duree du vote ; -1 = jusqu'a la cloture par l'organisateur
    public int revealStartDelaySeconds = 3;  // delai avant le premier vote revele
    public int revealIntervalSeconds = 4;    // temps entre deux votes reveles
    public int minParticipants = 2;          // nombre minimum de participants dans l'equipe pour lancer un conseil
    public boolean allowSelfVote = false;    // un joueur peut-il voter pour lui-meme ?
    public int collierPromptSeconds = 30;    // temps laisse a un porteur de collier pour decider (0 = pas de question)
    public boolean collierConsume = true;    // le collier est consomme quand le joueur l'utilise
    public boolean totemOnSoloWin = true;    // un totem est donne au vainqueur d'une epreuve solo
    public boolean totemOnlyWhenMerged = true; // ... seulement quand les equipes sont fusionnees
    public boolean totemConsumeAtClose = true; // le totem est consomme a la cloture du conseil qu'il a protege
    public String buttonCollierYes = "[UTILISER {V_le_collier}]";
    public String buttonCollierNo = "[LE GARDER]";
    public boolean revealToSpectators = true; // les spectateurs voient le depouillement (le staff le voit toujours)

    /** Messages : texte (&c, {cible}, {n}, {total}, {equipe}), sous-titre, canal (chat, title, actionbar), son. */
    public Map<String, GameConfig.Msg> messages = defaultMessages();

    public GameConfig.Msg message(String key) {
        GameConfig.Msg msg = messages == null ? null : messages.get(key);
        if (msg == null) {
            msg = defaultMessages().get(key);
        }
        return msg;
    }

    private static GameConfig.Msg chat(String text) {
        return new GameConfig.Msg(text, "chat");
    }

    private static GameConfig.Msg title(String text, String subtitle, String sound) {
        GameConfig.Msg msg = new GameConfig.Msg(text, "title");
        msg.subtitle = subtitle;
        msg.sound = sound;
        return msg;
    }

    public static Map<String, GameConfig.Msg> defaultMessages() {
        Map<String, GameConfig.Msg> m = new LinkedHashMap<>();
        m.put("voteStart", chat("&6&l{V_conseil} &7: le vote est ouvert ! Le menu se rouvre avec &f/ccis vote&7."));
        m.put("voteRecap", chat("&aTon vote : contre &l{cible}&a. &7Tu peux le modifier jusqu'à la clôture avec &f/ccis vote&7."));
        m.put("voteClosed", title("&c&lLE VOTE EST CLOS", "&7Le dépouillement va commencer", ""));
        m.put("revealStart", title("&6&lLE DÉPOUILLEMENT", "&7Les votes vont être révélés...", ""));
        m.put("revealVote", title("&c&l{cible}", "&7Vote {n}/{total}", "minecraft:block.note_block.hat"));
        m.put("revealVoid", title("&8&l{cible}", "&7Vote {n}/{total} &8(annulé : immunité)", "minecraft:block.note_block.hat"));
        m.put("result", title("&c&l{cible}", "&7est éliminé de l'aventure", "minecraft:block.beacon.deactivate"));
        m.put("tie", title("&e&lÉGALITÉ", "&7Un nouveau vote est nécessaire", "minecraft:block.note_block.bass"));
        m.put("noValid", title("&e&lAUCUN VOTE VALIDE", "&7Un nouveau vote est nécessaire", "minecraft:block.note_block.bass"));
        m.put("revealImmune", title("&c&l{cible}", "&7Vote {n}/{total} &e(immunisé : ne peut pas être éliminé)",
                "minecraft:block.note_block.hat"));
        m.put("collierPrompt", chat("&6&l{V_conseil} &7: tu possèdes un &e&l{v_collier}&7. Veux-tu l'utiliser avant le dépouillement ?"));
        m.put("collierRevealed", title("&6&l{nom}", "&7a révélé avoir l'&e&limmunité", "minecraft:ui.toast.challenge_complete"));
        m.put("collierKept", chat("&7Tu gardes {v_le_collier}."));
        m.put("totemProtected", title("&6&l{nom}", "&7est protégé par &e&l{v_le_totem}", "minecraft:block.beacon.activate"));
        m.put("totemGiven", title("&6&l{V_totem}", "&7Pose-le ou cache-le : il te protège au prochain {v_le_conseil}",
                "minecraft:ui.toast.challenge_complete"));
        m.put("tieVote", title("&e&lSECOND VOTE", "&7Seuls à égalité : &f{noms}", "minecraft:block.note_block.bass"));
        m.put("tieNoVote", chat("&e&l{V_conseil} &7: tu es à égalité, tu ne votes pas à ce tour. Les autres départagent."));
        m.put("farewell", chat("&7Tu quittes l'aventure. Merci d'avoir joué !"));
        return m;
    }
}
