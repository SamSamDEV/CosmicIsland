package fr.cosmicisland;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import fr.cosmicisland.camp.CampfireManager;
import fr.cosmicisland.conseil.ConseilAdminMenu;
import fr.cosmicisland.conseil.ConseilManager;
import fr.cosmicisland.conseil.ConseilMenus;
import fr.cosmicisland.game.GameConfig;
import fr.cosmicisland.game.GameManager;
import fr.cosmicisland.game.GameRegistry;
import fr.cosmicisland.game.KLGame;
import fr.cosmicisland.game.Placeholders;
import fr.cosmicisland.game.RankingManager;
import fr.cosmicisland.game.SpawnManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class KLCommand {
    private static final SuggestionProvider<CommandSourceStack> TEAM_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(TeamManager.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> ROLES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(KLRole.values()).map(KLRole::id), builder);

    private KLCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ccis")
                .then(Commands.literal("info")
                        .executes(KLCommand::info))
                .then(Commands.literal("help")
                        .executes(KLCommand::help))
                .then(Commands.literal("base")
                        .executes(KLCommand::goBase))
                .then(Commands.literal("campfire")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.literal("status")
                                .executes(KLCommand::campfireStatus))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::campfireTp)))
                        .then(Commands.literal("set")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::campfireSet)))
                        .then(Commands.literal("remove")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::campfireRemove)))
                        .then(Commands.literal("fuel")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("valeur", DoubleArgumentType.doubleArg(0.0, 100000.0))
                                                .executes(KLCommand::campfireFuel)))))
                .then(Commands.literal("say")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.argument("texte", StringArgumentType.greedyString())
                                .executes(KLCommand::say)))
                .then(Commands.literal("admin")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(KLCommand::admin))
                .then(Commands.literal("reload")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(KLCommand::reload))
                .then(Commands.literal("games")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(KLCommand::gameList))
                .then(Commands.literal("enable")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.argument("jeu", StringArgumentType.word())
                                .suggests(GAME_IDS)
                                .executes(c -> setEnabled(c, true))))
                .then(Commands.literal("disable")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.argument("jeu", StringArgumentType.word())
                                .suggests(GAME_IDS)
                                .executes(c -> setEnabled(c, false))))
                .then(Commands.literal("start")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.argument("jeu", StringArgumentType.word())
                                .suggests(GAME_IDS)
                                .executes(KLCommand::gameStart)))
                .then(Commands.literal("go")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(KLCommand::gameGo))
                .then(Commands.literal("freeze")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(KLCommand::gameFreeze))
                .then(Commands.literal("unfreeze")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(KLCommand::gameUnfreeze))
                .then(Commands.literal("stop")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(c -> gameStop(c, null))
                        .then(Commands.argument("jeu", StringArgumentType.word())
                                .suggests(GAME_IDS)
                                .executes(c -> gameStop(c, StringArgumentType.getString(c, "jeu")))))
                .then(Commands.literal("join")
                        .executes(KLCommand::gameJoin))
                .then(Commands.literal("decline")
                        .executes(KLCommand::gameDecline))
                .then(Commands.literal("spawn")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.literal("set")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(SPAWN_IDS)
                                        .executes(KLCommand::spawnSet)))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(SPAWN_IDS)
                                        .executes(KLCommand::spawnTp)))
                        .then(Commands.literal("base")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("equipe", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(c -> teamSpawnSet(c, "base:"))))
                                .then(Commands.literal("tp")
                                        .then(Commands.argument("equipe", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(c -> teamSpawnTp(c, "base:")))))
                        .then(Commands.literal("respawn")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("equipe", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(c -> teamSpawnSet(c, "respawn:"))))
                                .then(Commands.literal("tp")
                                        .then(Commands.argument("equipe", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(c -> teamSpawnTp(c, "respawn:")))))
                        .then(Commands.literal("jeu")
                                .then(Commands.argument("jeu", StringArgumentType.word())
                                        .suggests(GAME_IDS)
                                        .then(Commands.argument("equipe", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(KLCommand::gameZoneSet))))
                        .then(Commands.literal("list")
                                .executes(KLCommand::spawnList)))
                .then(Commands.literal("ranking")
                        .executes(c -> rankingTop(c, null))
                        .then(Commands.literal("jeu")
                                .then(Commands.argument("jeu", StringArgumentType.word())
                                        .suggests(GAME_IDS)
                                        .executes(c -> rankingTop(c, StringArgumentType.getString(c, "jeu")))))
                        .then(Commands.literal("joueur")
                                .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                                        .executes(KLCommand::rankingPlayer)))
                        .then(Commands.literal("reset")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.literal("confirm")
                                        .executes(KLCommand::rankingResetConfirm))
                                .then(Commands.argument("cible", StringArgumentType.word())
                                        .suggests(RANK_TARGETS)
                                        .executes(KLCommand::rankingResetAsk)))
                        .then(Commands.literal("add")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("jeu", StringArgumentType.word())
                                                .suggests(GAME_IDS)
                                                .then(Commands.argument("nombre", IntegerArgumentType.integer(-100, 100))
                                                        .executes(KLCommand::rankingAdd))))))
                .then(Commands.literal("placeholder")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.argument("texte", StringArgumentType.greedyString())
                                .executes(KLCommand::placeholderTest)))
                .then(Commands.literal("vote")
                        .executes(KLCommand::conseilVote))
                .then(Commands.literal("conseil")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.literal("start")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::conseilStart)))
                        .then(Commands.literal("test")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(c -> conseilTest(c, 3))
                                        .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 20))
                                                .executes(c -> conseilTest(c, IntegerArgumentType.getInteger(c, "nombre"))))))
                        .then(Commands.literal("menu")
                                .executes(KLCommand::conseilMenu))
                        .then(Commands.literal("status")
                                .executes(KLCommand::conseilStatus))
                        .then(Commands.literal("vote")
                                .then(Commands.literal("close")
                                        .executes(c -> conseilAction(c, "voteclose"))))
                        .then(Commands.literal("reveal")
                                .executes(c -> conseilAction(c, "reveal")))
                        .then(Commands.literal("revote")
                                .executes(c -> conseilAction(c, "revote")))
                        .then(Commands.literal("close")
                                .executes(c -> conseilAction(c, "close")))
                        .then(Commands.literal("cancel")
                                .executes(c -> conseilAction(c, "cancel")))
                        .then(Commands.literal("immunite")
                                .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                                        .executes(KLCommand::conseilImmunity))))
                .then(Commands.literal("role")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.literal("set")
                                .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .suggests(ROLES)
                                                .executes(KLCommand::roleSet))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                                        .executes(KLCommand::roleGet))))
                .then(Commands.literal("team")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.literal("list")
                                .executes(KLCommand::teamList))
                        .then(Commands.literal("add")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                                                .executes(KLCommand::teamAdd))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                                        .executes(KLCommand::teamRemove)))
                        .then(Commands.literal("name")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("texte", StringArgumentType.greedyString())
                                                .executes(c -> teamEdit(c, true)))))
                        .then(Commands.literal("tag")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("texte", StringArgumentType.greedyString())
                                                .executes(c -> teamEdit(c, false)))))
                        .then(Commands.literal("color")
                                .then(Commands.argument("equipe", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("couleur", ColorArgument.color())
                                                .executes(KLCommand::teamColor))))
                        .then(Commands.literal("merge")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .executes(KLCommand::teamMerge)));

        // Commandes propres a chaque jeu (ex. /ccis poteaux ...)
        for (String id : GameRegistry.ids()) {
            KLGame game = GameRegistry.get(id);
            LiteralArgumentBuilder<CommandSourceStack> node = game == null ? null : game.commands();
            if (node != null) {
                root.then(node);
            }
        }
        dispatcher.register(root);
        // Alias court : /base = /ccis base
        dispatcher.register(Commands.literal("base").executes(KLCommand::goBase));
    }

    // ---------------------------------------------------------------- general

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();

        // Staff : admin = rouge, organisateur = or, non = gris
        int level = KLPermissions.levelOf(player);
        MutableComponent staff;
        if (level == KLPermissions.ADMIN) {
            staff = Component.literal("Admin").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        } else if (level == KLPermissions.ORGANISATEUR) {
            staff = Component.literal("Organisateur").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else {
            staff = Component.literal("Non").withStyle(ChatFormatting.GRAY);
        }

        // Role : participant = vert, spectateur = cyan, organisateur = or
        KLRole role = RoleManager.effective(player);
        ChatFormatting roleColor = role == KLRole.PARTICIPANT ? ChatFormatting.GREEN
                : role == KLRole.SPECTATEUR ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        MutableComponent roleValue = Component.literal(capitalize(role.id())).withStyle(roleColor);

        // Equipe : tag en gras + nom, dans la couleur de l'equipe
        String teamId = TeamManager.teamIdOf(player.getUUID());
        MutableComponent team;
        if (teamId == null) {
            team = Component.literal("Aucune").withStyle(ChatFormatting.GRAY);
        } else {
            TeamManager.KLTeam t = TeamManager.get(teamId);
            team = Component.empty()
                    .append(boldTag(t))
                    .append(Component.literal(" " + t.name).withStyle(t.format()));
        }

        Component header = Component.literal("=== Cosmic Island ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        src.sendSuccess(() -> header, false);
        src.sendSuccess(() -> line("Staff", staff), false);
        src.sendSuccess(() -> line("Rôle", roleValue), false);
        src.sendSuccess(() -> line("Équipe", team), false);
        return 1;
    }

    /** Une ligne "Libelle : valeur" avec le libelle en gras gris. */
    private static Component line(String label, Component value) {
        return Component.empty()
                .append(Component.literal(label + " : ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
                .append(value);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static int admin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        fr.cosmicisland.gui.AdminMenus.openMain(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        KLFiles.loadAll();
        TeamManager.sync(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Configs Cosmic Island rechargées."), true);
        return 1;
    }

    // ---------------------------------------------------------------- roles

    private static int roleSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        KLRole role = KLRole.parse(StringArgumentType.getString(ctx, "role"));
        if (role == null) {
            ctx.getSource().sendFailure(Component.literal("Rôle inconnu. Choix : participant, spectateur, organisateur."));
            return 0;
        }
        for (GameProfile profile : profiles) {
            RoleManager.set(profile, role);
            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(profile.getId());
            if (online != null) {
                RoleManager.apply(online, role);
            }
        }
        String msg = profiles.size() + " joueur(s) -> " + role.id();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return profiles.size();
    }

    private static int roleGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        for (GameProfile profile : profiles) {
            KLRole role = RoleManager.get(profile.getId());
            String msg = profile.getName() + " : "
                    + (role == null ? "non enregistré (spectateur par défaut)" : role.id());
            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        }
        return profiles.size();
    }

    // ---------------------------------------------------------------- equipes

    /** Retourne l'equipe de l'argument "equipe", ou null (message d'erreur deja envoye). */
    private static TeamManager.KLTeam findTeam(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "equipe");
        TeamManager.KLTeam team = TeamManager.get(id);
        if (team == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Équipe inconnue : " + id + " (équipes : " + String.join(", ", TeamManager.ids()) + ")"));
        }
        return team;
    }

    private static int teamList(CommandContext<CommandSourceStack> ctx) {
        String state = TeamManager.isMerged() ? "fusionnées (tribu unique)" : "séparées";
        ctx.getSource().sendSuccess(() -> Component.literal("Équipes " + state + " :"), false);
        for (Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            TeamManager.KLTeam t = e.getValue();
            Component line = Component.empty()
                    .append(boldTag(t))
                    .append(Component.literal(" " + t.name + " (" + e.getKey() + ") - "
                            + t.members.size() + " joueur(s) : " + String.join(", ", t.members.values())));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return TeamManager.all().size();
    }

    private static Component boldTag(TeamManager.KLTeam t) {
        var tag = Txt.of(t.tag).withStyle(t.format());
        if (t.bold) {
            tag.withStyle(ChatFormatting.BOLD);
        }
        return tag;
    }

    private static int teamAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "equipe").toLowerCase();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        for (GameProfile profile : profiles) {
            TeamManager.assign(id, profile);
        }
        String msg = profiles.size() + " joueur(s) ajouté(s) à l'équipe " + team.name;
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return profiles.size();
    }

    private static int teamRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        for (GameProfile profile : profiles) {
            TeamManager.remove(profile);
        }
        String msg = profiles.size() + " joueur(s) retiré(s) de leur équipe";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return profiles.size();
    }

    private static int teamEdit(CommandContext<CommandSourceStack> ctx, boolean isName) {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "texte");
        if (isName) {
            team.name = text;
        } else {
            team.tag = text;
        }
        TeamManager.changed();
        ctx.getSource().sendSuccess(() -> Component.literal("Équipe mise à jour."), true);
        return 1;
    }

    private static int teamColor(CommandContext<CommandSourceStack> ctx) {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        team.color = ColorArgument.getColor(ctx, "couleur").getName();
        TeamManager.changed();
        ctx.getSource().sendSuccess(() -> Component.literal("Couleur de l'équipe mise à jour."), true);
        return 1;
    }

    private static int teamMerge(CommandContext<CommandSourceStack> ctx) {
        if (!TeamManager.merge()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Fusion impossible : l'équipe '" + TeamManager.MERGED_ID + "' n'existe pas dans teams.json."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Fusion effectuée : tous les joueurs sont dans l'équipe " + TeamManager.MERGED_ID + "."), true);
        return 1;
    }

    // ---------------------------------------------------------------- epreuves

    private static final SuggestionProvider<CommandSourceStack> GAME_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(GameRegistry.ids(), builder);

    private static int gameList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("=== Épreuves ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (String id : GameRegistry.ids()) {
            GameConfig c = GameRegistry.config(id);
            if (c == null) {
                continue;
            }
            Component status = c.enabled
                    ? Component.literal("ACTIVÉ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.literal("DÉSACTIVÉ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            Component row = Component.empty()
                    .append(Component.literal(c.displayName + " (" + id + ") : ").withStyle(ChatFormatting.WHITE))
                    .append(status);
            src.sendSuccess(() -> row, false);
        }
        String active = GameManager.describe();
        src.sendSuccess(() -> line("Épreuve en cours", Component.literal(active).withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String id = StringArgumentType.getString(ctx, "jeu");
        GameConfig c = GameRegistry.config(id);
        if (c == null) {
            ctx.getSource().sendFailure(Component.literal("Jeu inconnu : " + id));
            return 0;
        }
        c.enabled = enabled;
        GameRegistry.saveConfig(id);
        String msg = "Jeu " + c.displayName + (enabled ? " activé." : " désactivé.");
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int gameStart(CommandContext<CommandSourceStack> ctx) {
        String err = GameManager.start(StringArgumentType.getString(ctx, "jeu"));
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int gameGo(CommandContext<CommandSourceStack> ctx) {
        String err = GameManager.go();
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Décompte lancé."), true);
        return 1;
    }

    private static int gameStop(CommandContext<CommandSourceStack> ctx, String id) {
        if (GameManager.state() == GameManager.State.IDLE) {
            ctx.getSource().sendFailure(Component.literal("Aucune épreuve en cours."));
            return 0;
        }
        if (id != null && !GameManager.isActive(id)) {
            ctx.getSource().sendFailure(Component.literal("L'épreuve en cours n'est pas : " + id));
            return 0;
        }
        GameManager.finish(true);
        return 1;
    }

    private static int gameJoin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String err = GameManager.join(ctx.getSource().getPlayerOrException());
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int gameDecline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String err = GameManager.decline(ctx.getSource().getPlayerOrException());
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    // ---------------------------------------------------------------- spawns

    private static int spawnSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "equipe").toLowerCase();
        String label;
        if (SpawnManager.isSpecial(id)) {
            label = id;
        } else {
            TeamManager.KLTeam team = findTeam(ctx);
            if (team == null) {
                return 0;
            }
            label = "de l'équipe " + team.name;
        }
        SpawnManager.set(id, player);
        String msg = "Spawn " + label + " défini à ta position.";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int spawnTp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "equipe").toLowerCase();
        if (!SpawnManager.isSpecial(id) && findTeam(ctx) == null) {
            return 0;
        }
        SpawnManager.Spawn spawn = SpawnManager.get(id);
        if (spawn == null) {
            ctx.getSource().sendFailure(Component.literal("Spawn non défini : " + id));
            return 0;
        }
        if (!SpawnManager.teleport(ctx.getSource().getServer(), player, spawn)) {
            ctx.getSource().sendFailure(Component.literal("Dimension du spawn introuvable : " + spawn.dimension));
            return 0;
        }
        return 1;
    }

    private static int spawnList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("=== Spawns ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            String id = e.getKey();
            String name = e.getValue().name;
            spawnRow(src, name + " (zone de jeu)", id);
            spawnRow(src, name + " (base)", "base:" + id);
            spawnRow(src, name + " (respawn mort)", "respawn:" + id);
        }
        spawnRow(src, "Conseil", "conseil");
        spawnRow(src, "Élimination", "elimination");
        return 1;
    }

    private static void spawnRow(CommandSourceStack src, String label, String key) {
        SpawnManager.Spawn s = SpawnManager.get(key);
        Component value = s == null
                ? Component.literal("non défini").withStyle(ChatFormatting.RED)
                : Component.literal(String.format("%s  %.1f %.1f %.1f", s.dimension, s.x, s.y, s.z))
                        .withStyle(ChatFormatting.GREEN);
        Component row = line(label, value);
        src.sendSuccess(() -> row, false);
    }

    private static int gameFreeze(CommandContext<CommandSourceStack> ctx) {
        String err = GameManager.freeze();
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int gameUnfreeze(CommandContext<CommandSourceStack> ctx) {
        GameManager.unfreeze();
        ctx.getSource().sendSuccess(() -> Component.literal("Participants libérés."), true);
        return 1;
    }

    // ---------------------------------------------------------------- classement

    private static final SuggestionProvider<CommandSourceStack> RANK_TARGETS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Stream.concat(Stream.of("all"), GameRegistry.ids().stream()), builder);

    private static String sourceKey(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID().toString() : "console";
    }

    private static int rankingTop(CommandContext<CommandSourceStack> ctx, String gameId) {
        CommandSourceStack src = ctx.getSource();
        String title = "Classement général";
        if (gameId != null) {
            GameConfig c = GameRegistry.config(gameId);
            if (c == null) {
                src.sendFailure(Component.literal("Jeu inconnu : " + gameId));
                return 0;
            }
            title = "Classement - " + c.displayName;
        }
        String header = "=== " + title + " ===";
        src.sendSuccess(() -> Component.literal(header).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        List<RankingManager.Row> rows = RankingManager.top(gameId, 10);
        if (rows.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Aucun joueur enregistré.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        int rank = 1;
        for (RankingManager.Row row : rows) {
            ChatFormatting color = rank == 1 ? ChatFormatting.GOLD
                    : rank == 2 ? ChatFormatting.GRAY
                    : rank == 3 ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY;
            String wins = row.wins() + (row.wins() > 1 ? " victoires" : " victoire");
            Component line = Component.empty()
                    .append(Component.literal("#" + rank + " ").withStyle(color, ChatFormatting.BOLD))
                    .append(Component.literal(row.name() == null ? "?" : row.name()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + wins).withStyle(ChatFormatting.GREEN));
            src.sendSuccess(() -> line, false);
            rank++;
        }
        return rows.size();
    }

    private static int rankingPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        for (GameProfile profile : profiles) {
            RankingManager.PlayerStats s = RankingManager.statsOf(profile.getId());
            int total = s == null ? 0 : s.wins;
            int played = s == null ? 0 : s.played;
            Component summary = line(profile.getName(), Component.literal(
                    total + " victoire(s) sur " + played + " épreuve(s)").withStyle(ChatFormatting.GREEN));
            ctx.getSource().sendSuccess(() -> summary, false);
            if (s != null) {
                for (Map.Entry<String, Integer> e : s.perGame.entrySet()) {
                    GameConfig c = GameRegistry.config(e.getKey());
                    String name = c == null ? e.getKey() : c.displayName;
                    Component detail = Component.literal("  - " + name + " : " + e.getValue())
                            .withStyle(ChatFormatting.GRAY);
                    ctx.getSource().sendSuccess(() -> detail, false);
                }
            }
        }
        return profiles.size();
    }

    private static int rankingAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        String game = StringArgumentType.getString(ctx, "jeu").toLowerCase();
        int n = IntegerArgumentType.getInteger(ctx, "nombre");
        if (GameRegistry.get(game) == null) {
            ctx.getSource().sendFailure(Component.literal("Jeu inconnu : " + game));
            return 0;
        }
        for (GameProfile profile : profiles) {
            RankingManager.add(profile, game, n);
        }
        String msg = profiles.size() + " joueur(s) : " + (n >= 0 ? "+" : "") + n + " victoire(s) pour " + game;
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return profiles.size();
    }

    private static int rankingResetAsk(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "cible").toLowerCase();
        if (!target.equals("all") && GameRegistry.get(target) == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Cible inconnue : " + target + " (utilise all ou l'identifiant d'un jeu)."));
            return 0;
        }
        RankingManager.requestReset(sourceKey(ctx), target);
        Component msg = Component.empty()
                .append(Component.literal("ATTENTION ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("Remise à zéro de '" + target + "'. Tape ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/ccis ranking reset confirm").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" dans les 30 secondes pour confirmer (copie de secours automatique).")
                        .withStyle(ChatFormatting.GRAY));
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int rankingResetConfirm(CommandContext<CommandSourceStack> ctx) {
        RankingManager.ResetResult r = RankingManager.confirmReset(sourceKey(ctx));
        if (r == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Aucune remise à zéro en attente (elle expire au bout de 30 secondes)."));
            return 0;
        }
        String msg = "Classement remis à zéro (" + r.target() + "). Copie de secours : "
                + (r.backup() == null ? "aucune" : r.backup());
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int placeholderTest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String result = Placeholders.apply(StringArgumentType.getString(ctx, "texte"), player);
        ctx.getSource().sendSuccess(() -> fr.cosmicisland.Txt.of(result), false);
        return 1;
    }

    // ---------------------------------------------------------------- conseil

    private static final SuggestionProvider<CommandSourceStack> SPAWN_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Stream.concat(TeamManager.ids().stream(), Stream.of("conseil", "elimination")), builder);

    private static int conseilStart(CommandContext<CommandSourceStack> ctx) {
        String err = ConseilManager.start(StringArgumentType.getString(ctx, "equipe"));
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int conseilTest(CommandContext<CommandSourceStack> ctx, int fakes) {
        String err = ConseilManager.start(StringArgumentType.getString(ctx, "equipe"), fakes);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int conseilMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String err = ConseilAdminMenu.open(ctx.getSource().getPlayerOrException());
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int conseilStatus(CommandContext<CommandSourceStack> ctx) {
        for (Component c : ConseilManager.statusLines()) {
            ctx.getSource().sendSuccess(() -> c, false);
        }
        return 1;
    }

    private static int conseilAction(CommandContext<CommandSourceStack> ctx, String action) {
        String err = switch (action) {
            case "voteclose" -> ConseilManager.closeVote();
            case "reveal" -> ConseilManager.reveal();
            case "revote" -> ConseilManager.revote();
            case "close" -> ConseilManager.close();
            case "cancel" -> ConseilManager.cancel();
            default -> "Action inconnue.";
        };
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int conseilImmunity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "joueur");
        int done = 0;
        for (GameProfile profile : profiles) {
            String err = ConseilManager.toggleImmune(profile.getId());
            if (err != null) {
                ctx.getSource().sendFailure(Component.literal(profile.getName() + " : " + err));
            } else {
                done++;
            }
        }
        return done;
    }

    private static int conseilVote(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String err = ConseilMenus.openVote(ctx.getSource().getPlayerOrException());
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    // ---------------------------------------------------------------- aide et annonces

    private record HelpEntry(String command, String description, int level) {}

    private static final List<HelpEntry> HELP = List.of(
            // Tous les joueurs
            new HelpEntry("/ccis help", "Affiche cette aide (selon ton grade)", KLPermissions.NONE),
            new HelpEntry("/ccis info", "Ton rôle, ton grade staff et ton équipe", KLPermissions.NONE),
            new HelpEntry("/ccis join", "Rejoindre une épreuve (boutons du chat)", KLPermissions.NONE),
            new HelpEntry("/ccis decline", "Refuser une épreuve (boutons du chat)", KLPermissions.NONE),
            new HelpEntry("/ccis vote", "Rouvrir le menu de vote du conseil", KLPermissions.NONE),
            new HelpEntry("/ccis base  (ou /base)", "Retourner à la base de ton équipe (impossible pendant une épreuve)", KLPermissions.NONE),
            new HelpEntry("/ccis ranking", "Classement général des victoires", KLPermissions.NONE),
            new HelpEntry("/ccis ranking jeu <jeu>", "Classement d'un jeu", KLPermissions.NONE),
            new HelpEntry("/ccis ranking joueur <pseudo>", "Victoires d'un joueur", KLPermissions.NONE),
            // Organisateurs
            new HelpEntry("/ccis games", "Liste des épreuves et leur état", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis start <jeu>", "Envoie les invitations d'une épreuve", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis freeze | unfreeze", "Immobilise ou libère les participants", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis go", "Lance le décompte et le départ", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis stop [jeu]", "Arrête l'épreuve en cours", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis team list|add|remove|name|tag|color", "Gérer les équipes", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis poteaux zone pos1|pos2|info", "Zone d'eau des poteaux", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis poteaux check", "Qui est sur un poteau ?", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis conseil start|test <équipe>", "Lancer un conseil (ou un test)", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis conseil menu|status", "Menu et état du conseil", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis conseil vote close|reveal|revote|close|cancel", "Piloter le conseil", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis conseil immunite <joueur>", "Activer ou retirer l'immunité", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis campfire status|tp <équipe>", "État des feux de camp, se téléporter à un feu", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis say <message>", "Annonce visible par tout le monde", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis placeholder <texte>", "Tester les placeholders", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis admin", "Menu admin : épreuves, équipes, joueurs, conseil, classement, spawns", KLPermissions.ORGANISATEUR),
            // Admins
            new HelpEntry("/ccis reload", "Recharge les fichiers de config", KLPermissions.ADMIN),
            new HelpEntry("/ccis role set|get <joueur> [rôle]", "Gérer les rôles (participant, spectateur, organisateur)", KLPermissions.ADMIN),
            new HelpEntry("/ccis enable|disable <jeu>", "Activer ou désactiver un jeu", KLPermissions.ADMIN),
            new HelpEntry("/ccis campfire set|remove|fuel <équipe>", "Définir (en regardant le feu), retirer, régler la jauge", KLPermissions.ADMIN),
            new HelpEntry("/ccis team merge", "Fusion des équipes en tribu unique", KLPermissions.ADMIN),
            new HelpEntry("/ccis spawn set|tp|list", "Zone de jeu des équipes, conseil, élimination", KLPermissions.ADMIN),
            new HelpEntry("/ccis spawn base|respawn set|tp <équipe>", "Base et respawn (mort) des équipes", KLPermissions.ADMIN),
            new HelpEntry("/ccis spawn jeu <jeu> <équipe>", "Point d'équipe propre à un jeu", KLPermissions.ADMIN),
            new HelpEntry("/ccis ranking reset|add", "Remise à zéro et corrections du classement", KLPermissions.ADMIN));

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean player = src.getEntity() instanceof ServerPlayer;
        int level = player ? KLPermissions.levelOf((ServerPlayer) src.getEntity()) : KLPermissions.ADMIN;
        String grade = !player ? "console"
                : level == KLPermissions.ADMIN ? "admin"
                : level == KLPermissions.ORGANISATEUR ? "organisateur" : "joueur";
        src.sendSuccess(() -> Component.literal("=== Commandes /ccis (" + grade + ") ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (HelpEntry e : HELP) {
            if (e.level() <= level) {
                Component row = Component.empty()
                        .append(Component.literal(e.command()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("  " + e.description()).withStyle(ChatFormatting.GRAY));
                src.sendSuccess(() -> row, false);
            }
        }
        return 1;
    }

    /** Annonce visible par tous les joueurs, quelle que soit leur equipe. */
    private static int say(CommandContext<CommandSourceStack> ctx) {
        Component msg = Component.empty()
                .append(Component.literal("[Annonce] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(fr.cosmicisland.Txt.of(StringArgumentType.getString(ctx, "texte")));
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(msg, false);
        return 1;
    }

    // ---------------------------------------------------------------- base et respawn

    /** /ccis base (et /base) : retour a la base de son equipe, impossible pendant une epreuve ou un conseil. */
    private static int goBase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (GameManager.isInGame(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal(
                    "Tu ne peux pas rentrer à ta base avant la fin de l'épreuve."));
            return 0;
        }
        if (ConseilManager.phase() != ConseilManager.Phase.IDLE && ConseilManager.isCandidate(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal(
                    "Tu ne peux pas rentrer à ta base avant la fin du conseil."));
            return 0;
        }
        String team = TeamManager.teamIdOf(player.getUUID());
        if (team == null) {
            ctx.getSource().sendFailure(Component.literal("Tu n'as pas d'équipe."));
            return 0;
        }
        SpawnManager.Spawn base = SpawnManager.get("base:" + team);
        if (base == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "La base de ton équipe n'est pas définie : préviens un organisateur."));
            return 0;
        }
        if (!SpawnManager.teleport(ctx.getSource().getServer(), player, base)) {
            ctx.getSource().sendFailure(Component.literal("Dimension de la base introuvable."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Retour à ta base.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int teamSpawnSet(CommandContext<CommandSourceStack> ctx, String prefix) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        SpawnManager.set(prefix + StringArgumentType.getString(ctx, "equipe").toLowerCase(), player);
        String what = prefix.equals("base:") ? "Base" : "Point de respawn";
        String msg = what + " de l'équipe " + team.name + " défini à ta position.";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int teamSpawnTp(CommandContext<CommandSourceStack> ctx, String prefix) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        SpawnManager.Spawn spawn = SpawnManager.get(prefix + StringArgumentType.getString(ctx, "equipe").toLowerCase());
        if (spawn == null) {
            ctx.getSource().sendFailure(Component.literal("Spawn non défini pour l'équipe " + team.name + "."));
            return 0;
        }
        if (!SpawnManager.teleport(ctx.getSource().getServer(), player, spawn)) {
            ctx.getSource().sendFailure(Component.literal("Dimension du spawn introuvable : " + spawn.dimension));
            return 0;
        }
        return 1;
    }

    /** Point d'equipe propre a un jeu (remplace le point d'equipe general pour ce jeu). */
    private static int gameZoneSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String game = StringArgumentType.getString(ctx, "jeu").toLowerCase();
        if (GameRegistry.get(game) == null) {
            ctx.getSource().sendFailure(Component.literal("Jeu inconnu : " + game));
            return 0;
        }
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        SpawnManager.set("zone:" + game + ":" + StringArgumentType.getString(ctx, "equipe").toLowerCase(), player);
        String msg = "Point de l'équipe " + team.name + " pour le jeu " + game + " défini à ta position.";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    // ---------------------------------------------------------------- feu de camp

    private static int campfireStatus(CommandContext<CommandSourceStack> ctx) {
        for (Component c : CampfireManager.statusLines()) {
            ctx.getSource().sendSuccess(() -> c, false);
        }
        return 1;
    }

    private static int campfireTp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        CampfireManager.Fire fire = CampfireManager.get(StringArgumentType.getString(ctx, "equipe"));
        if (fire == null) {
            ctx.getSource().sendFailure(Component.literal("Aucun feu défini pour l'équipe " + team.name + "."));
            return 0;
        }
        if (!CampfireManager.teleportTo(player, fire)) {
            ctx.getSource().sendFailure(Component.literal("Dimension du feu introuvable."));
            return 0;
        }
        return 1;
    }

    /** Definit le feu de l'equipe : il faut regarder un bloc "campfire" (a moins de 8 blocs). */
    private static int campfireSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        HitResult hit = player.pick(8.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Regarde un feu de camp (bloc campfire) à moins de 8 blocs, puis relance la commande."));
            return 0;
        }
        net.minecraft.core.BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!(player.level().getBlockState(pos).getBlock() instanceof CampfireBlock)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Ce bloc n'est pas un feu de camp. Pose un campfire et regarde-le."));
            return 0;
        }
        CampfireManager.set(StringArgumentType.getString(ctx, "equipe"), player.serverLevel(), pos);
        String msg = "Feu de camp de l'équipe " + team.name + " défini en " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int campfireRemove(CommandContext<CommandSourceStack> ctx) {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        if (!CampfireManager.remove(StringArgumentType.getString(ctx, "equipe"))) {
            ctx.getSource().sendFailure(Component.literal("Aucun feu défini pour l'équipe " + team.name + "."));
            return 0;
        }
        String msg = "Feu de camp de l'équipe " + team.name + " retiré.";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int campfireFuel(CommandContext<CommandSourceStack> ctx) {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "equipe");
        if (CampfireManager.get(id) == null) {
            ctx.getSource().sendFailure(Component.literal("Aucun feu défini pour l'équipe " + team.name + "."));
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "valeur");
        CampfireManager.setFuel(id, value);
        String msg = "Jauge du feu de l'équipe " + team.name + " : " + CampfireManager.percent(CampfireManager.get(id)) + "%.";
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }
}
