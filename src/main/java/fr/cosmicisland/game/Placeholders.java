package fr.cosmicisland.game;

import fr.cosmicisland.TeamManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholders utilisables dans les messages de tous les jeux :
 * {joueur} {equipe} {victoires} {victoires_<jeu>}
 * {equipe_<id>} (nom actuel de l'equipe rouge/jaune/blanche...)  {tag_<id>} (son tag)
 * {classement_N_nom} {classement_N_victoires}            (classement general, N = 1, 2, 3...)
 * {classement_<jeu>_N_nom} {classement_<jeu>_N_victoires} (classement d'un jeu)
 */
public final class Placeholders {
    private static final Pattern WINS_GAME = Pattern.compile("\\{victoires_([a-zA-Z0-9-]+)\\}");
    private static final Pattern RANK =
            Pattern.compile("\\{classement_(?:([a-zA-Z0-9-]+)_)?(\\d{1,3})_(nom|victoires)\\}");

    private static final Pattern TEAM_NAME = Pattern.compile("\\{(equipe|tag)_([a-zA-Z0-9-]+)\\}");

    private Placeholders() {}

    public static String apply(String text, ServerPlayer player) {
        if (text == null || text.indexOf('{') < 0) {
            return text;
        }
        text = fr.cosmicisland.Vocab.apply(text);
        String result = TEAM_NAME.matcher(text).replaceAll(m -> {
            TeamManager.KLTeam t = TeamManager.get(m.group(2));
            if (t == null) {
                return Matcher.quoteReplacement(m.group(0));
            }
            return Matcher.quoteReplacement(m.group(1).equals("equipe") ? t.name : t.tag);
        });

        if (player != null) {
            String teamId = TeamManager.teamIdOf(player.getUUID());
            String teamName = teamId == null ? "" : TeamManager.get(teamId).name;
            result = result.replace("{joueur}", player.getGameProfile().getName())
                    .replace("{equipe}", teamName);
            result = WINS_GAME.matcher(result).replaceAll(m ->
                    String.valueOf(RankingManager.wins(player.getUUID(), m.group(1))));
            result = result.replace("{victoires}", String.valueOf(RankingManager.wins(player.getUUID(), null)));
        }

        result = RANK.matcher(result).replaceAll(m -> {
            String game = m.group(1);
            int n = Integer.parseInt(m.group(2));
            boolean wantName = m.group(3).equals("nom");
            List<RankingManager.Row> rows = RankingManager.top(game, n);
            if (n < 1 || rows.size() < n) {
                return Matcher.quoteReplacement(wantName ? "-" : "0");
            }
            RankingManager.Row row = rows.get(n - 1);
            String name = row.name() == null ? "?" : row.name();
            return Matcher.quoteReplacement(wantName ? name : String.valueOf(row.wins()));
        });
        return result;
    }
}
