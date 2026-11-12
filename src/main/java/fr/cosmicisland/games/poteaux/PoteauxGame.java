package fr.cosmicisland.games.poteaux;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.camp.CampfireManager;
import fr.cosmicisland.Txt;
import fr.cosmicisland.game.GameConfig;
import fr.cosmicisland.game.GameContext;
import fr.cosmicisland.game.GameManager;
import fr.cosmicisland.game.GameMessages;
import fr.cosmicisland.game.GameResult;
import fr.cosmicisland.game.GameRegistry;
import fr.cosmicisland.game.KLGame;
import fr.cosmicisland.game.SpawnManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Epreuve des poteaux : tout le monde se tient sur un poteau au-dessus de l'eau.
 * Une fleche apparait au centre de l'ecran (meme sequence et meme rythme pour tous),
 * il faut appuyer sur la bonne touche dans le temps imparti. Trop d'erreurs : on perd l'equilibre.
 * Tomber dans l'eau de la zone elimine le joueur.
 *
 * Reglages dans games/poteaux.json (section "settings") : maxErrors, windowStartMs, windowMinMs,
 * windowStepMs, gapStartMs, gapMinMs, gapStepMs, graceMs, firstDelayMs, pushStrength, fallTimeoutTicks.
 */
public class PoteauxGame implements KLGame {
    public static final PoteauxGame INSTANCE = new PoteauxGame();
    private static final String ID = "poteaux";

    private static class Contestant {
        final UUID id;
        final String name;
        final String group; // equipe (mode equipes) ou identifiant du joueur (mode solo)
        int errors = 0;
        boolean alive = true;
        boolean answered = false;
        boolean falling = false;
        int fallTicks = 0;
        int deadline = 0; // dernier tick ou la reponse est acceptee pour la fleche en cours

        Contestant(UUID id, String name, String group) {
            this.id = id;
            this.name = name;
            this.group = group;
        }
    }

    private record Zone(String dimension, AABB box) {}

    private record Elimination(UUID id, String name, double seconds, String reason, int errors) {}

    private final Map<UUID, Contestant> contestants = new LinkedHashMap<>();
    private final List<Elimination> eliminations = new ArrayList<>();
    private int arrowsShown = 0;
    private Random random = new Random();
    private boolean teamMode = true;
    private int initialGroups = 0;
    private int tickCounter = 0;
    private boolean arrowActive = false;
    private int arrowDir = 0;
    private int arrowResolveTick = 0;
    private int nextArrowTick = 0;
    private int windowMs = 2000;
    private int gapMs = 900;

    private PoteauxGame() {}

    // ------------------------------------------------------------ API KLGame

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String defaultDisplayName() {
        return "Poteaux";
    }

    @Override
    public GameConfig createDefaultConfig() {
        GameConfig c = new GameConfig();
        c.displayName = "Poteaux";
        c.gameMode = "adventure";
        c.inviteSeconds = 120;
        c.durationSeconds = 600; // -1 = temps infini
        JsonObject s = c.settings;
        s.addProperty("maxErrors", 3);        // le joueur tombe a cette erreur
        s.addProperty("windowStartMs", 2000); // temps pour appuyer, au debut
        s.addProperty("windowMinMs", 500);    // temps pour appuyer, minimum
        s.addProperty("windowStepMs", 75);    // reduction apres chaque fleche
        s.addProperty("gapStartMs", 900);     // pause entre deux fleches, au debut
        s.addProperty("gapMinMs", 400);       // pause minimum
        s.addProperty("gapStepMs", 40);       // reduction apres chaque fleche
        s.addProperty("graceMs", 150);        // tolerance de latence
        s.addProperty("firstDelayMs", 1500);  // delai avant la premiere fleche
        s.addProperty("pushStrength", 0.3);   // poussee vers l'avant (distance en blocs ~ 7 x valeur)
        s.addProperty("pushUp", 0.3);         // petit saut vers le haut pendant la poussee
        s.addProperty("campfireWindowMultiplier", 0.75); // feu de camp eteint : temps pour appuyer x 0.75
        s.addProperty("campfireErrorWeight", 2);         // feu de camp eteint : chaque erreur compte double
        s.addProperty("fallTimeoutTicks", 80); // elimination de secours si le joueur ne touche pas l'eau
        c.messages.put("fall", new GameConfig.Msg("&c&lTu perds l'équilibre !", "actionbar"));
        GameConfig.Msg eliminated = new GameConfig.Msg("&c&lÉLIMINÉ", "title");
        eliminated.subtitle = "&7Tu retournes au camp";
        c.messages.put("eliminated", eliminated);
        c.messages.put("eliminatedOther", new GameConfig.Msg("&e{joueur} &7est éliminé.", "chat"));
        return c;
    }

    @Override
    public String canStart(GameContext ctx) {
        GameConfig cfg = ctx.config();
        if (readZone(cfg) == null) {
            return "Zone non définie : place-toi à un coin de la zone d'eau et tape /ccis poteaux zone pos1, "
                    + "puis à l'autre coin opposé et tape /ccis poteaux zone pos2.";
        }
        List<String> missing = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        Map<BlockPos, String> used = new HashMap<>();
        for (ServerPlayer p : ctx.participants()) {
            String name = p.getGameProfile().getName();
            BlockPos pole = poleOf(p);
            if (pole == null) {
                missing.add(name);
            } else {
                String other = used.putIfAbsent(pole, name);
                if (other != null) {
                    duplicates.add(other + " et " + name);
                }
            }
        }
        if (!missing.isEmpty()) {
            return "Ces joueurs ne sont pas sur un poteau : " + String.join(", ", missing);
        }
        if (!duplicates.isEmpty()) {
            return "Deux joueurs sur le même poteau : " + String.join(" ; ", duplicates);
        }
        return null;
    }

    @Override
    public void onStart(GameContext ctx) {
        GameConfig cfg = ctx.config();
        contestants.clear();
        eliminations.clear();
        arrowsShown = 0;
        random = new Random();
        teamMode = !TeamManager.isMerged();
        tickCounter = 0;
        arrowActive = false;
        windowMs = num(cfg, "windowStartMs", 2000);
        gapMs = num(cfg, "gapStartMs", 900);
        nextArrowTick = ticks(num(cfg, "firstDelayMs", 1500));

        Set<String> groups = new HashSet<>();
        for (ServerPlayer p : ctx.participants()) {
            String team = TeamManager.teamIdOf(p.getUUID());
            String group = (teamMode && team != null) ? team : p.getUUID().toString();
            contestants.put(p.getUUID(), new Contestant(p.getUUID(), p.getGameProfile().getName(), group));
            groups.add(group);
            sendState(p, true, 0, 0);
        }
        initialGroups = groups.size();
    }

    @Override
    public void onTick(GameContext ctx) {
        tickCounter++;
        GameConfig cfg = ctx.config();
        Zone zone = readZone(cfg);

        for (Contestant c : new ArrayList<>(contestants.values())) {
            if (!c.alive) {
                continue;
            }
            ServerPlayer p = ctx.server().getPlayerList().getPlayer(c.id);
            if (p == null) {
                continue;
            }
            if (p.isInWater() && (zone == null || inZone(p, zone))) {
                eliminate(c, p, c.falling ? "perte d'équilibre" : "tombé à l'eau");
                continue;
            }
            if (c.falling) {
                c.fallTicks--;
                if (c.fallTicks <= 0) {
                    eliminate(c, p, "perte d'équilibre (délai)");
                }
            }
        }

        if (arrowActive) {
            if (tickCounter >= arrowResolveTick) {
                resolveArrow(cfg);
            }
        } else if (tickCounter >= nextArrowTick) {
            startArrow(cfg);
        }

        checkEnd(ctx);
    }

    @Override
    public void onStop(GameContext ctx, boolean cancelled) {
        GameConfig cfg = ctx.config();
        if (!cancelled) {
            announce(cfg);
        }
        for (Contestant c : contestants.values()) {
            ServerPlayer p = online(c);
            if (p != null) {
                PoteauxNetwork.toPlayer(p, new PoteauxNetwork.ArrowPacket(-1, 0));
                PoteauxNetwork.toPlayer(p, new PoteauxNetwork.StatePacket(false, 0, 0, 0));
            }
        }
        contestants.clear();
        eliminations.clear();
        arrowActive = false;
    }

    /** Resultat envoye au moteur : vainqueurs, classement complet et details (eliminations, erreurs). */
    @Override
    public GameResult buildResult(GameContext ctx, boolean cancelled) {
        GameResult r = new GameResult();
        r.teamMode = teamMode;
        String winner = cancelled ? null : computeWinner();
        if (winner != null) {
            for (Contestant c : contestants.values()) {
                if (c.group.equals(winner)) {
                    r.winners.add(c.id);
                }
            }
            if (teamMode && TeamManager.get(winner) != null) {
                r.winnerTeam = winner;
            }
        }

        // Classement : survivants (moins d'erreurs d'abord), puis elimines du dernier au premier
        List<Contestant> survivors = new ArrayList<>();
        for (Contestant c : contestants.values()) {
            if (c.alive) {
                survivors.add(c);
            }
        }
        survivors.sort((a, b) -> Integer.compare(a.errors, b.errors));
        for (Contestant c : survivors) {
            r.placements.add(c.id);
        }
        for (int i = eliminations.size() - 1; i >= 0; i--) {
            r.placements.add(eliminations.get(i).id());
        }

        JsonArray elim = new JsonArray();
        for (Elimination e : eliminations) {
            JsonObject o = new JsonObject();
            o.addProperty("player", e.name());
            o.addProperty("secondes", Math.round(e.seconds() * 10.0) / 10.0);
            o.addProperty("raison", e.reason());
            o.addProperty("erreurs", e.errors());
            elim.add(o);
        }
        r.details.add("eliminations", elim);
        JsonObject errors = new JsonObject();
        for (Contestant c : contestants.values()) {
            errors.addProperty(c.name, c.errors);
        }
        r.details.add("erreursParJoueur", errors);
        r.details.addProperty("fleches", arrowsShown);
        return r;
    }

    /** Groupe vainqueur (le plus de joueurs encore debout), ou null en cas d'egalite ou si personne ne reste. */
    private String computeWinner() {
        Map<String, Integer> alive = new LinkedHashMap<>();
        for (Contestant c : contestants.values()) {
            if (c.alive) {
                alive.merge(c.group, 1, Integer::sum);
            }
        }
        String winner = null;
        int best = 0;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : alive.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                winner = e.getKey();
                tie = false;
            } else if (e.getValue() == best) {
                tie = true;
            }
        }
        return (winner == null || tie) ? null : winner;
    }

    // ------------------------------------------------------------ rythme

    private void startArrow(GameConfig cfg) {
        arrowDir = random.nextInt(4);
        arrowsShown++;
        arrowActive = true;
        int grace = Math.max(0, (int) Math.ceil(num(cfg, "graceMs", 150) / 50.0));
        int maxDeadline = tickCounter + ticks(windowMs) + grace;
        for (Contestant c : contestants.values()) {
            if (!c.alive || c.falling) {
                continue;
            }
            c.answered = false;
            int win = windowFor(cfg, c);
            c.deadline = tickCounter + ticks(win) + grace;
            maxDeadline = Math.max(maxDeadline, c.deadline);
            ServerPlayer p = online(c);
            if (p != null) {
                GameMessages.clearTitle(p); // evite que le GO ou un titre cache la fleche
                PoteauxNetwork.toPlayer(p, new PoteauxNetwork.ArrowPacket(arrowDir, win));
            }
        }
        arrowResolveTick = maxDeadline;
    }

    private void resolveArrow(GameConfig cfg) {
        arrowActive = false;
        for (Contestant c : new ArrayList<>(contestants.values())) {
            if (!c.alive || c.falling) {
                continue;
            }
            ServerPlayer p = online(c);
            if (p != null) {
                PoteauxNetwork.toPlayer(p, new PoteauxNetwork.ArrowPacket(-1, 0));
            }
            if (!c.answered) {
                fail(cfg, c, p);
            }
        }
        // Crescendo : la fenetre et la pause diminuent a chaque fleche
        windowMs = Math.max(num(cfg, "windowMinMs", 500), windowMs - num(cfg, "windowStepMs", 75));
        gapMs = Math.max(num(cfg, "gapMinMs", 400), gapMs - num(cfg, "gapStepMs", 40));
        nextArrowTick = tickCounter + ticks(gapMs);
    }

    public static void handleKey(ServerPlayer player, int direction) {
        INSTANCE.onKey(player, direction);
    }

    private void onKey(ServerPlayer player, int dir) {
        if (dir < 0 || dir > 3 || !arrowActive) {
            return;
        }
        Contestant c = contestants.get(player.getUUID());
        GameConfig cfg = GameManager.config();
        if (c == null || cfg == null || !c.alive || c.falling || c.answered) {
            return;
        }
        c.answered = true;
        if (dir == arrowDir && tickCounter <= c.deadline) {
            GameMessages.playSound(player, "minecraft:block.note_block.pling", 1.6f);
            sendState(player, true, c.errors, 1);
        } else {
            fail(cfg, c, player);
        }
    }

    private void fail(GameConfig cfg, Contestant c, ServerPlayer p) {
        c.errors += errorWeight(cfg, c);
        if (p == null) {
            c.alive = false; // joueur deconnecte
            eliminations.add(new Elimination(c.id, c.name, tickCounter / 20.0, "déconnecté", c.errors));
            return;
        }
        GameMessages.playSound(p, "minecraft:block.note_block.bass", 0.7f);
        sendState(p, true, c.errors, 2);
        if (c.errors >= maxErrors(cfg)) {
            startFall(cfg, c, p);
        }
    }

    private void startFall(GameConfig cfg, Contestant c, ServerPlayer p) {
        c.falling = true;
        c.fallTicks = Math.max(1, num(cfg, "fallTimeoutTicks", 80));
        // Poussee dans la direction ou regarde le joueur (selon l'orientation horizontale, donc il avance
        // toujours, meme en regardant le ciel ou le sol).
        double yaw = Math.toRadians(p.getYRot());
        double strength = dbl(cfg, "pushStrength", 0.3);
        double up = dbl(cfg, "pushUp", 0.3);
        p.setDeltaMovement(-Math.sin(yaw) * strength, up, Math.cos(yaw) * strength);
        p.hurtMarked = true;
        GameMessages.send(cfg, "fall", p);
    }

    private void eliminate(Contestant c, ServerPlayer p, String reason) {
        GameConfig cfg = GameManager.config();
        c.alive = false;
        eliminations.add(new Elimination(c.id, c.name, tickCounter / 20.0, reason, c.errors));
        PoteauxNetwork.toPlayer(p, new PoteauxNetwork.ArrowPacket(-1, 0));
        sendState(p, false, c.errors, 0);
        if (cfg != null) {
            GameMessages.send(cfg, "eliminated", p);
            for (ServerPlayer other : GameManager.onlineParticipants()) {
                if (!other.getUUID().equals(p.getUUID())) {
                    GameMessages.send(cfg, "eliminatedOther", other, p);
                }
            }
        }
        // Retour au spawn de l'equipe
        String team = TeamManager.teamIdOf(p.getUUID());
        SpawnManager.Spawn spawn = team == null ? null : SpawnManager.zoneSpawn(ID, team);
        p.fallDistance = 0.0f;
        if (spawn != null && GameManager.server() != null) {
            SpawnManager.teleport(GameManager.server(), p, spawn);
        }
        p.setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------ fin de partie

    private void checkEnd(GameContext ctx) {
        if (initialGroups <= 1) {
            return; // un seul groupe au depart (test en solo) : fin par le timer ou /ccis stop
        }
        Set<String> aliveGroups = new HashSet<>();
        for (Contestant c : contestants.values()) {
            if (c.alive) {
                aliveGroups.add(c.group);
            }
        }
        if (aliveGroups.size() <= 1) {
            ctx.end();
        }
    }

    /** Vainqueur = groupe avec le plus de joueurs encore debout. Egalite = pas de vainqueur. */
    private void announce(GameConfig cfg) {
        String winner = computeWinner();
        if (winner == null) {
            for (Contestant c : contestants.values()) {
                ServerPlayer p = online(c);
                if (p != null) {
                    p.sendSystemMessage(Txt.of("&e&lÉgalité !&7 Pas de vainqueur."));
                }
            }
            return;
        }
        for (Contestant c : contestants.values()) {
            ServerPlayer p = online(c);
            if (p != null) {
                GameMessages.send(cfg, c.group.equals(winner) ? "victory" : "defeat", p);
            }
        }
    }

    // ------------------------------------------------------------ utilitaires

    /** Feu de camp eteint : le malus de difficulte s'applique-t-il a ce joueur ? */
    private static boolean fireOut(GameConfig cfg, Contestant c) {
        return cfg.campfireDebuff && CampfireManager.isOut(TeamManager.teamIdOf(c.id));
    }

    /** Temps (ms) pour repondre : reduit si le feu de camp de l'equipe est eteint. */
    private int windowFor(GameConfig cfg, Contestant c) {
        if (!fireOut(cfg, c)) {
            return windowMs;
        }
        return Math.max(150, (int) Math.round(windowMs * dbl(cfg, "campfireWindowMultiplier", 0.75)));
    }

    /** Poids d'une erreur : plus lourd si le feu de camp de l'equipe est eteint. */
    private static int errorWeight(GameConfig cfg, Contestant c) {
        return fireOut(cfg, c) ? Math.max(1, num(cfg, "campfireErrorWeight", 2)) : 1;
    }

    private static ServerPlayer online(Contestant c) {
        if (GameManager.server() == null) {
            return null;
        }
        return GameManager.server().getPlayerList().getPlayer(c.id);
    }

    private static void sendState(ServerPlayer p, boolean active, int errors, int result) {
        GameConfig cfg = GameManager.config();
        PoteauxNetwork.toPlayer(p, new PoteauxNetwork.StatePacket(active, errors, maxErrors(cfg), result));
    }

    private static int maxErrors(GameConfig cfg) {
        return cfg == null ? 3 : Math.max(1, num(cfg, "maxErrors", 3));
    }

    /** Millisecondes -> ticks serveur (50 ms par tick), au moins 1. */
    private static int ticks(int ms) {
        return Math.max(1, (int) Math.ceil(ms / 50.0));
    }

    private static int num(GameConfig cfg, String key, int def) {
        if (cfg != null && cfg.settings != null && cfg.settings.has(key) && cfg.settings.get(key).isJsonPrimitive()) {
            try {
                return cfg.settings.get(key).getAsInt();
            } catch (Exception ignored) {
                // valeur invalide : on garde la valeur par defaut
            }
        }
        return def;
    }

    private static double dbl(GameConfig cfg, String key, double def) {
        if (cfg != null && cfg.settings != null && cfg.settings.has(key) && cfg.settings.get(key).isJsonPrimitive()) {
            try {
                return cfg.settings.get(key).getAsDouble();
            } catch (Exception ignored) {
                // valeur invalide : on garde la valeur par defaut
            }
        }
        return def;
    }

    /** Poteau sous les pieds du joueur, ou null. */
    private static BlockPos poleOf(ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        if (p.level().getBlockState(pos).getBlock() instanceof PoteauBlock) {
            return pos;
        }
        BlockPos below = pos.below();
        if (p.level().getBlockState(below).getBlock() instanceof PoteauBlock) {
            return below;
        }
        return null;
    }

    // ------------------------------------------------------------ zone

    private static JsonObject corner(GameConfig cfg, String key) {
        if (cfg == null || cfg.settings == null || !cfg.settings.has(key) || !cfg.settings.get(key).isJsonObject()) {
            return null;
        }
        return cfg.settings.getAsJsonObject(key);
    }

    private static Zone readZone(GameConfig cfg) {
        JsonObject a = corner(cfg, "zonePos1");
        JsonObject b = corner(cfg, "zonePos2");
        if (a == null || b == null) {
            return null;
        }
        try {
            String dimension = a.get("dimension").getAsString();
            if (!dimension.equals(b.get("dimension").getAsString())) {
                return null;
            }
            int x1 = a.get("x").getAsInt();
            int y1 = a.get("y").getAsInt();
            int z1 = a.get("z").getAsInt();
            int x2 = b.get("x").getAsInt();
            int y2 = b.get("y").getAsInt();
            int z2 = b.get("z").getAsInt();
            AABB box = new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, Math.max(z1, z2) + 1);
            return new Zone(dimension, box);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean inZone(ServerPlayer p, Zone zone) {
        return p.level().dimension().location().toString().equals(zone.dimension())
                && zone.box().contains(p.getX(), p.getY(), p.getZ());
    }

    private static String describeCorner(GameConfig cfg, String key) {
        JsonObject c = corner(cfg, key);
        if (c == null) {
            return "non défini";
        }
        try {
            return c.get("dimension").getAsString() + " " + c.get("x").getAsInt() + " "
                    + c.get("y").getAsInt() + " " + c.get("z").getAsInt();
        } catch (Exception e) {
            return "invalide";
        }
    }

    // ------------------------------------------------------------ commandes : /ccis poteaux ...

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal(ID)
                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                .then(Commands.literal("zone")
                        .then(Commands.literal("pos1").executes(c -> setCorner(c, 1)))
                        .then(Commands.literal("pos2").executes(c -> setCorner(c, 2)))
                        .then(Commands.literal("info").executes(this::zoneInfo)))
                .then(Commands.literal("check").executes(this::check));
    }

    private int setCorner(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        GameConfig cfg = GameRegistry.config(ID);
        if (cfg == null) {
            ctx.getSource().sendFailure(Component.literal("Config des poteaux introuvable."));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        JsonObject corner = new JsonObject();
        corner.addProperty("dimension", player.level().dimension().location().toString());
        corner.addProperty("x", pos.getX());
        corner.addProperty("y", pos.getY());
        corner.addProperty("z", pos.getZ());
        cfg.settings.add("zonePos" + n, corner);
        GameRegistry.saveConfig(ID);
        String msg = "Coin " + n + " de la zone défini : " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private int zoneInfo(CommandContext<CommandSourceStack> ctx) {
        GameConfig cfg = GameRegistry.config(ID);
        String text = "Zone : coin 1 = " + describeCorner(cfg, "zonePos1")
                + " | coin 2 = " + describeCorner(cfg, "zonePos2");
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** Indique qui est sur un poteau parmi les participants (a utiliser avant /ccis go). */
    private int check(CommandContext<CommandSourceStack> ctx) {
        List<ServerPlayer> list = GameManager.onlineParticipants();
        if (list.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Aucun participant : lance /ccis start poteaux et attends que les joueurs rejoignent."));
            return 0;
        }
        for (ServerPlayer p : list) {
            BlockPos pole = poleOf(p);
            Component value = pole == null
                    ? Component.literal("pas sur un poteau").withStyle(ChatFormatting.RED)
                    : Component.literal("poteau " + pole.getX() + " " + pole.getY() + " " + pole.getZ())
                            .withStyle(ChatFormatting.GREEN);
            Component row = Component.empty()
                    .append(Component.literal(p.getGameProfile().getName() + " : ")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
                    .append(value);
            ctx.getSource().sendSuccess(() -> row, false);
        }
        return list.size();
    }
}
