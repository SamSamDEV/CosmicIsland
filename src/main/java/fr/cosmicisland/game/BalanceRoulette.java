package fr.cosmicisland.game;

import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import fr.cosmicisland.conseil.ConseilMessages;
import fr.cosmicisland.gui.KLMenu;
import fr.cosmicisland.gui.MenuItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Roulette d'equilibrage : quand une equipe a plus de joueurs que la plus petite, un joueur est tire au sort
 * (facon caisse Counter-Strike : les tetes defilent puis ralentissent) et dispense de l'epreuve.
 */
public final class BalanceRoulette {
    private BalanceRoulette() {}

    /** Un tirage : equipe + joueurs candidats (uuid -> pseudo). */
    public record Draw(String teamId, List<UUID> ids, List<String> names, int remainingAfter) {}

    private static final String TAG = "roulette";
    private static final int STEPS = 42;
    private static final Random RANDOM = new Random();

    private static final Deque<Draw> queue = new ArrayDeque<>();
    private static Draw current;
    private static SimpleContainer box;
    private static List<Integer> strip;     // indices de candidats
    private static int winner;
    private static int step;
    private static int wait;
    private static int endWait;
    private static boolean finishing;

    public static boolean running() {
        return current != null || !queue.isEmpty();
    }

    public static void start(List<Draw> draws) {
        queue.clear();
        queue.addAll(draws);
        current = null;
    }

    private static final String[] FAKE = {"Notch", "jeb_", "Dinnerbone", "Grumm", "Steve", "Alex", "Herobrine", "Technoblade", "Dream", "Mumbo"};

    /** Test en solo : tirage avec des joueurs fictifs (personne n'est dispense, rien n'est enregistre). */
    public static String test(int count) {
        if (running()) {
            return "La roulette est déjà en cours.";
        }
        String teamId = TeamManager.all().isEmpty() ? "test" : TeamManager.all().keySet().iterator().next();
        int n = Math.max(2, Math.min(FAKE.length, count));
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(UUID.randomUUID());
            names.add(FAKE[i]);
        }
        queue.add(new Draw(teamId, ids, names, 0));
        return null;
    }

    public static void enqueue(Draw draw) {
        queue.add(draw);
    }

    public static void cancel(MinecraftServer server) {
        queue.clear();
        if (current != null && server != null) {
            for (ServerPlayer p : audience(server)) {
                closeFor(p);
            }
        }
        current = null;
    }

    private static List<ServerPlayer> audience(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (GameManager.isInGame(p.getUUID()) || KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR) {
                out.add(p);
            }
        }
        return out;
    }

    private static void closeFor(ServerPlayer p) {
        if (p.containerMenu instanceof KLMenu menu && TAG.equals(menu.getTag())) {
            p.closeContainer();
        }
    }

    public static void tick() {
        MinecraftServer server = GameManager.server();
        if (server == null) {
            return;
        }
        if (current == null) {
            if (queue.isEmpty()) {
                return;
            }
            begin(server, queue.poll());
            return;
        }
        if (finishing) {
            if (--endWait <= 0) {
                for (ServerPlayer p : audience(server)) {
                    closeFor(p);
                }
                GameManager.dispense(current.ids().get(winner));
                current = null;
            }
            return;
        }
        if (--wait > 0) {
            return;
        }
        step++;
        render(server);
        for (ServerPlayer p : audience(server)) {
            GameMessages.playSound(p, "minecraft:ui.button.click", 1.0f + 0.6f * step / STEPS);
        }
        if (step >= STEPS - 1) {
            finishing = true;
            endWait = 70;
            String name = current.names().get(winner);
            TeamManager.KLTeam team = TeamManager.get(current.teamId());
            GameConfig.Msg msg = new GameConfig.Msg("&6&l" + name, "title");
            msg.subtitle = "&7est dispensé de l'épreuve pour équilibrer les équipes";
            msg.sound = "minecraft:entity.player.levelup";
            msg.stay = 60;
            for (ServerPlayer p : audience(server)) {
                ConseilMessages.send(msg, p, Map.of());
                p.sendSystemMessage(Txt.of("&6[Roulette] &f" + name + "&7 est dispensé de l'épreuve (équipe "
                        + (team == null ? current.teamId() : team.name) + ")."));
            }
            return;
        }
        double t = (double) step / (STEPS - 1);
        wait = 1 + (int) Math.floor(Math.pow(t, 3) * 11);
    }

    private static void begin(MinecraftServer server, Draw draw) {
        current = draw;
        winner = RANDOM.nextInt(draw.ids().size());
        strip = new ArrayList<>();
        int last = -1;
        for (int i = 0; i < STEPS; i++) {
            int idx;
            do {
                idx = RANDOM.nextInt(draw.ids().size());
            } while (draw.ids().size() > 1 && idx == last);
            strip.add(idx);
            last = idx;
        }
        if (draw.ids().size() > 1 && strip.get(STEPS - 2) == winner) {
            strip.set(STEPS - 2, (winner + 1) % draw.ids().size());
        }
        strip.set(STEPS - 1, winner);
        step = 0;
        wait = 1;
        finishing = false;
        box = new SimpleContainer(27);
        TeamManager.KLTeam team = TeamManager.get(draw.teamId());
        String title = "&6&lRoulette &7- " + (team == null ? draw.teamId() : team.name);
        List<ServerPlayer> viewers = audience(server);
        for (int i = 0; i < 27; i++) {
            box.setItem(i, MenuItems.named(Items.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        }
        box.setItem(4, MenuItems.named(Items.HOPPER, "&e▼", List.of()));
        box.setItem(22, MenuItems.named(Items.END_ROD, "&e▲", List.of()));
        render(server);
        for (ServerPlayer p : viewers) {
            KLMenu.open(p, Txt.of(title), 3, box, TAG, (pl, slot) -> { });
            p.sendSystemMessage(Txt.of("&6[Roulette] &7" + (team == null ? draw.teamId() : team.name)
                    + " : un joueur va être dispensé pour équilibrer les équipes..."));
        }
    }

    /** Affiche 9 tetes centrees sur le candidat courant. */
    private static void render(MinecraftServer server) {
        int n = current.ids().size();
        for (int col = 0; col < 9; col++) {
            int offset = col - 4;
            int pos = step + offset;
            int idx;
            if (pos < 0) {
                idx = Math.floorMod(winner + pos * 7, n);
            } else if (pos >= STEPS) {
                idx = Math.floorMod(winner + (pos - STEPS + 1), n);
            } else {
                idx = strip.get(pos);
            }
            String name = current.names().get(idx);
            boolean center = col == 4;
            var head = MenuItems.head(name, (center ? "&e&l" : "&7") + name, List.of());
            if (center && finishing) {
                MenuItems.glow(head);
            }
            box.setItem(9 + col, head);
        }
    }
}
