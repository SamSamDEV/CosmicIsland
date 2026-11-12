package fr.cosmicisland.game;

import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.KLRole;
import fr.cosmicisland.RoleManager;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Moteur des epreuves. Une seule epreuve a la fois.
 * Etats : IDLE -> INVITE -> (READY) -> COUNTDOWN -> RUNNING -> IDLE
 */
public final class GameManager {

    public enum State { IDLE, INVITE, READY, COUNTDOWN, RUNNING }

    private static MinecraftServer server;
    private static KLGame game;
    private static GameConfig cfg;
    private static State state = State.IDLE;

    private static final Set<UUID> invited = new LinkedHashSet<>();
    private static final Set<UUID> joined = new LinkedHashSet<>();
    private static final Set<UUID> declined = new HashSet<>();

    private static int ticks;
    private static int countdownValue;

    private static boolean freezeActive = false;
    private static final Map<UUID, double[]> anchors = new HashMap<>();
    private static ServerBossEvent timerBar;

    private static final GameContext CONTEXT = new GameContext();

    private GameManager() {}

    // ------------------------------------------------------------ cycle de vie

    public static void init(MinecraftServer s) {
        server = s;
    }

    public static void shutdown() {
        if (server != null && state != State.IDLE) {
            finish(true);
        }
        server = null;
    }

    // ------------------------------------------------------------ acces

    public static State state() {
        return state;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static GameConfig config() {
        return cfg;
    }

    public static boolean isActive(String gameId) {
        return game != null && game.id().equalsIgnoreCase(gameId);
    }

    public static boolean isInGame(UUID id) {
        return state != State.IDLE && joined.contains(id);
    }

    /** Flags de l'epreuve si le joueur y participe, sinon null (aucune restriction). */
    public static GameConfig.Flags flagsFor(ServerPlayer player) {
        if (cfg != null && isInGame(player.getUUID())) {
            return cfg.flags;
        }
        return null;
    }

    public static List<ServerPlayer> onlineParticipants() {
        List<ServerPlayer> list = new ArrayList<>();
        if (server == null) {
            return list;
        }
        for (UUID id : joined) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    public static String describe() {
        if (state == State.IDLE || cfg == null) {
            return "Aucune";
        }
        String label = switch (state) {
            case INVITE -> "invitations";
            case READY -> "prêt";
            case COUNTDOWN -> "décompte";
            case RUNNING -> "en cours";
            default -> "";
        };
        return cfg.displayName + " (" + label + ", " + joined.size() + " joueur(s))";
    }

    // ------------------------------------------------------------ commandes

    /** Lance les invitations. Retourne un message d'erreur, ou null si tout va bien. */
    public static String start(String gameId) {
        if (server == null) {
            return "Le serveur n'est pas prêt.";
        }
        if (state != State.IDLE) {
            return "Une épreuve est déjà en cours : " + describe() + " (utilise /kl stop pour l'arrêter).";
        }
        KLGame g = GameRegistry.get(gameId);
        GameConfig c = GameRegistry.config(gameId);
        if (g == null || c == null) {
            return "Jeu inconnu : " + gameId;
        }
        if (!c.enabled) {
            return "Le jeu " + c.displayName + " est désactivé (/kl enable " + gameId + ").";
        }

        List<UUID> targets = new ArrayList<>();
        Set<String> missingSpawns = new LinkedHashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RoleManager.effective(p) != KLRole.PARTICIPANT) {
                continue;
            }
            String team = TeamManager.teamIdOf(p.getUUID());
            if (team == null || !c.allowedTeams.contains(team)) {
                continue;
            }
            if (SpawnManager.get(team) == null) {
                missingSpawns.add(team);
            }
            targets.add(p.getUUID());
        }
        if (targets.isEmpty()) {
            return "Aucun participant à inviter (il faut le rôle participant et une équipe autorisée pour ce jeu).";
        }
        if (!missingSpawns.isEmpty()) {
            return "Spawn manquant pour : " + String.join(", ", missingSpawns)
                    + " (utilise /kl spawn set <équipe> à l'endroit voulu).";
        }

        game = g;
        cfg = c;
        state = State.INVITE;
        ticks = Math.max(1, c.inviteSeconds) * 20;
        invited.clear();
        joined.clear();
        declined.clear();
        invited.addAll(targets);

        for (UUID id : targets) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                sendInvite(p);
            }
        }
        notifyStaff("Invitations envoyées à &f" + targets.size() + "&7 joueur(s) pour &f" + c.displayName
                + "&7. Lance le départ avec &f/kl go&7 quand tout le monde est prêt.");
        return null;
    }

    /** Un joueur invité clique sur [REJOINDRE]. */
    public static String join(ServerPlayer player) {
        UUID id = player.getUUID();
        if (state != State.INVITE) {
            return "Aucune invitation en cours.";
        }
        if (!invited.contains(id)) {
            return "Tu n'es pas invité à cette épreuve.";
        }
        if (joined.contains(id)) {
            return "Tu as déjà rejoint l'épreuve.";
        }
        String team = TeamManager.teamIdOf(id);
        SpawnManager.Spawn spawn = team == null ? null : SpawnManager.get(team);
        if (spawn == null) {
            return "Ton équipe n'a pas de point de spawn : préviens un organisateur.";
        }
        if (SpawnManager.level(server, spawn.dimension) == null) {
            return "Le spawn de ton équipe est dans une dimension introuvable : préviens un organisateur.";
        }

        InventoryStore.save(player);
        if (cfg.clearInventoryOnJoin) {
            player.getInventory().clearContent();
        }
        giveKit(player);
        player.setGameMode(GameType.byName(cfg.gameMode, GameType.SURVIVAL));
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        SpawnManager.teleport(server, player, spawn);

        declined.remove(id);
        joined.add(id);
        GameMessages.send(cfg, "joined", player);
        checkAllAnswered();
        return null;
    }

    /** Un joueur invité clique sur [NE PAS REJOINDRE]. */
    public static String decline(ServerPlayer player) {
        UUID id = player.getUUID();
        if (state != State.INVITE) {
            return "Aucune invitation en cours.";
        }
        if (!invited.contains(id)) {
            return "Tu n'es pas invité à cette épreuve.";
        }
        if (joined.contains(id)) {
            return "Tu as déjà rejoint l'épreuve.";
        }
        declined.add(id);
        GameMessages.send(cfg, "declined", player);
        checkAllAnswered();
        return null;
    }

    /** Lance le decompte. Retourne un message d'erreur, ou null. */
    public static String go() {
        if (state != State.INVITE && state != State.READY) {
            return "Aucune épreuve prête à démarrer (utilise /kl start <jeu>).";
        }
        if (joined.isEmpty()) {
            return "Personne n'a rejoint l'épreuve pour l'instant.";
        }
        try {
            String refusal = game.canStart(CONTEXT);
            if (refusal != null) {
                return refusal;
            }
        } catch (Exception e) {
            CosmicIsland.LOGGER.error("Erreur dans canStart du jeu {}", game.id(), e);
            return "Erreur interne pendant la vérification du jeu (voir les logs).";
        }
        state = State.COUNTDOWN;
        ticks = 0;
        countdownValue = Math.max(0, cfg.countdownSeconds);
        if (cfg.freezeDuringCountdown) {
            freezeActive = true;
        }
        return null;
    }

    /** Termine l'epreuve, restaure les inventaires et renvoie les joueurs. */
    public static void finish(boolean cancelled) {
        if (state == State.IDLE || server == null) {
            return;
        }
        KLGame g = game;
        GameConfig c = cfg;
        boolean wasRunning = state == State.RUNNING;

        if (wasRunning) {
            try {
                g.onStop(CONTEXT, cancelled);
            } catch (Exception e) {
                CosmicIsland.LOGGER.error("Erreur dans onStop du jeu {}", g.id(), e);
            }
        }

        for (UUID id : new ArrayList<>(joined)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                continue; // restaure automatiquement a la prochaine connexion
            }
            GameMessages.send(c, cancelled ? "stop" : "end", p);
            InventoryStore.restore(server, p);
        }

        unfreeze();
        removeTimerBar();
        String name = c.displayName;
        state = State.IDLE;
        game = null;
        cfg = null;
        invited.clear();
        joined.clear();
        declined.clear();
        notifyStaff((cancelled ? "&cÉpreuve arrêtée" : "&aÉpreuve terminée") + "&7 : &f" + name);
    }

    // ------------------------------------------------------------ tick

    public static void tick() {
        if (state == State.IDLE || server == null || cfg == null) {
            return;
        }
        switch (state) {
            case INVITE -> {
                ticks--;
                if (ticks <= 0) {
                    state = State.READY;
                    notifyStaff("Invitations terminées : &a" + joined.size() + "&7/&f" + invited.size()
                            + "&7 ont rejoint. Utilise &f/kl go&7 pour lancer le décompte.");
                }
            }
            case COUNTDOWN -> tickCountdown();
            case RUNNING -> tickRunning();
            default -> { }
        }

        if (freezeActive && state != State.IDLE && state != State.RUNNING) {
            applyFreeze();
        }

        // Faim : si le flag est a false, on garde les joueurs rassasies
        if (cfg != null && state != State.IDLE && !cfg.flags.hunger && server.getTickCount() % 20 == 0) {
            for (ServerPlayer p : onlineParticipants()) {
                p.getFoodData().setFoodLevel(20);
                p.getFoodData().setSaturation(20.0f);
            }
        }
    }

    private static void tickCountdown() {
        if (ticks % 20 == 0) {
            if (countdownValue > 0) {
                Component title = Txt.of(cfg.countdownFormat.replace("{n}", String.valueOf(countdownValue)));
                for (ServerPlayer p : onlineParticipants()) {
                    GameMessages.showTitle(p, title, Component.empty(), 0, 15, 5);
                    GameMessages.playSound(p, cfg.countdownSound, 1.0f);
                }
                countdownValue--;
            } else {
                for (ServerPlayer p : onlineParticipants()) {
                    GameMessages.sendWithTitleTimes(cfg, "start", p, 0, cfg.goTitleStayTicks, 5);
                    GameMessages.playSound(p, cfg.goSound, 2.0f);
                }
                unfreeze();
                startRunning();
                return;
            }
        }
        ticks++;
    }

    private static void startRunning() {
        state = State.RUNNING;
        ticks = 0;
        updateTimerBar();
        try {
            game.onStart(CONTEXT);
        } catch (Exception e) {
            CosmicIsland.LOGGER.error("Erreur dans onStart du jeu {}", game.id(), e);
            finish(true);
        }
    }

    private static void tickRunning() {
        try {
            game.onTick(CONTEXT);
        } catch (Exception e) {
            CosmicIsland.LOGGER.error("Erreur dans onTick du jeu {} : arret de l'epreuve", game.id(), e);
            finish(true);
            return;
        }
        if (state != State.RUNNING) {
            return; // le jeu s'est termine lui-meme via ctx.end()
        }
        ticks++;
        if (cfg.showTimer && ticks % 20 == 0) {
            updateTimerBar();
        }
        if (cfg.reminderSeconds > 0 && ticks % (cfg.reminderSeconds * 20) == 0) {
            for (ServerPlayer p : onlineParticipants()) {
                GameMessages.send(cfg, "reminder", p);
            }
        }
        if (cfg.durationSeconds > 0 && ticks >= cfg.durationSeconds * 20) {
            finish(false);
        }
    }

    // ------------------------------------------------------------ freeze

    /** Immobilise les participants (utile avant /kl go pour un depart simultane). */
    public static String freeze() {
        if (state == State.IDLE) {
            return "Aucune épreuve en cours.";
        }
        if (state == State.RUNNING) {
            return "L'épreuve a déjà démarré.";
        }
        freezeActive = true;
        notifyStaff("Participants &bimmobilisés&7. Ils seront libérés au GO du &f/kl go&7 (ou avec &f/kl unfreeze&7).");
        return null;
    }

    public static void unfreeze() {
        freezeActive = false;
        anchors.clear();
    }

    private static void applyFreeze() {
        for (ServerPlayer p : onlineParticipants()) {
            double[] a = anchors.computeIfAbsent(p.getUUID(), k -> new double[] {p.getX(), p.getY(), p.getZ()});
            double dx = p.getX() - a[0];
            double dz = p.getZ() - a[2];
            double dy = p.getY() - a[1];
            if (dx * dx + dz * dz > 0.0004 || dy > 0.01) {
                p.connection.teleport(a[0], a[1], a[2], p.getYRot(), p.getXRot());
            }
        }
    }

    // ------------------------------------------------------------ timer

    private static void updateTimerBar() {
        if (cfg == null || !cfg.showTimer) {
            return;
        }
        boolean limited = cfg.durationSeconds > 0;
        int elapsed = ticks / 20;
        int shown = limited ? Math.max(0, cfg.durationSeconds - elapsed) : elapsed;
        String time = String.format("%02d:%02d", shown / 60, shown % 60);
        String label = limited ? cfg.timerLabelRemaining : cfg.timerLabelElapsed;
        if (timerBar == null) {
            timerBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS);
        }
        timerBar.setName(Txt.of(label.replace("{time}", time)));
        timerBar.setProgress(limited ? Math.max(0.0f, Math.min(1.0f, (float) shown / cfg.durationSeconds)) : 1.0f);
        for (ServerPlayer p : onlineParticipants()) {
            timerBar.addPlayer(p);
        }
    }

    private static void removeTimerBar() {
        if (timerBar != null) {
            timerBar.removeAllPlayers();
            timerBar = null;
        }
    }

    // ------------------------------------------------------------ utilitaires

    private static void checkAllAnswered() {
        if (joined.size() + declined.size() >= invited.size()) {
            notifyStaff("Tous les joueurs ont répondu : &a" + joined.size()
                    + "&7 participant(s). Utilise &f/kl go&7 pour lancer le décompte.");
        }
    }

    private static void sendInvite(ServerPlayer player) {
        GameMessages.send(cfg, "invite", player);
        MutableComponent buttons = Component.empty()
                .append(button(cfg.buttonJoin, ChatFormatting.GREEN, "/kl join", "Clique pour rejoindre l'épreuve"))
                .append(Component.literal("   "))
                .append(button(cfg.buttonDecline, ChatFormatting.RED, "/kl decline", "Clique pour ne pas participer"));
        player.sendSystemMessage(buttons);
    }

    private static Component button(String label, ChatFormatting color, String command, String hover) {
        Style style = Style.EMPTY
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover)));
        return Component.literal(label).withStyle(style);
    }

    private static void giveKit(ServerPlayer player) {
        for (GameConfig.KitItem kit : cfg.kit) {
            ResourceLocation id = ResourceLocation.tryParse(kit.item);
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                CosmicIsland.LOGGER.warn("Objet de kit inconnu dans la config : {}", kit.item);
                continue;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, kit.count));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** Message aux organisateurs/admins connectes et a la console. */
    private static void notifyStaff(String text) {
        if (server == null) {
            return;
        }
        Component msg = Component.empty()
                .append(Component.literal("[Cosmic Island] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Txt.of(text).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR) {
                p.sendSystemMessage(msg);
            }
        }
        server.sendSystemMessage(msg);
    }
}
