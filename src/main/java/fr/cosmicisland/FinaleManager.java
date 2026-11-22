package fr.cosmicisland;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.cosmicisland.game.GameMessages;
import fr.cosmicisland.conseil.ConseilNetwork;
import fr.cosmicisland.game.SpawnManager;
import fr.cosmicisland.gui.KLMenu;
import fr.cosmicisland.gui.MenuItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jury et finale. Les joueurs elimines au conseil apres la fusion forment le jury (il ne vote qu'a la fin).
 * Deroulement : /ccis finale start (3 joueurs en jeu) -> epreuve des poteaux -> /ccis finale winner <joueur> ->
 * le vainqueur choisit son adversaire dans le chat (l'autre est elimine et rejoint le jury) -> /ccis finale vote start
 * (vote secret du jury, par clic) -> /ccis finale reveal (depouillement un par un, puis le vainqueur de l'aventure).
 */
public final class FinaleManager {
    private FinaleManager() {}

    public enum Stage { NONE, POTEAUX, CHOICE, READY, VOTING, REVEAL, DONE }

    private static SeasonManager.Data d() {
        return SeasonManager.data();
    }

    public static Stage stage() {
        try {
            return Stage.valueOf(d().finaleStage);
        } catch (IllegalArgumentException e) {
            return Stage.NONE;
        }
    }

    private static void setStage(Stage s) {
        d().finaleStage = s.name();
        SeasonManager.save();
    }

    private static String t(String key, Object... args) {
        return Tr.s(key, args);
    }

    // ------------------------------------------------------------ jury

    /** Seuls les joueurs qui ont fait partie de l'equipe de fusion (reunification) forment le jury. */
    private static boolean wasInMergedTeam(UUID id) {
        return TeamManager.MERGED_ID.equals(TeamManager.teamIdOf(id));
    }

    /** Appele a la cloture d'un conseil : un elimine qui etait dans l'equipe de fusion rejoint le jury. */
    public static void addJuror(UUID id, String name) {
        if (wasInMergedTeam(id)) {
            d().jury.put(id.toString(), name);
            SeasonManager.save();
        }
    }

    public static void showJury(CommandSourceStack src) {
        if (d().jury.isEmpty()) {
            src.sendSystemMessage(Txt.of(t("finale.jury.empty")));
            return;
        }
        src.sendSystemMessage(Txt.of(t("finale.jury.list", d().jury.size(), String.join(", ", d().jury.values()))));
    }

    // ------------------------------------------------------------ utilitaires

    private static void broadcast(MinecraftServer server, Component c) {
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(c);
            }
        }
    }

    private static void title(MinecraftServer server, String title, String sub, String sound) {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            GameMessages.showTitle(p, Txt.of(title), Txt.of(sub), 10, 70, 20);
            GameMessages.playSound(p, sound, 1.0f);
        }
    }

    private static UUID findUuid(Map<String, String> byUuid, String name) {
        for (Map.Entry<String, String> e : byUuid.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return UUID.fromString(e.getKey());
            }
        }
        return null;
    }

    private static UUID findAlive(String name) {
        for (Map.Entry<UUID, String> e : RoleManager.participants().entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static MutableComponent clickable(String label, ChatFormatting color, String command, String hover) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Txt.of(hover))));
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendFailure(Txt.err(text));
        return 0;
    }

    private static int ok(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Txt.ok(text), true);
        return 1;
    }

    private static boolean wrongStage(CommandContext<CommandSourceStack> ctx, Stage... allowed) {
        for (Stage s : allowed) {
            if (stage() == s) {
                return false;
            }
        }
        fail(ctx, t("finale.err.state", t("finale.stage." + stage().name().toLowerCase())));
        return true;
    }

    // ------------------------------------------------------------ deroulement

    private static int start(CommandContext<CommandSourceStack> ctx) {
        if (stage() != Stage.NONE && stage() != Stage.DONE) {
            return fail(ctx, t("finale.err.state", t("finale.stage." + stage().name().toLowerCase())));
        }
        Map<UUID, String> alive = RoleManager.participants();
        if (alive.size() != 3) {
            return fail(ctx, t("finale.start.fail", alive.size()));
        }
        SeasonManager.Data data = d();
        data.finalists = new java.util.LinkedHashMap<>();
        data.votes = new java.util.LinkedHashMap<>();
        data.potWinner = "";
        SeasonManager.setPhase(SeasonManager.Phase.FINALE);
        setStage(Stage.POTEAUX);
        SeasonHistory.log("finale", "La finale commence avec " + String.join(", ", alive.values()) + ".");
        MinecraftServer server = ctx.getSource().getServer();
        title(server, t("finale.start.title"), t("finale.start.sub"), "minecraft:ui.toast.challenge_complete");
        return ok(ctx, t("finale.start.ok", String.join(", ", alive.values())));
    }

    /** Designe le gagnant de l'epreuve : il choisit son adversaire dans le chat. */
    private static int winner(CommandContext<CommandSourceStack> ctx) {
        if (wrongStage(ctx, Stage.POTEAUX)) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "player");
        UUID id = findAlive(name);
        if (id == null) {
            return fail(ctx, t("finale.winner.fail", name));
        }
        d().potWinner = id.toString();
        setStage(Stage.CHOICE);
        MinecraftServer server = ctx.getSource().getServer();
        String realName = RoleManager.participants().get(id);
        SeasonHistory.log("finale", realName + " remporte l'épreuve de la finale.");
        broadcast(server, Txt.of(t("finale.winner.ok", realName)));
        ServerPlayer winner = server.getPlayerList().getPlayer(id);
        if (winner != null) {
            MutableComponent line = Component.empty().append(Txt.of(t("finale.choose.prompt")));
            for (Map.Entry<UUID, String> e : RoleManager.participants().entrySet()) {
                if (!e.getKey().equals(id)) {
                    line.append(clickable(e.getValue(), ChatFormatting.GOLD, "/ccis finale pick " + e.getValue(),
                            t("finale.choose.hover", e.getValue()))).append(Component.literal("  "));
                }
            }
            winner.sendSystemMessage(line);
        } else {
            ctx.getSource().sendSystemMessage(Txt.of(t("finale.choose.offline", realName)));
        }
        return 1;
    }

    /** Clic du gagnant des poteaux : son adversaire est choisi, le troisieme est elimine et rejoint le jury. */
    private static int pick(CommandContext<CommandSourceStack> ctx) {
        if (stage() != Stage.CHOICE) {
            return fail(ctx, t("finale.pick.fail"));
        }
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return fail(ctx, t("finale.pick.fail"));
        }
        boolean staff = KLPermissions.levelOf(player) >= KLPermissions.ORGANISATEUR;
        if (!player.getUUID().toString().equals(d().potWinner) && !staff) {
            return fail(ctx, t("finale.pick.fail"));
        }
        String name = StringArgumentType.getString(ctx, "player");
        UUID chosen = findAlive(name);
        UUID winnerId = UUID.fromString(d().potWinner);
        if (chosen == null || chosen.equals(winnerId)) {
            return fail(ctx, t("finale.pick.invalid"));
        }
        Map<UUID, String> alive = RoleManager.participants();
        UUID out = null;
        for (UUID id : alive.keySet()) {
            if (!id.equals(winnerId) && !id.equals(chosen)) {
                out = id;
            }
        }
        MinecraftServer server = ctx.getSource().getServer();
        d().finalists = new java.util.LinkedHashMap<>();
        d().finalists.put(winnerId.toString(), alive.get(winnerId));
        d().finalists.put(chosen.toString(), alive.get(chosen));
        if (out != null) {
            String outName = alive.get(out);
            RoleManager.set(new GameProfile(out, outName), KLRole.SPECTATEUR);
            ServerPlayer outPlayer = server.getPlayerList().getPlayer(out);
            if (outPlayer != null) {
                RoleManager.apply(outPlayer, KLRole.SPECTATEUR);
                SpawnManager.Spawn spawn = SpawnManager.get("elimination");
                if (spawn != null) {
                    SpawnManager.teleport(server, outPlayer, spawn);
                }
            }
            SeasonHistory.log("elimination", outName + " est éliminé en finale (hors jury).");
            broadcast(server, Txt.of(t("finale.pick.out.nojury", outName)));
        }
        setStage(Stage.READY);
        List<String> names = new ArrayList<>(d().finalists.values());
        SeasonHistory.log("finale", "Finalistes : " + names.get(0) + " et " + names.get(1) + ".");
        title(server, t("finale.pick.title"), t("finale.pick.sub", names.get(0), names.get(1)), "minecraft:ui.toast.challenge_complete");
        return 1;
    }

    private static int voteStart(CommandContext<CommandSourceStack> ctx) {
        if (wrongStage(ctx, Stage.READY, Stage.VOTING)) {
            return 0;
        }
        if (d().finalists.size() != 2 || d().jury.isEmpty()) {
            return fail(ctx, t("finale.vote.start.fail"));
        }
        MinecraftServer server = ctx.getSource().getServer();
        d().votes = new java.util.LinkedHashMap<>();
        setStage(Stage.VOTING);
        SpawnManager.Spawn jurySpawn = SpawnManager.get("jury");
        for (String uuid : d().jury.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(uuid));
            if (p == null) {
                continue;
            }
            if (jurySpawn != null) {
                SpawnManager.teleport(server, p, jurySpawn);
            }
            p.sendSystemMessage(Txt.of(t("finale.vote.prompt")));
            openVoteMenu(p);
        }
        return ok(ctx, t("finale.vote.started.staff", d().jury.size()));
    }

    // ------------------------------------------------------------ menu de vote (meme style que le conseil)

    private static void openVoteMenu(ServerPlayer player) {
        String me = player.getUUID().toString();
        String current = d().votes.get(me);
        SimpleContainer container = new SimpleContainer(27);
        int[] slots = {11, 15};
        java.util.Map<Integer, String> bySlot = new HashMap<>();
        int i = 0;
        for (Map.Entry<String, String> e : d().finalists.entrySet()) {
            boolean chosen = e.getKey().equals(current);
            List<String> lore = new ArrayList<>();
            lore.add(t("finale.menu.lore"));
            if (chosen) {
                lore.add("");
                lore.add(t("finale.menu.current"));
            }
            ItemStack head = MenuItems.head(e.getValue(), (chosen ? "&a&l" : "&f&l") + e.getValue(), lore);
            if (chosen) {
                MenuItems.glow(head);
            }
            container.setItem(slots[i], head);
            bySlot.put(slots[i], e.getKey());
            i++;
        }
        String recap = current == null ? t("finale.menu.none") : t("finale.menu.yours", d().finalists.getOrDefault(current, "?"));
        container.setItem(22, MenuItems.named(Items.PAPER, t("finale.menu.choice"), List.of(recap, "", t("finale.menu.change"))));
        KLMenu.open(player, Txt.of(t("finale.menu.title")), 3, container, "jury-vote", (p, slot) -> {
            String target = bySlot.get(slot);
            if (target != null) {
                openConfirm(p, target);
            }
        });
    }

    private static void openConfirm(ServerPlayer player, String target) {
        String name = d().finalists.get(target);
        if (name == null) {
            return;
        }
        SimpleContainer container = new SimpleContainer(27);
        container.setItem(11, MenuItems.named(Items.LIME_CONCRETE, t("finale.menu.confirm"), List.of(t("finale.menu.confirm.lore", name))));
        container.setItem(13, MenuItems.head(name, "&f&l" + name, List.of(t("finale.menu.target"))));
        container.setItem(15, MenuItems.named(Items.RED_CONCRETE, t("finale.menu.cancel"), List.of(t("finale.menu.cancel.lore"))));
        KLMenu.open(player, Txt.of(t("finale.menu.confirm.title")), 3, container, "jury-confirm", (p, slot) -> {
            if (slot == 11) {
                String err = castVote(p, UUID.fromString(target));
                if (err != null) {
                    p.sendSystemMessage(Txt.of("&c" + err));
                }
                p.closeContainer();
            } else if (slot == 15) {
                openVoteMenu(p);
            }
        });
    }

    /** Vote secret d'un juré (modifiable tant que le vote est ouvert). Retourne un message d'erreur ou null. */
    private static String castVote(ServerPlayer player, UUID finalist) {
        if (stage() != Stage.VOTING) {
            return t("finale.vote.closed");
        }
        if (!d().jury.containsKey(player.getUUID().toString())) {
            return t("finale.vote.notjuror");
        }
        if (!d().finalists.containsKey(finalist.toString())) {
            return t("finale.vote.invalid");
        }
        d().votes.put(player.getUUID().toString(), finalist.toString());
        SeasonManager.save();
        player.sendSystemMessage(Txt.of(t("finale.vote.recorded", d().finalists.get(finalist.toString()))));
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR) {
                    p.sendSystemMessage(Txt.of(t("finale.vote.staff", d().votes.size(), d().jury.size())));
                }
            }
        }
        return null;
    }

    /** /ccis finale vote : (re)ouvre le menu de vote d'un juré. */
    private static int voteMenu(CommandContext<CommandSourceStack> ctx) {
        if (stage() != Stage.VOTING) {
            return fail(ctx, t("finale.vote.closed"));
        }
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (!d().jury.containsKey(p.getUUID().toString())) {
                return fail(ctx, t("finale.vote.notjuror"));
            }
            openVoteMenu(p);
            return 1;
        } catch (Exception e) {
            return fail(ctx, t("finale.vote.notjuror"));
        }
    }

    /** /ccis finale vote <joueur> : vote direct (utilise aussi par le menu). */
    private static int vote(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return fail(ctx, t("finale.vote.notjuror"));
        }
        UUID finalist = findUuid(d().finalists, StringArgumentType.getString(ctx, "player"));
        if (finalist == null) {
            return fail(ctx, t("finale.vote.invalid"));
        }
        String err = castVote(player, finalist);
        return err == null ? 1 : fail(ctx, err);
    }

    // ------------------------------------------------------------ depouillement

    private static final Map<String, Integer> revealTally = new HashMap<>();
    private static List<String> revealQueue = null;
    private static int revealIndex = 0;
    private static int revealWait = 0;

    private static int reveal(CommandContext<CommandSourceStack> ctx) {
        if (wrongStage(ctx, Stage.VOTING)) {
            return 0;
        }
        if (d().votes.isEmpty()) {
            return fail(ctx, t("finale.reveal.fail"));
        }
        revealQueue = new ArrayList<>(d().votes.values());
        Collections.shuffle(revealQueue);   // anonyme : l'ordre n'indique pas qui a vote
        revealIndex = 0;
        revealWait = 0;
        revealTally.clear();
        for (String id : d().finalists.keySet()) {
            revealTally.put(id, 0);
        }
        setStage(Stage.REVEAL);
        sendTally(ctx.getSource().getServer(), true);
        return ok(ctx, t("finale.reveal.start", revealQueue.size()));
    }

    /** Appele chaque seconde : revele un vote toutes les 5 secondes, puis annonce le vainqueur. */
    public static void tick(MinecraftServer server) {
        if (server == null || stage() != Stage.REVEAL) {
            return;
        }
        if (revealQueue == null) {   // redemarrage du serveur en plein depouillement : on conclut directement
            revealQueue = new ArrayList<>(d().votes.values());
            revealIndex = revealQueue.size();
        }
        if (revealIndex < revealQueue.size()) {
            if (revealWait-- > 0) {
                return;
            }
            revealWait = 4;
            String name = d().finalists.getOrDefault(revealQueue.get(revealIndex), "?");
            revealIndex++;
            revealTally.merge(revealQueue.get(revealIndex - 1), 1, Integer::sum);
            for (ServerPlayer p : audience(server)) {
                GameMessages.showTitle(p, Txt.of(t("finale.reveal.vote", revealIndex, revealQueue.size())), Txt.of("&f" + name), 10, 70, 20);
                GameMessages.playSound(p, "minecraft:block.note_block.bell", 1.0f);
                p.sendSystemMessage(Txt.of(t("finale.reveal.chat", revealIndex, revealQueue.size(), name)));
            }
            sendTally(server, true);
            return;
        }
        if (revealWait-- > 0) {
            return;
        }
        conclude(server);
    }

    /** Jury, finalistes, organisateurs et spectateurs assistent au depouillement (comme au conseil). */
    private static List<ServerPlayer> audience(MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String id = p.getUUID().toString();
            if (d().jury.containsKey(id) || d().finalists.containsKey(id)
                    || KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR || RoleManager.effective(p) == KLRole.SPECTATEUR) {
                list.add(p);
            }
        }
        return list;
    }

    /** Decompte en direct des votes (meme HUD que le conseil). active = false : le masque. */
    private static void sendTally(MinecraftServer server, boolean active) {
        if (!active) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ConseilNetwork.toPlayer(p, new ConseilNetwork.TallyPacket(false, new ArrayList<>(), new ArrayList<>()));
            }
            return;
        }
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Map.Entry<String, String> e : d().finalists.entrySet()) {
            names.add(e.getValue());
            counts.add(revealTally.getOrDefault(e.getKey(), 0));
        }
        for (ServerPlayer p : audience(server)) {
            ConseilNetwork.toPlayer(p, new ConseilNetwork.TallyPacket(true, names, counts));
        }
    }

    private static void conclude(MinecraftServer server) {
        sendTally(server, false);
        Map<String, Integer> tally = new HashMap<>();
        for (String finalist : d().votes.values()) {
            tally.merge(finalist, 1, Integer::sum);
        }
        String best = null;
        int bestCount = -1;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : tally.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
                tie = false;
            } else if (e.getValue() == bestCount) {
                tie = true;
            }
        }
        revealQueue = null;
        if (best == null || tie) {
            setStage(Stage.VOTING);
            d().votes = new java.util.LinkedHashMap<>();
            SeasonManager.save();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR) {
                    p.sendSystemMessage(Txt.of(t("finale.reveal.tie", bestCount)));
                }
            }
            return;
        }
        String winnerName = d().finalists.getOrDefault(best, "?");
        SeasonHistory.log("finale", winnerName + " remporte l'aventure (" + bestCount + " votes sur " + d().votes.size() + ").");
        setStage(Stage.DONE);
        title(server, t("finale.win.title", winnerName), t("finale.win.sub"), "minecraft:ui.toast.challenge_complete");
        broadcast(server, Txt.of(t("finale.win.chat", winnerName, bestCount, d().votes.size())));
        SeasonManager.end(server);
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        d().finalists = new java.util.LinkedHashMap<>();
        d().votes = new java.util.LinkedHashMap<>();
        d().potWinner = "";
        revealQueue = null;
        setStage(Stage.NONE);
        if (ctx.getSource().getServer() != null) {
            sendTally(ctx.getSource().getServer(), false);
        }
        if (SeasonManager.phase() == SeasonManager.Phase.FINALE) {
            SeasonManager.setPhase(SeasonManager.Phase.POST_MERGE);
        }
        return ok(ctx, t("finale.reset"));
    }

    /** TEST SOLO (admin) : faux jurés et faux finalistes, tu votes toi-meme via le menu. */
    private static int simulate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer me;
        try {
            me = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return fail(ctx, t("finale.vote.notjuror"));
        }
        SeasonManager.Data data = d();
        data.jury = new java.util.LinkedHashMap<>();
        data.finalists = new java.util.LinkedHashMap<>();
        data.votes = new java.util.LinkedHashMap<>();
        String[] fakeFinal = {"Alex", "Camille"};
        String[] fakeJury = {"Sam", "Lea", "Noe", "Maya"};
        List<String> finalIds = new ArrayList<>();
        for (String n : fakeFinal) {
            String id = UUID.nameUUIDFromBytes(("sim-" + n).getBytes()).toString();
            data.finalists.put(id, n);
            finalIds.add(id);
        }
        java.util.Random rnd = new java.util.Random();
        for (String n : fakeJury) {
            String id = UUID.nameUUIDFromBytes(("sim-" + n).getBytes()).toString();
            data.jury.put(id, n);
            data.votes.put(id, finalIds.get(rnd.nextInt(2)));   // vote deja depose
        }
        data.jury.put(me.getUUID().toString(), me.getGameProfile().getName());
        data.potWinner = "";
        SeasonManager.setPhase(SeasonManager.Phase.FINALE);
        setStage(Stage.VOTING);
        me.sendSystemMessage(Txt.of(t("finale.sim.started")));
        me.sendSystemMessage(Txt.of(t("finale.vote.prompt")));
        openVoteMenu(me);
        return 1;
    }

    /** Fin du test : efface jury, finalistes et votes, remet la saison en phase apres-fusion. */
    private static int simulateStop(CommandContext<CommandSourceStack> ctx) {
        d().jury = new java.util.LinkedHashMap<>();
        reset(ctx);
        SeasonManager.setPhase(SeasonManager.Phase.POST_MERGE);
        return 1;
    }

    /** Jury actuel (uuid -> pseudo), copie. */
    public static Map<String, String> juryMap() {
        return new java.util.LinkedHashMap<>(d().jury);
    }

    public static List<String> finalistNames() {
        return new ArrayList<>(d().finalists.values());
    }

    public static int voteCount() {
        return d().votes.size();
    }

    /** SIMULATION : les jures qui n'ont pas encore vote (faux jures) votent au hasard. Retourne le nombre de votes ajoutes. */
    public static int fillMissingVotes() {
        if (stage() != Stage.VOTING || d().finalists.size() != 2) {
            return 0;
        }
        List<String> ids = new ArrayList<>(d().finalists.keySet());
        java.util.Random rnd = new java.util.Random();
        int added = 0;
        for (String juror : d().jury.keySet()) {
            if (!d().votes.containsKey(juror)) {
                d().votes.put(juror, ids.get(rnd.nextInt(2)));
                added++;
            }
        }
        SeasonManager.save();
        return added;
    }

    /** Etat de la finale pour la fenetre d'administration. */
    public static com.google.gson.JsonObject snapshot() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("stage", t("finale.stage." + stage().name().toLowerCase()));
        o.addProperty("jury", d().jury.size());
        o.addProperty("votes", d().votes.size());
        com.google.gson.JsonArray fin = new com.google.gson.JsonArray();
        for (String n : d().finalists.values()) {
            fin.add(n);
        }
        o.add("finalists", fin);
        return o;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(Txt.of(t("finale.status", t("finale.stage." + stage().name().toLowerCase()),
                d().jury.size(), d().finalists.isEmpty() ? "-" : String.join(" / ", d().finalists.values()))));
        return 1;
    }

    // ------------------------------------------------------------ commandes

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestAlive(
            CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(RoleManager.participants().values(), b);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("finale")
                .executes(FinaleManager::status)
                .then(Commands.literal("status").executes(FinaleManager::status))
                .then(Commands.literal("start")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(FinaleManager::start))
                .then(Commands.literal("winner")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(FinaleManager::suggestAlive)
                                .executes(FinaleManager::winner)))
                .then(Commands.literal("pick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(FinaleManager::suggestAlive)
                                .executes(FinaleManager::pick)))
                .then(Commands.literal("vote")
                        .then(Commands.literal("start")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                                .executes(FinaleManager::voteStart))
                        .executes(FinaleManager::voteMenu)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(FinaleManager::vote)))
                .then(Commands.literal("reveal")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(FinaleManager::reveal))
                .then(Commands.literal("jury")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(ctx -> {
                            showJury(ctx.getSource());
                            return 1;
                        }))
                .then(Commands.literal("simulate")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(FinaleManager::simulate)
                        .then(Commands.literal("stop").executes(FinaleManager::simulateStop)))
                .then(Commands.literal("reset")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(FinaleManager::reset));
    }
}
