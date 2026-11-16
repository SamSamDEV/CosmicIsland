package fr.cosmicisland;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Historique de la saison : journal des evenements marquants (eliminations, colliers, fusion, abandons...).
 * Fichier courant : config/cosmicisland/history/current.json ; une nouvelle saison archive l'ancienne
 * dans config/cosmicisland/history/<nom>.json.
 */
public final class SeasonHistory {
    private SeasonHistory() {}

    public static class Event {
        public String date = "";
        public String type = "";
        public String text = "";
    }

    public static class Data {
        public String season = "Saison 1";
        public String startedAt = now();
        public List<Event> events = new ArrayList<>();
    }

    private static final String CURRENT = "history/current.json";
    private static Data data = new Data();

    private static String now() {
        return LocalDateTime.now().withNano(0).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }

    public static void load() {
        data = KLFiles.load(CURRENT, Data.class, Data::new);
        if (data.events == null) {
            data.events = new ArrayList<>();
        }
    }

    public static Data data() {
        return data;
    }

    /** Ajoute un evenement. type : elimination, egalite, collier, fusion, defusion, abandon, retour, conseil, epreuve. */
    public static void log(String type, String text) {
        Event e = new Event();
        e.date = now();
        e.type = type;
        e.text = Vocab.apply(text);
        data.events.add(e);
        KLFiles.save(CURRENT, data);
    }

    /** Archive la saison en cours et en demarre une nouvelle. Retourne une erreur ou null. */
    public static String newSeason(String name) {
        String safe = data.season.replaceAll("[^A-Za-z0-9_\\-]", "_");
        Path archive = KLFiles.dir().resolve("history").resolve(safe + ".json");
        if (Files.exists(archive)) {
            safe = safe + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        }
        KLFiles.save("history/" + safe + ".json", data);
        Data fresh = new Data();
        fresh.season = name == null || name.isBlank() ? "Nouvelle saison" : name.trim();
        data = fresh;
        KLFiles.save(CURRENT, data);
        return null;
    }

    public static List<String> archives() {
        List<String> out = new ArrayList<>();
        try (var s = Files.list(KLFiles.dir().resolve("history"))) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json") && !n.equals("current.json"))
                    .sorted().forEach(n -> out.add(n.substring(0, n.length() - 5)));
        } catch (Exception ignored) {
            // pas encore d'archive
        }
        return out;
    }

    public static Data readArchive(String name) {
        if (!name.matches("[A-Za-z0-9_\\-]+") || !Files.exists(KLFiles.dir().resolve("history").resolve(name + ".json"))) {
            return null;
        }
        return KLFiles.load("history/" + name + ".json", Data.class, Data::new);
    }
}
