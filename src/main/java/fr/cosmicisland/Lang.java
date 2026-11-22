package fr.cosmicisland;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Langue des messages du mod (settings.json : "language": "fr", "en" ou le code d'une langue ajoutee).
 * Les textes par defaut sont ecrits en francais ; dans les autres langues ils sont traduits a l'affichage par un
 * dictionnaire de phrases (config/cosmicisland/lang_<code>.json ; lang_en.json est complete automatiquement).
 * Ajouter une langue = ajouter un fichier lang_<code>.json (meme format que lang_en.json).
 * La traduction fonctionne aussi sur les textes deja sauvegardes dans les configs.
 */
public final class Lang {
    private Lang() {}

    public static class Settings {
        public String language = "fr";
    }

    private static Settings settings = new Settings();
    private static Map<String, Pattern> patterns = new java.util.HashMap<>();
    private static Map<String, Map<String, String>> dicts = new LinkedHashMap<>();

    private static void loadResourceDefaults() {
        try (java.io.InputStream in = Lang.class.getResourceAsStream("/data/cosmicisland/lang_en_defaults.json")) {
            if (in != null) {
                Map<?, ?> m = new com.google.gson.Gson().fromJson(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), Map.class);
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    DEFAULTS.putIfAbsent((String) e.getKey(), (String) e.getValue());
                }
            }
        } catch (Exception e) {
            CosmicIsland.LOGGER.error("Dictionnaire anglais illisible", e);
        }
    }

    public static void load() {
        loadResourceDefaults();
        settings = KLFiles.load("settings.json", Settings.class, Settings::new);
        if (settings.language == null || settings.language.isBlank()) {
            settings.language = "fr";
        }
        dicts = new LinkedHashMap<>();
        patterns = new java.util.HashMap<>();
        // Anglais : dictionnaire fourni par le mod, complete automatiquement dans lang_en.json
        LangFile en = KLFiles.load("lang_en.json", LangFile.class, () -> {
            LangFile f = new LangFile();
            f.phrases = new LinkedHashMap<>(DEFAULTS);
            return f;
        });
        if (en.phrases == null) {
            en.phrases = new LinkedHashMap<>();
        }
        boolean added = false;
        for (Map.Entry<String, String> e : DEFAULTS.entrySet()) {
            if (!en.phrases.containsKey(e.getKey())) {
                en.phrases.put(e.getKey(), e.getValue());
                added = true;
            }
        }
        if (added) {
            KLFiles.save("lang_en.json", en);
        }
        dicts.put("en", en.phrases);
        // Autres langues : tout fichier config/cosmicisland/lang_<code>.json est une langue de plus
        try (var files = java.nio.file.Files.list(KLFiles.dir())) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("lang_") && n.endsWith(".json") && !n.equals("lang_en.json"))
                    .forEach(n -> {
                        String code = n.substring(5, n.length() - 5).toLowerCase();
                        LangFile f = KLFiles.load(n, LangFile.class, LangFile::new);
                        if (f.phrases != null && !code.isEmpty() && !code.equals("fr")) {
                            dicts.put(code, f.phrases);
                        }
                    });
        } catch (Exception ignored) {
            // pas de dossier lisible : seul l'anglais est disponible
        }
    }

    public static class LangFile {
        public String info = "Phrases françaises -> langue du fichier (lang_<code>.json), appliquées quand language = <code> dans settings.json.";
        public Map<String, String> phrases = new LinkedHashMap<>();
    }

    private static volatile String synced = null;

    /** Langue recue du serveur (cote client) ; null = utiliser le reglage local. */
    public static void setSynced(String lang) {
        synced = lang;
    }

    /** Langue a utiliser pour les textes affiches par le client. */
    public static String effective() {
        return synced != null ? synced : settings.language;
    }

    public static String language() {
        return settings.language;
    }

    /** Langues disponibles : le francais (langue des textes d'origine) et chaque dictionnaire lang_<code>.json. */
    public static java.util.List<String> available() {
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add("fr");
        list.addAll(dicts.keySet());
        return list;
    }

    public static boolean setLanguage(String lang) {
        if (!available().contains(lang)) {
            return false;
        }
        settings.language = lang;
        KLFiles.save("settings.json", settings);
        fr.cosmicisland.camp.FireNetwork.broadcastLang();
        return true;
    }

    /** Traduit un texte dans la langue du mod ; en francais (langue d'origine) il est renvoye tel quel. */
    public static String tr(String text) {
        String code = settings.language;
        if (text == null || "fr".equals(code)) {
            return text;
        }
        Map<String, String> dict = dicts.get(code);
        if (dict == null || dict.isEmpty()) {
            return text;
        }
        Pattern pattern = patterns.get(code);
        if (pattern == null) {
            StringBuilder sb = new StringBuilder();
            dict.keySet().stream().sorted((a, b) -> b.length() - a.length()).forEach(k -> {
                if (!k.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append('|');
                    }
                    sb.append(Pattern.quote(k));
                }
            });
            if (sb.length() == 0) {
                return text;
            }
            pattern = Pattern.compile(sb.toString());
            patterns.put(code, pattern);
        }
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String rep = dict.get(m.group());
            m.appendReplacement(out, Matcher.quoteReplacement(rep == null ? m.group() : rep));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    private static void p(String fr, String en) {
        DEFAULTS.put(fr, en);
    }

    static {
        // Conseil
        p(" : le vote est ouvert ! Le menu se rouvre avec ", " : voting is open! Reopen the menu with ");
        p("Ton vote : contre ", "Your vote: against ");
        p(". ", ". ");
        p("Tu peux le modifier jusqu'à la clôture avec ", "You can change it until voting closes with ");
        p("LE VOTE EST CLOS", "VOTING IS CLOSED");
        p("Le dépouillement va commencer", "The count is about to begin");
        p("LE DÉPOUILLEMENT", "THE COUNT");
        p("Les votes vont être révélés...", "The votes are about to be revealed...");
        p("Vote ", "Vote ");
        p("(annulé : immunité)", "(void: immunity)");
        p("est éliminé de l'aventure", "is eliminated from the adventure");
        p("ÉGALITÉ", "TIE");
        p("Un nouveau vote est nécessaire", "A new vote is needed");
        p("AUCUN VOTE VALIDE", "NO VALID VOTE");
        p("(immunisé : ne peut pas être éliminé)", "(immune: cannot be eliminated)");
        p(" : tu possèdes un ", " : you own a ");
        p(". Veux-tu l'utiliser avant le dépouillement ?", ". Do you want to use it before the count?");
        p("Veux-tu l'utiliser avant le dépouillement ?", "Do you want to use it before the count?");
        p("a révélé avoir l'", "has revealed holding ");
        p("immunité", "immunity");
        p("Tu gardes ", "You keep ");
        p("est protégé par ", "is protected by ");
        p("Pose-le ou cache-le : il te protège au prochain ", "Place it or hide it: it protects you at the next ");
        p("SECOND VOTE", "SECOND VOTE");
        p("Seuls à égalité : ", "Tied players only: ");
        p(" : tu es à égalité, tu ne votes pas à ce tour. Les autres départagent.", " : you are tied, you do not vote this round. The others decide.");
        p("Tu quittes l'aventure. Merci d'avoir joué !", "You leave the adventure. Thanks for playing!");
        p("Tu révèles ton immunité : les votes contre toi ne pourront pas t'éliminer.", "You reveal your immunity: votes against you cannot eliminate you.");
        p("[UTILISER ", "[USE ");
        p("[LE GARDER]", "[KEEP IT]");
        p("Le vote n'est pas ouvert.", "Voting is not open.");
        p("Tu ne participes pas à ", "You are not taking part in ");
        p("Tu es à égalité : tu ne votes pas à ce tour.", "You are tied: you do not vote this round.");
        p("Clique pour voter contre ce joueur", "Click to vote against this player");
        p("Tu n'as pas encore voté.", "You have not voted yet.");
        p("Ton vote : ", "Your vote: ");
        p("Tu peux changer ton vote jusqu'à la clôture.", "You can change your vote until voting closes.");
        p("Joueur visé par ton vote", "Player targeted by your vote");
        p("Revenir à la liste", "Back to the list");
        p("Confirmer ton vote", "Confirm your vote");
        p("Ce joueur ne peut pas être voté.", "This player cannot be voted for.");
        p("Seuls les joueurs à égalité peuvent être votés à ce tour.", "Only tied players can be voted for this round.");
        p("Tu ne peux pas voter pour toi-même.", "You cannot vote for yourself.");
        p("Ce joueur est protégé par un ", "This player is protected by a ");
        p(" : il ne peut pas être voté.", " : they cannot be voted for.");
        p("Aucun choix en attente pour toi.", "No choice pending for you.");
        p("Tu n'as plus de ", "You no longer have a ");
        // Camp
        p("vous entrez dans la zone", "you enter the zone");
        p("Tu nourris le feu : ", "You feed the fire: ");
        p(" (feu à ", " (fire at ");
        p("Le feu est déjà au maximum.", "The fire is already at maximum.");
        p("Ce n'est pas le feu de ton équipe.", "This is not your team's fire.");
        p("Pensez à l'alimenter (", "Remember to feed it (");
        p("Malus pendant les épreuves !", "Penalty during challenges!");
        p("Le feu n'est pas allumé !", "The fire is not lit!");
        p("Allumez-le (briquet) pour éviter le malus", "Light it (lighter) to avoid the penalty");
        p("Feu de camp créé !", "Campfire created!");
        p("Allumez-le (briquet) - zone de base : ", "Light it (lighter) - base zone: ");
        p(" blocs", " blocks");
        p("➤ Vous entrez dans la zone ", "➤ You enter the zone ");
        p("➤ Vous quittez la zone ", "➤ You leave the zone ");
        p("Bonus : moins de faim", "Bonus: less hunger");
        p("Malus : plus de faim (", "Penalty: more hunger (");
        p("Le feu est revenu à un niveau normal (", "The fire is back to a normal level (");
        p("Feu de camp fabriqué : posez-le au sol, puis allumez-le !", "Campfire crafted: place it on the ground, then light it!");
        p("Le malus est levé", "The penalty is lifted");
        p("Feu de camp", "Campfire");
        p(" (éteint, à rallumer)", " (out, relight it)");
        // Chat / equipes / statut
        p("Cette commande est désactivée pendant l'aventure.", "This command is disabled during the adventure.");
        p("Les équipes ne forment plus qu'une seule tribu", "The teams now form a single tribe");
        p(" : toutes les équipes sont réunies. Désormais, c'est ", " : all teams are united. From now on, it is ");
        p("chacun pour soi", "every man for himself");
        p(" !", "!");
        p("[ORGA → toi] ", "[ORGA → you] ");
        p("Aucun organisateur n'est connecté pour le moment.", "No organizer is online right now.");
        p("Message envoyé à l'organisation.", "Message sent to the organizers.");
        p("Veux-tu vraiment ", "Do you really want to ");
        p("abandonner", "give up");
        p(" ? Tu seras spectateur. ", "? You will become a spectator. ");
        p("[OUI, J'ABANDONNE]", "[YES, I GIVE UP]");
        p("Quitter définitivement l'aventure", "Leave the adventure for good");
        p("Tu ne participes pas à l'aventure.", "You are not taking part in the adventure.");
        p("Tu ne peux pas abandonner pendant ", "You cannot give up during ");
        p(" : demande à un organisateur.", " : ask an organizer.");
        p("[Aventure] ", "[Adventure] ");
        p(" a abandonné l'aventure.", " has given up the adventure.");
        p("Tu reviens dans l'aventure, équipe ", "You are back in the adventure, team ");
        p(" est de retour dans l'aventure (équipe ", " is back in the adventure (team ");
        p("Dans quelle équipe remettre ", "Which team should we put ");
        p(" ? ", " in? ");
        // Epreuves
        p("Temps écoulé : ", "Time elapsed: ");
        p(" : une épreuve se prépare ! Rejoins-tu ?", " : a challenge is starting! Are you joining?");
        p("Tu as rejoint ", "You joined ");
        p(". Équipe : ", ". Team: ");
        p(". Attends le top départ !", ". Wait for the start signal!");
        p("Tu ne participes pas à ", "You are not taking part in ");
        p("Rappel : ", "Reminder: ");
        p(" est en cours !", " is under way!");
        p(" est terminé !", " is over!");
        p(" a été arrêté par l'organisation.", " was stopped by the organizers.");
        p("VICTOIRE !", "VICTORY!");
        p("DÉFAITE", "DEFEAT");
    }
}
