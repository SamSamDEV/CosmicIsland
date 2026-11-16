package fr.cosmicisland;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vocabulaire de la saison (config/cosmicisland/vocabulary.json).
 * Les commandes gardent leurs noms officiels (/ccis conseil, /ccis collier...) ; seuls les textes affiches changent.
 *
 * Utilisable dans TOUS les messages / configs de jeux :
 *   {v_conseil}     -> Éclipse            {v_le_conseil} -> l'Éclipse
 *   {v_du_conseil}  -> de l'Éclipse       {v_au_conseil} -> à l'Éclipse
 *   {v_un_conseil}  -> une Éclipse
 *   {V_conseil}     -> ÉCLIPSE (majuscules, marche avec toutes les formes : {V_le_conseil}...)
 * Termes : totem, collier, conseil, confort, reunification, elimination.
 */
public final class Vocab {
    public static class Term {
        public String nom, le, du, au, un;

        Term(String nom, String le, String du, String au, String un) {
            this.nom = nom; this.le = le; this.du = du; this.au = au; this.un = un;
        }
    }

    public static class Data {
        public String info = "Termes affichés dans les messages. Les commandes gardent leurs noms officiels. "
                + "Usage dans les textes : {v_conseil} {v_le_conseil} {v_du_conseil} {v_au_conseil} {v_un_conseil} ({V_...} = majuscules). "
                + "Les noms des objets (Bracelet d'Exemption, Globe d'immunité) sont dans assets/cosmicisland/lang/*.json.";
        public Map<String, Term> termes = defaults();
        /** Termes utilises quand la langue des messages est l'anglais (settings.json). */
        public Map<String, Term> termesEn = defaultsEn();
    }

    private static final Pattern TOKEN = Pattern.compile("\\{([vV])_(?:(le|du|au|un)_)?([a-z]+)\\}");
    private static Data data = new Data();

    private Vocab() {}

    public static Map<String, Term> defaults() {
        Map<String, Term> m = new LinkedHashMap<>();
        m.put("totem", new Term("Globe d'immunité", "le Globe d'immunité", "du Globe d'immunité", "au Globe d'immunité", "un Globe d'immunité"));
        m.put("collier", new Term("Bracelet d'Exemption", "le Bracelet d'Exemption", "du Bracelet d'Exemption", "au Bracelet d'Exemption", "un Bracelet d'Exemption"));
        m.put("conseil", new Term("Éclipse", "l'Éclipse", "de l'Éclipse", "à l'Éclipse", "une Éclipse"));
        m.put("confort", new Term("Moisson Stellaire", "la Moisson Stellaire", "de la Moisson Stellaire", "à la Moisson Stellaire", "une Moisson Stellaire"));
        m.put("reunification", new Term("Fusion", "la Fusion", "de la Fusion", "à la Fusion", "une Fusion"));
        m.put("elimination", new Term("Élimination", "l'Élimination", "de l'Élimination", "à l'Élimination", "une Élimination"));
        return m;
    }

    public static Map<String, Term> defaultsEn() {
        Map<String, Term> m = new LinkedHashMap<>();
        m.put("totem", new Term("Immunity Globe", "the Immunity Globe", "of the Immunity Globe", "to the Immunity Globe", "an Immunity Globe"));
        m.put("collier", new Term("Exemption Bracelet", "the Exemption Bracelet", "of the Exemption Bracelet", "to the Exemption Bracelet", "an Exemption Bracelet"));
        m.put("conseil", new Term("Eclipse", "the Eclipse", "of the Eclipse", "to the Eclipse", "an Eclipse"));
        m.put("confort", new Term("Stellar Harvest", "the Stellar Harvest", "of the Stellar Harvest", "to the Stellar Harvest", "a Stellar Harvest"));
        m.put("reunification", new Term("Merge", "the Merge", "of the Merge", "to the Merge", "a Merge"));
        m.put("elimination", new Term("Elimination", "the Elimination", "of the Elimination", "to the Elimination", "an Elimination"));
        return m;
    }

    private static Map<String, Term> terms() {
        return "en".equals(Lang.language()) && data.termesEn != null ? data.termesEn : data.termes;
    }

    public static void load() {
        Data d = KLFiles.load("vocabulary.json", Data.class, Data::new);
        if (d.termes == null) {
            d.termes = new LinkedHashMap<>();
        }
        // termes manquants (ajoutes dans une mise a jour) : valeurs par defaut
        defaults().forEach(d.termes::putIfAbsent);
        if (d.termesEn == null) {
            d.termesEn = new LinkedHashMap<>();
        }
        defaultsEn().forEach(d.termesEn::putIfAbsent);
        KLFiles.save("vocabulary.json", d);
        // ancien nom par defaut (avant le Bracelet) : mise a jour automatique
        Term old = d.termes.get("collier");
        if (old != null && "Anneau d'Exemption".equals(old.nom)) {
            d.termes.put("collier", defaults().get("collier"));
        }
        data = d;
        broadcast();
    }

    /** Nom d'un terme pour les noms d'objets (cote client : valeurs recues du serveur). */
    public static String nom(String key) {
        Term t = terms().get(key);
        return t == null ? key : t.nom;
    }

    public static String toJson() {
        return new com.google.gson.Gson().toJson(terms());
    }

    /** Termes recus du serveur (le client n'a pas forcement le fichier de config). */
    public static void setFromJson(String json) {
        try {
            Map<String, Term> m = new com.google.gson.Gson().fromJson(json,
                    new com.google.gson.reflect.TypeToken<Map<String, Term>>() {}.getType());
            if (m != null) {
                defaults().forEach(m::putIfAbsent);
                Data d = new Data();
                d.termes = m;
                data = d;
            }
        } catch (RuntimeException e) {
            CosmicIsland.LOGGER.warn("Vocabulaire recu du serveur illisible : ignore");
        }
    }

    public static void sendTo(net.minecraft.server.level.ServerPlayer player) {
        fr.cosmicisland.conseil.ConseilNetwork.toPlayer(player, new fr.cosmicisland.conseil.ConseilNetwork.VocabPacket(toJson()));
    }

    public static void broadcast() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(Vocab::sendTo));
        }
    }

    /** Remplace les {v_...} d'un texte. Les jetons inconnus sont laisses tels quels. */
    public static String apply(String text) {
        if (text == null || text.indexOf("_") < 0 || text.indexOf('{') < 0) {
            return text;
        }
        return TOKEN.matcher(text).replaceAll(m -> {
            Term t = terms().get(m.group(3));
            if (t == null) {
                return Matcher.quoteReplacement(m.group(0));
            }
            String form = m.group(2);
            String s = form == null ? t.nom : switch (form) {
                case "le" -> t.le;
                case "du" -> t.du;
                case "au" -> t.au;
                default -> t.un;
            };
            if (s == null) {
                s = t.nom;
            }
            if (m.group(1).equals("V")) {
                s = s.toUpperCase(java.util.Locale.ROOT);
            }
            return Matcher.quoteReplacement(s);
        });
    }
}
