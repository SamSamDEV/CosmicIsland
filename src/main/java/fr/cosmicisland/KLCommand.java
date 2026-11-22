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
            (ctx, builder) -> SharedSuggestionProvider.suggest(TeamManager.names(), builder);

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
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::campfireTp)))
                        .then(Commands.literal("set")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::campfireSet)))
                        .then(Commands.literal("remove")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::campfireRemove)))
                        .then(Commands.literal("fuel")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 100000.0))
                                                .executes(KLCommand::campfireFuel)))))
                .then(SeasonManager.command())
                .then(FinaleManager.command())
                .then(fr.cosmicisland.survival.FoodCommand.command())
                .then(Commands.literal("say")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(KLCommand::say)))
                .then(Commands.literal("admin")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(c -> {
                            fr.cosmicisland.admin.AdminNetwork.sendState(c.getSource().getPlayerOrException(), true);
                            return 1;
                        })
                        .then(Commands.literal("chest").executes(KLCommand::admin)))
                .then(Commands.literal("gui")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(c -> {
                            fr.cosmicisland.admin.AdminNetwork.sendState(c.getSource().getPlayerOrException(), true);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(KLCommand::reload))
                .then(Commands.literal("games")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(KLCommand::gameList))
                .then(Commands.literal("enable")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(GAME_IDS)
                                .executes(c -> setEnabled(c, true))))
                .then(Commands.literal("disable")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(GAME_IDS)
                                .executes(c -> setEnabled(c, false))))
                .then(Commands.literal("start")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.argument("game", StringArgumentType.word())
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
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(GAME_IDS)
                                .executes(c -> gameStop(c, StringArgumentType.getString(c, "game")))))
                .then(Commands.literal("join")
                        .executes(KLCommand::gameJoin))
                .then(Commands.literal("decline")
                        .executes(KLCommand::gameDecline))
                .then(Commands.literal("spawn")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.literal("set")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SPAWN_IDS)
                                        .executes(KLCommand::spawnSet)))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(SPAWN_IDS)
                                        .executes(KLCommand::spawnTp)))
                        .then(Commands.literal("base")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(c -> teamSpawnSet(c, "base:"))))
                                .then(Commands.literal("tp")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(c -> teamSpawnTp(c, "base:")))))
                        .then(Commands.literal("respawn")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(c -> teamSpawnSet(c, "respawn:"))))
                                .then(Commands.literal("tp")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(c -> teamSpawnTp(c, "respawn:")))))
                        .then(Commands.literal("game")
                                .then(Commands.argument("game", StringArgumentType.word())
                                        .suggests(GAME_IDS)
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(TEAM_IDS)
                                                .executes(KLCommand::gameZoneSet))))
                        .then(Commands.literal("list")
                                .executes(KLCommand::spawnList)))
                .then(Commands.literal("ranking")
                        .executes(c -> rankingTop(c, null))
                        .then(Commands.literal("game")
                                .then(Commands.argument("game", StringArgumentType.word())
                                        .suggests(GAME_IDS)
                                        .executes(c -> rankingTop(c, StringArgumentType.getString(c, "game")))))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(KLCommand::rankingPlayer)))
                        .then(Commands.literal("reset")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.literal("confirm")
                                        .executes(KLCommand::rankingResetConfirm))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(RANK_TARGETS)
                                        .executes(KLCommand::rankingResetAsk)))
                        .then(Commands.literal("add")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("game", StringArgumentType.word())
                                                .suggests(GAME_IDS)
                                                .then(Commands.argument("count", IntegerArgumentType.integer(-100, 100))
                                                        .executes(KLCommand::rankingAdd))))))
                .then(Commands.literal("placeholder")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(KLCommand::placeholderTest)))
                .then(Commands.literal("vote")
                        .executes(KLCommand::conseilVote))
                .then(Commands.literal("bracelet")
                        .then(Commands.literal("yes").executes(c -> collierAnswer(c, true)))
                        .then(Commands.literal("no").executes(c -> collierAnswer(c, false))))
                .then(Commands.literal("council")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.literal("start")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::conseilStart)))
                        .then(Commands.literal("test")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(c -> conseilTest(c, 3))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                                .executes(c -> conseilTest(c, IntegerArgumentType.getInteger(c, "count"))))))
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
                        .then(Commands.literal("immunity")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(KLCommand::conseilImmunity))))
                .then(Commands.literal("role")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .suggests(ROLES)
                                                .executes(KLCommand::roleSet))))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(KLCommand::roleGet))))
                .then(Commands.literal("team")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.literal("list")
                                .executes(KLCommand::teamList))
                        .then(Commands.literal("add")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                .executes(KLCommand::teamAdd))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(KLCommand::teamRemove)))
                        .then(Commands.literal("draw")
                                .executes(c -> teamDraw(c, false))
                                .then(Commands.literal("all").executes(c -> teamDraw(c, true))))
                        .then(Commands.literal("name")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(c -> teamEdit(c, true)))))
                        .then(Commands.literal("tag")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(c -> teamEdit(c, false)))))
                        .then(Commands.literal("color")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .then(Commands.argument("color", ColorArgument.color())
                                                .executes(KLCommand::teamColor))))
                        .then(Commands.literal("create")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("color", ColorArgument.color())
                                                .executes(KLCommand::teamCreate))))
                        .then(Commands.literal("delete")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(TEAM_IDS)
                                        .executes(KLCommand::teamDelete)))
                        .then(Commands.literal("merge")
                                .executes(KLCommand::teamMerge)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(KLCommand::teamMerge)))
                        .then(Commands.literal("unmerge")
                                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                                .executes(KLCommand::teamUnmerge)));

        // {v_reunification} : lancee a la main par un admin ou un organisateur
        root.then(Commands.literal("merge")
                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                .executes(KLCommand::teamMerge)
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(KLCommand::teamMerge)));

        // Reponse du staff a un joueur : /ccis reply <joueur> <message> (les joueurs ecrivent avec /admin <message>)
        root.then(Commands.literal("reply")
                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(c -> {
                                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player");
                                    String msg = StringArgumentType.getString(c, "message");
                                    fr.cosmicisland.chat.ChatManager.reply(target, c.getSource().getTextName(), msg);
                                    c.getSource().sendSuccess(() -> Txt.ok("Réponse envoyée à " + target.getGameProfile().getName() + "."), false);
                                    return 1;
                                }))));
        // Seul message prive autorise pour les joueurs : /admin <message>
        dispatcher.register(Commands.literal("admin")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> fr.cosmicisland.chat.ChatManager.toStaff(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "message")))));
        // Chat global ON = tout le monde se lit ; OFF = chat par equipe
        root.then(Commands.literal("chat")
                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                .executes(c -> {
                    boolean on = !fr.cosmicisland.chat.ChatManager.config().teamChat;
                    c.getSource().sendSuccess(() -> Txt.ok("Chat global : " + (on ? "ON (tout le monde se lit)" : "OFF (chat par équipe)")), false);
                    return 1;
                })
                .then(Commands.literal("global")
                        .then(Commands.literal("on").executes(c -> {
                            fr.cosmicisland.chat.ChatManager.setGlobal(true);
                            c.getSource().sendSuccess(() -> Txt.ok("Chat global ON : tout le monde se lit."), true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(c -> {
                            fr.cosmicisland.chat.ChatManager.setGlobal(false);
                            c.getSource().sendSuccess(() -> Txt.ok("Chat global OFF : chat par équipe."), true);
                            return 1;
                        }))));

        // Roulette d'equilibrage des equipes avant une epreuve
        root.then(Commands.literal("balance")
                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                .executes(c -> {
                    String err = GameManager.balance();
                    if (err != null) {
                        c.getSource().sendFailure(Txt.err(err));
                        return 0;
                    }
                    c.getSource().sendSuccess(() -> Txt.ok("Roulette lancée."), true);
                    return 1;
                })
                .then(Commands.argument("team", StringArgumentType.word())
                        .suggests(TEAM_IDS)
                        .executes(c -> {
                            String err = GameManager.balance(StringArgumentType.getString(c, "team"));
                            if (err != null) {
                                c.getSource().sendFailure(Txt.err(err));
                                return 0;
                            }
                            c.getSource().sendSuccess(() -> Txt.ok("Roulette lancée."), true);
                            return 1;
                        }))
                .then(Commands.literal("test")
                        .executes(c -> balanceTest(c, 6))
                        .then(Commands.argument("players", IntegerArgumentType.integer(2, 10))
                                .executes(c -> balanceTest(c, IntegerArgumentType.getInteger(c, "players"))))));

        // Soif
        root.then(Commands.literal("thirst")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(() -> Txt.info(fr.cosmicisland.survival.ThirstManager.status(p)), false);
                    return 1;
                })
                .then(Commands.literal("set")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.players())
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(c -> {
                                            int v = IntegerArgumentType.getInteger(c, "value");
                                            int n = 0;
                                            for (ServerPlayer t : net.minecraft.commands.arguments.EntityArgument.getPlayers(c, "player")) {
                                                fr.cosmicisland.survival.ThirstManager.setLevel(t, v);
                                                n++;
                                            }
                                            int done = n;
                                            c.getSource().sendSuccess(() -> Txt.ok("Soif réglée à " + v + " pour " + done + " joueur(s)."), true);
                                            return n;
                                        }))))
                .then(Commands.literal("on")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(c -> {
                            fr.cosmicisland.survival.ThirstManager.setEnabled(true);
                            c.getSource().sendSuccess(() -> Txt.ok("Soif activée."), true);
                            return 1;
                        }))
                .then(Commands.literal("off")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(c -> {
                            fr.cosmicisland.survival.ThirstManager.setEnabled(false);
                            c.getSource().sendSuccess(() -> Txt.ok("Soif désactivée."), true);
                            return 1;
                        })));

        // Donner les objets de survie : /ccis give <marmite|gourde|riz|coquillage> <joueurs> [nombre]
        root.then(Commands.literal("give")
                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                .then(Commands.argument("item", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("pot", "canteen", "rice", "shell", "lighter", "campfire"), b))
                        .then(Commands.argument("players", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(c -> giveSurvival(c, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> giveSurvival(c, IntegerArgumentType.getInteger(c, "count")))))));

        // Langue des messages (fr / en)
        root.then(Commands.literal("language")
                .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Txt.info("Langue actuelle : " + Lang.language() + " (" + String.join(", ", Lang.available()) + ")"), false);
                    return 1;
                })
                .then(Commands.argument("lang", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Lang.available(), b))
                        .executes(c -> {
                            String l = StringArgumentType.getString(c, "lang").toLowerCase();
                            if (!Lang.setLanguage(l)) {
                                c.getSource().sendFailure(Txt.err("Langues disponibles : " + String.join(", ", Lang.available()) + "."));
                                return 0;
                            }
                            Vocab.broadcast();
                            CommandAliases.refresh(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Txt.ok("Langue des messages : " + l + " (appliquée immédiatement, sans reload)"), true);
                            return 1;
                        })));

        // Historique de la saison
        root.then(Commands.literal("history")
                .executes(c -> showHistory(c, null, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(c -> showHistory(c, null, IntegerArgumentType.getInteger(c, "page"))))
                .then(Commands.literal("archive")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SeasonHistory.archives(), b))
                                .executes(c -> showHistory(c, StringArgumentType.getString(c, "name"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> showHistory(c, StringArgumentType.getString(c, "name"),
                                                IntegerArgumentType.getInteger(c, "page"))))))
                .then(Commands.literal("new")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(c -> {
                                    String old = SeasonHistory.data().season;
                                    SeasonHistory.newSeason(StringArgumentType.getString(c, "name"));
                                    c.getSource().sendSuccess(() -> Txt.ok("« " + old + " » archivée. Nouvelle saison : "
                                            + SeasonHistory.data().season + "."), true);
                                    return 1;
                                })))
                .then(Commands.literal("clear")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(c -> {
                            c.getSource().sendSystemMessage(Txt.info("Cela efface le journal de la saison en cours. Pour confirmer : /ccis history clear confirm"));
                            return 1;
                        })
                        .then(Commands.literal("confirm").executes(c -> {
                            SeasonHistory.clear();
                            c.getSource().sendSuccess(() -> Txt.ok("Journal de la saison en cours effacé."), true);
                            return 1;
                        }))));
        // Abandon volontaire (avec confirmation) et retour d'un elimine
        root.then(Commands.literal("giveup")
                .executes(c -> {
                    String err = PlayerStatus.askAbandon(c.getSource().getPlayerOrException());
                    if (err != null) {
                        c.getSource().sendFailure(Txt.err(err));
                        return 0;
                    }
                    return 1;
                })
                .then(Commands.literal("confirm")
                        .executes(c -> {
                            String err = PlayerStatus.abandon(c.getSource().getPlayerOrException());
                            if (err != null) {
                                c.getSource().sendFailure(Txt.err(err));
                                return 0;
                            }
                            return 1;
                        })));
        root.then(Commands.literal("revive")
                .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(c -> {
                            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
                            if (profiles.size() != 1) {
                                c.getSource().sendFailure(Txt.err("Indique un seul joueur."));
                                return 0;
                            }
                            PlayerStatus.askTeam(c.getSource(), profiles.iterator().next());
                            return 1;
                        })
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(TEAM_IDS)
                                .executes(c -> {
                                    Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
                                    int n = 0;
                                    for (GameProfile profile : profiles) {
                                        String err = PlayerStatus.revive(c.getSource().getServer(), profile,
                                                StringArgumentType.getString(c, "team"));
                                        if (err != null) {
                                            c.getSource().sendFailure(Txt.err(err));
                                        } else {
                                            n++;
                                        }
                                    }
                                    int done = n;
                                    c.getSource().sendSuccess(() -> Txt.ok(done + " joueur(s) remis en jeu."), true);
                                    return n;
                                }))));

        // Commandes propres a chaque jeu (ex. /ccis poteaux ...)
        for (String id : GameRegistry.ids()) {
            KLGame game = GameRegistry.get(id);
            LiteralArgumentBuilder<CommandSourceStack> node = game == null ? null : game.commands();
            if (node != null) {
                root.then(node);
            }
        }
        dispatcher.register(root);
        CommandAliases.install(dispatcher);
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
            staff = Txt.lit("Admin").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        } else if (level == KLPermissions.ORGANISATEUR) {
            staff = Txt.lit("Organisateur").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else {
            staff = Txt.lit("Non").withStyle(ChatFormatting.GRAY);
        }

        // Role : participant = vert, spectateur = cyan, organisateur = or
        KLRole role = RoleManager.effective(player);
        ChatFormatting roleColor = role == KLRole.PARTICIPANT ? ChatFormatting.GREEN
                : role == KLRole.SPECTATEUR ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        MutableComponent roleValue = Txt.lit(capitalize(role.id())).withStyle(roleColor);

        // Equipe : tag en gras + nom, dans la couleur de l'equipe
        String teamId = TeamManager.teamIdOf(player.getUUID());
        MutableComponent team;
        if (teamId == null) {
            team = Txt.lit("Aucune").withStyle(ChatFormatting.GRAY);
        } else {
            TeamManager.KLTeam t = TeamManager.get(teamId);
            team = Component.empty()
                    .append(boldTag(t))
                    .append(Txt.lit(" " + t.name).withStyle(t.format()));
        }

        Component header = Txt.lit("=== Cosmic Island ===")
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
                .append(Txt.lit(label + " : ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
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
        ctx.getSource().sendSuccess(() -> Txt.ok("Configs Cosmic Island rechargées."), true);
        return 1;
    }

    // ---------------------------------------------------------------- roles

    private static int roleSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        KLRole role = KLRole.parse(StringArgumentType.getString(ctx, "role"));
        if (role == null) {
            ctx.getSource().sendFailure(Txt.err("Rôle inconnu. Choix : participant, spectateur, organisateur."));
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
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return profiles.size();
    }

    private static int roleGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        for (GameProfile profile : profiles) {
            KLRole role = RoleManager.get(profile.getId());
            String msg = profile.getName() + " : "
                    + (role == null ? "non enregistré (spectateur par défaut)" : role.id());
            ctx.getSource().sendSuccess(() -> Txt.ok(msg), false);
        }
        return profiles.size();
    }

    // ---------------------------------------------------------------- equipes

    /** Argument "equipe" converti en identifiant interne : accepte l'identifiant (rouge) ou le nom actuel (Phoenix). */
    private static String teamArg(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "team");
        String id = TeamManager.resolveId(raw);
        return id != null ? id : raw;
    }

    /** Retourne l'equipe de l'argument "equipe", ou null (message d'erreur deja envoye). */
    private static TeamManager.KLTeam findTeam(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "team");
        TeamManager.KLTeam team = TeamManager.get(id);
        if (team == null) {
            ctx.getSource().sendFailure(Txt.err(
                    "Équipe inconnue : " + id + " (équipes : " + String.join(", ", TeamManager.names()) + ")"));
        }
        return team;
    }

    private static int teamList(CommandContext<CommandSourceStack> ctx) {
        String state = TeamManager.isMerged() ? "fusionnées (tribu unique)" : "séparées";
        ctx.getSource().sendSuccess(() -> Txt.ok("Équipes " + state + " :"), false);
        for (Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            TeamManager.KLTeam t = e.getValue();
            Component line = Component.empty()
                    .append(boldTag(t))
                    .append(Txt.lit(" " + t.name + " (" + e.getKey() + ") - "
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
        String id = teamArg(ctx).toLowerCase();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        for (GameProfile profile : profiles) {
            TeamManager.assign(id, profile);
        }
        String msg = profiles.size() + " joueur(s) ajouté(s) à l'équipe " + team.name;
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return profiles.size();
    }

    /** Tirage au sort des equipes : les participants sans equipe (ou tous avec "all") sont repartis a parts egales. */
    private static int teamDraw(CommandContext<CommandSourceStack> ctx, boolean all) {
        java.util.Map<String, Integer> placed = TeamManager.draw(all);
        if (placed == null) {
            ctx.getSource().sendFailure(Txt.err(Tr.s("team.draw.noteams")));
            return 0;
        }
        int total = placed.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            ctx.getSource().sendFailure(Txt.err(Tr.s("team.draw.nobody")));
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : placed.entrySet()) {
            TeamManager.KLTeam t = TeamManager.get(e.getKey());
            sb.append("\n &").append(t.format().getChar()).append("\u25CF ").append(t.name).append(" &7: &f").append(e.getValue());
        }
        ctx.getSource().sendSuccess(() -> Txt.ok(Tr.s("team.draw.done", total) + sb), true);
        int teamCount = placed.size();
        int inTeams = 0;
        for (String id : placed.keySet()) {
            inTeams += TeamManager.get(id).members.size();
        }
        if (teamCount > 0 && inTeams % teamCount != 0) {
            int lower = inTeams / teamCount * teamCount;
            ctx.getSource().sendSystemMessage(Txt.of(Tr.s("team.draw.uneven", inTeams, teamCount, lower, lower + teamCount)));
        }
        return total;
    }

    private static int teamRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        for (GameProfile profile : profiles) {
            TeamManager.remove(profile);
        }
        String msg = profiles.size() + " joueur(s) retiré(s) de leur équipe";
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return profiles.size();
    }

    private static int teamEdit(CommandContext<CommandSourceStack> ctx, boolean isName) {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "text");
        if (isName) {
            team.name = text;
            if (team.autoTag) {
                team.tag = TeamManager.autoTagFor(text); // Mars -> [MARS]
            }
        } else if (text.equalsIgnoreCase("auto")) {
            team.autoTag = true;
            team.tag = TeamManager.autoTagFor(team.name);
        } else {
            team.tag = text;
            team.autoTag = false;
        }
        TeamManager.changed();
        ctx.getSource().sendSuccess(() -> Txt.ok("Équipe mise à jour."), true);
        return 1;
    }

    private static int teamColor(CommandContext<CommandSourceStack> ctx) {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        team.color = ColorArgument.getColor(ctx, "color").getName();
        TeamManager.changed();
        ctx.getSource().sendSuccess(() -> Txt.ok("Couleur de l'équipe mise à jour."), true);
        return 1;
    }

    private static int teamMerge(CommandContext<CommandSourceStack> ctx) {
        String name = null;
        try {
            name = StringArgumentType.getString(ctx, "name");
        } catch (IllegalArgumentException ignored) {
            // pas de nom : le nom par defaut du vocabulaire est utilise
        }
        TeamManager.merge(name);
        String shown = TeamManager.get(TeamManager.MERGED_ID).name;
        ctx.getSource().sendSuccess(() -> Txt.ok(
                "{v_reunification} effectuée : tous les joueurs sont dans l'équipe " + shown + "."), true);
        return 1;
    }

    private static int teamCreate(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        String error = TeamManager.createTeam(id, ColorArgument.getColor(ctx, "color"));
        if (error != null) {
            ctx.getSource().sendFailure(Txt.err(error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Txt.ok("Équipe créée : " + TeamManager.get(id).name
                + ". Renomme-la avec /ccis team name, puis définis ses points de spawn et son feu de camp."), true);
        return 1;
    }

    private static int teamDelete(CommandContext<CommandSourceStack> ctx) {
        String error = TeamManager.deleteTeam(StringArgumentType.getString(ctx, "team"));
        if (error != null) {
            ctx.getSource().sendFailure(Txt.err(error));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Txt.ok("Équipe supprimée (ses joueurs n'ont plus d'équipe)."), true);
        return 1;
    }

    // ---------------------------------------------------------------- epreuves

    private static final SuggestionProvider<CommandSourceStack> GAME_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(GameRegistry.ids(), builder);

    private static int gameList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Txt.ok("=== Épreuves ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (String id : GameRegistry.ids()) {
            GameConfig c = GameRegistry.config(id);
            if (c == null) {
                continue;
            }
            Component status = c.enabled
                    ? Txt.lit("ACTIVÉ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Txt.lit("DÉSACTIVÉ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            Component row = Component.empty()
                    .append(Txt.lit(c.displayName + " (" + id + ") : ").withStyle(ChatFormatting.WHITE))
                    .append(status);
            src.sendSuccess(() -> row, false);
        }
        String active = GameManager.describe();
        src.sendSuccess(() -> line("Épreuve en cours", Txt.lit(active).withStyle(ChatFormatting.YELLOW)), false);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String id = StringArgumentType.getString(ctx, "game");
        GameConfig c = GameRegistry.config(id);
        if (c == null) {
            ctx.getSource().sendFailure(Txt.err("Jeu inconnu : " + id));
            return 0;
        }
        c.enabled = enabled;
        GameRegistry.saveConfig(id);
        String msg = "Jeu " + c.displayName + (enabled ? " activé." : " désactivé.");
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return 1;
    }

    private static int gameStart(CommandContext<CommandSourceStack> ctx) {
        String err = GameManager.start(StringArgumentType.getString(ctx, "game"));
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        return 1;
    }

    private static int gameGo(CommandContext<CommandSourceStack> ctx) {
        String err = GameManager.go();
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Txt.ok("Décompte lancé."), true);
        return 1;
    }

    private static int gameStop(CommandContext<CommandSourceStack> ctx, String id) {
        if (GameManager.state() == GameManager.State.IDLE) {
            ctx.getSource().sendFailure(Txt.err("Aucune épreuve en cours."));
            return 0;
        }
        if (id != null && !GameManager.isActive(id)) {
            ctx.getSource().sendFailure(Txt.err("L'épreuve en cours n'est pas : " + id));
            return 0;
        }
        GameManager.finish(true);
        return 1;
    }

    private static int gameJoin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String err = GameManager.join(ctx.getSource().getPlayerOrException());
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        return 1;
    }

    private static int gameDecline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String err = GameManager.decline(ctx.getSource().getPlayerOrException());
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        return 1;
    }

    // ---------------------------------------------------------------- spawns

    private static int spawnSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = teamArg(ctx).toLowerCase();
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
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return 1;
    }

    private static int spawnTp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = teamArg(ctx).toLowerCase();
        if (!SpawnManager.isSpecial(id) && findTeam(ctx) == null) {
            return 0;
        }
        SpawnManager.Spawn spawn = SpawnManager.get(id);
        if (spawn == null) {
            ctx.getSource().sendFailure(Txt.err("Spawn non défini : " + id));
            return 0;
        }
        if (!SpawnManager.teleport(ctx.getSource().getServer(), player, spawn)) {
            ctx.getSource().sendFailure(Txt.err("Dimension du spawn introuvable : " + spawn.dimension));
            return 0;
        }
        return 1;
    }

    private static int spawnList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Txt.ok("=== Spawns ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            String id = e.getKey();
            String name = e.getValue().name;
            spawnRow(src, name + " (zone de jeu)", id);
            spawnRow(src, name + " (base)", "base:" + id);
            spawnRow(src, name + " (respawn mort)", "respawn:" + id);
        }
        spawnRow(src, "{v_conseil}", "conseil");
        spawnRow(src, "{v_elimination}", "elimination");
        spawnRow(src, "Jury", "jury");
        return 1;
    }

    private static void spawnRow(CommandSourceStack src, String label, String key) {
        SpawnManager.Spawn s = SpawnManager.get(key);
        Component value = s == null
                ? Txt.lit("non défini").withStyle(ChatFormatting.RED)
                : Txt.lit(String.format("%s  %.1f %.1f %.1f", s.dimension, s.x, s.y, s.z))
                        .withStyle(ChatFormatting.GREEN);
        Component row = line(label, value);
        src.sendSuccess(() -> row, false);
    }

    private static int gameFreeze(CommandContext<CommandSourceStack> ctx) {
        String err = GameManager.freeze();
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        return 1;
    }

    private static int gameUnfreeze(CommandContext<CommandSourceStack> ctx) {
        GameManager.unfreeze();
        ctx.getSource().sendSuccess(() -> Txt.ok("Participants libérés."), true);
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
                src.sendFailure(Txt.err("Jeu inconnu : " + gameId));
                return 0;
            }
            title = "Classement - " + c.displayName;
        }
        String header = "=== " + title + " ===";
        src.sendSuccess(() -> Txt.ok(header).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        List<RankingManager.Row> rows = RankingManager.top(gameId, 10);
        if (rows.isEmpty()) {
            src.sendSuccess(() -> Txt.ok("Aucun joueur enregistré.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        int rank = 1;
        for (RankingManager.Row row : rows) {
            ChatFormatting color = rank == 1 ? ChatFormatting.GOLD
                    : rank == 2 ? ChatFormatting.GRAY
                    : rank == 3 ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY;
            String wins = row.wins() + (row.wins() > 1 ? " victoires" : " victoire");
            Component line = Component.empty()
                    .append(Txt.lit("#" + rank + " ").withStyle(color, ChatFormatting.BOLD))
                    .append(Txt.lit(row.name() == null ? "?" : row.name()).withStyle(ChatFormatting.WHITE))
                    .append(Txt.lit("  " + wins).withStyle(ChatFormatting.GREEN));
            src.sendSuccess(() -> line, false);
            rank++;
        }
        return rows.size();
    }

    private static int rankingPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        for (GameProfile profile : profiles) {
            RankingManager.PlayerStats s = RankingManager.statsOf(profile.getId());
            int total = s == null ? 0 : s.wins;
            int played = s == null ? 0 : s.played;
            Component summary = line(profile.getName(), Txt.lit(
                    total + " victoire(s) sur " + played + " épreuve(s)").withStyle(ChatFormatting.GREEN));
            ctx.getSource().sendSuccess(() -> summary, false);
            if (s != null) {
                for (Map.Entry<String, Integer> e : s.perGame.entrySet()) {
                    GameConfig c = GameRegistry.config(e.getKey());
                    String name = c == null ? e.getKey() : c.displayName;
                    Component detail = Txt.lit("  - " + name + " : " + e.getValue())
                            .withStyle(ChatFormatting.GRAY);
                    ctx.getSource().sendSuccess(() -> detail, false);
                }
            }
        }
        return profiles.size();
    }

    private static int rankingAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        String game = StringArgumentType.getString(ctx, "game").toLowerCase();
        int n = IntegerArgumentType.getInteger(ctx, "count");
        if (GameRegistry.get(game) == null) {
            ctx.getSource().sendFailure(Txt.err("Jeu inconnu : " + game));
            return 0;
        }
        for (GameProfile profile : profiles) {
            RankingManager.add(profile, game, n);
        }
        String msg = profiles.size() + " joueur(s) : " + (n >= 0 ? "+" : "") + n + " victoire(s) pour " + game;
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return profiles.size();
    }

    private static int rankingResetAsk(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "target").toLowerCase();
        if (!target.equals("all") && GameRegistry.get(target) == null) {
            ctx.getSource().sendFailure(Txt.err(
                    "Cible inconnue : " + target + " (utilise all ou l'identifiant d'un jeu)."));
            return 0;
        }
        RankingManager.requestReset(sourceKey(ctx), target);
        Component msg = Component.empty()
                .append(Txt.lit("ATTENTION ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Txt.lit("Remise à zéro de '" + target + "'. Tape ").withStyle(ChatFormatting.GRAY))
                .append(Txt.lit("/ccis ranking reset confirm").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Txt.lit(" dans les 30 secondes pour confirmer (copie de secours automatique).")
                        .withStyle(ChatFormatting.GRAY));
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int rankingResetConfirm(CommandContext<CommandSourceStack> ctx) {
        RankingManager.ResetResult r = RankingManager.confirmReset(sourceKey(ctx));
        if (r == null) {
            ctx.getSource().sendFailure(Txt.err(
                    "Aucune remise à zéro en attente (elle expire au bout de 30 secondes)."));
            return 0;
        }
        String msg = "Classement remis à zéro (" + r.target() + "). Copie de secours : "
                + (r.backup() == null ? "aucune" : r.backup());
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return 1;
    }

    private static int placeholderTest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String result = Placeholders.apply(StringArgumentType.getString(ctx, "text"), player);
        ctx.getSource().sendSuccess(() -> fr.cosmicisland.Txt.of(result), false);
        return 1;
    }

    // ---------------------------------------------------------------- conseil

    private static final SuggestionProvider<CommandSourceStack> SPAWN_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Stream.concat(TeamManager.names().stream(), Stream.of("conseil", "elimination", "jury")), builder);

    private static int conseilStart(CommandContext<CommandSourceStack> ctx) {
        String err = ConseilManager.start(teamArg(ctx));
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        return 1;
    }

    private static int conseilTest(CommandContext<CommandSourceStack> ctx, int fakes) {
        String err = ConseilManager.start(teamArg(ctx), fakes);
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        return 1;
    }

    private static int conseilMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String err = ConseilAdminMenu.open(ctx.getSource().getPlayerOrException());
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
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
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        return 1;
    }

    private static int conseilImmunity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        int done = 0;
        for (GameProfile profile : profiles) {
            String err = ConseilManager.toggleImmune(profile.getId());
            if (err != null) {
                ctx.getSource().sendFailure(Txt.err(profile.getName() + " : " + err));
            } else {
                done++;
            }
        }
        return done;
    }

    private static int collierAnswer(CommandContext<CommandSourceStack> ctx, boolean use) throws CommandSyntaxException {
        String err = ConseilManager.answerCollier(ctx.getSource().getPlayerOrException(), use);
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        return 1;
    }

    private static int conseilVote(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String err = ConseilMenus.openVote(ctx.getSource().getPlayerOrException());
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
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
            new HelpEntry("/ccis vote", "Rouvrir le menu de vote {v_du_conseil}", KLPermissions.NONE),
            new HelpEntry("/admin <message>", "Écrire à l'organisation (seul message privé autorisé)", KLPermissions.NONE),
            new HelpEntry("/ccis reply <joueur> <réponse>", "Répondre en privé à un joueur", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis chat global <on|off>", "ON : tout le monde se lit / OFF : chat par équipe", KLPermissions.ADMIN),
            new HelpEntry("/ccis balance [équipe]", "Roulette : tire au sort les joueurs en trop pour équilibrer les équipes (ex. 5v5 au lieu de 6v5)", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis balance test [joueurs]", "Teste la roulette en solo avec des joueurs fictifs", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis thirst", "Ta soif actuelle", KLPermissions.NONE),
            new HelpEntry("/ccis thirst set <joueurs> <valeur>", "Règle la soif (0-20)", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis thirst on|off", "Active ou coupe la soif", KLPermissions.ADMIN),
            new HelpEntry("/ccis give <marmite|gourde|riz|coquillage> <joueurs> [nombre]", "Donne un objet de survie", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis language <fr|en>", "Langue des messages du mod (français / anglais)", KLPermissions.ADMIN),
            new HelpEntry("/ccis team draw [all]", "Tirage au sort des équipes : répartit les participants sans équipe (ou tout le monde avec all) à parts égales", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis history [page]", "Historique de la saison (éliminations, colliers, fusion...)", KLPermissions.NONE),
            new HelpEntry("/ccis history archive <saison>", "Relire une saison archivée", KLPermissions.NONE),
            new HelpEntry("/ccis history new <nom>", "Archive la saison en cours et en commence une nouvelle", KLPermissions.ADMIN),
            new HelpEntry("/ccis history clear", "Efface le journal de la saison en cours (avec confirmation)", KLPermissions.ADMIN),
            new HelpEntry("/ccis giveup", "Abandonner l'aventure (avec confirmation) : tu deviens spectateur", KLPermissions.NONE),
            new HelpEntry("/ccis revive <joueur> [équipe]", "Remet un éliminé ou un abandon en jeu : choisis son équipe en cliquant dans le chat (il quitte l'ancienne)", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis base  (ou /base)", "Retourner à la base de ton équipe (impossible pendant une épreuve)", KLPermissions.NONE),
            new HelpEntry("/ccis ranking", "Classement général des victoires", KLPermissions.NONE),
            new HelpEntry("/ccis ranking jeu <jeu>", "Classement d'un jeu", KLPermissions.NONE),
            new HelpEntry("/ccis ranking player <pseudo>", "Victoires d'un joueur", KLPermissions.NONE),
            // Organisateurs
            new HelpEntry("/ccis games", "Liste des épreuves et leur état", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis start <jeu>", "Envoie les invitations d'une épreuve", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis freeze | unfreeze", "Immobilise ou libère les participants", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis go", "Lance le décompte et le départ", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis stop [jeu]", "Arrête l'épreuve en cours", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis team list|add|remove|name|tag|color", "Gérer les équipes", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis poteaux zone pos1|pos2|info", "Zone d'eau des poteaux", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis poteaux check", "Qui est sur un poteau ?", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis council start|test <équipe>", "Lancer {v_un_conseil} (ou un test)", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis council menu|status", "Menu et état {v_du_conseil}", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis council vote close|reveal|revote|close|cancel", "Piloter {v_le_conseil}", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis council immunity <joueur>", "Activer ou retirer l'immunité", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis campfire status|tp <équipe>", "État des feux de camp, se téléporter à un feu", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis say <message>", "Annonce visible par tout le monde", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis placeholder <texte>", "Tester les placeholders", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis admin", "Menu admin : épreuves, équipes, joueurs, {v_le_conseil}, classement, spawns", KLPermissions.ORGANISATEUR),
            // Admins
            new HelpEntry("/ccis reload", "Recharge les fichiers de config", KLPermissions.ADMIN),
            new HelpEntry("/ccis role set|get <joueur> [rôle]", "Gérer les rôles (participant, spectateur, organisateur)", KLPermissions.ADMIN),
            new HelpEntry("/ccis enable|disable <jeu>", "Activer ou désactiver un jeu", KLPermissions.ADMIN),
            new HelpEntry("/ccis campfire set|remove|fuel <équipe>", "Définir (en regardant le feu), retirer, régler la jauge", KLPermissions.ADMIN),
            new HelpEntry("/ccis finale [start|winner <joueur>|vote start|reveal|jury|status|reset]", "Finale : 3 derniers, poteaux, choix de l'adversaire, vote du jury", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis season [start|pause|resume|end|day <n|next>|auto <true|false>|phase <p>|step <e|next>|mergeat <n>|finaleat <n>|reset]", "Déroulement de la saison : jour, phase, étape de l'épisode, tableau de bord", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis merge [nom]", "{v_reunification} (à la main) : crée l'équipe unique, y met tout le monde et annonce", KLPermissions.ORGANISATEUR),
            new HelpEntry("/ccis team create <id> <couleur>", "Crée une équipe (autant que de couleurs, minimum 2 en jeu)", KLPermissions.ADMIN),
            new HelpEntry("/ccis team delete <équipe>", "Supprime une équipe (il en faut au moins 2)", KLPermissions.ADMIN),
            new HelpEntry("/ccis team unmerge", "Retour aux équipes séparées", KLPermissions.ADMIN),
            new HelpEntry("/ccis spawn set|tp|list", "Zone de jeu des équipes, {v_le_conseil}, {v_elimination}", KLPermissions.ADMIN),
            new HelpEntry("/ccis spawn base|respawn set|tp <équipe>", "Base et respawn (mort) des équipes", KLPermissions.ADMIN),
            new HelpEntry("/ccis spawn game <jeu> <équipe>", "Point d'équipe propre à un jeu", KLPermissions.ADMIN),
            new HelpEntry("/ccis ranking reset|add", "Remise à zéro et corrections du classement", KLPermissions.ADMIN));

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        boolean player = src.getEntity() instanceof ServerPlayer;
        int level = player ? KLPermissions.levelOf((ServerPlayer) src.getEntity()) : KLPermissions.ADMIN;
        String grade = !player ? "console"
                : level == KLPermissions.ADMIN ? "admin"
                : level == KLPermissions.ORGANISATEUR ? "organisateur" : "joueur";
        src.sendSuccess(() -> Txt.ok("=== Commandes /ccis (" + grade + ") ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (HelpEntry e : HELP) {
            if (e.level() <= level) {
                Component row = Component.empty()
                        .append(Txt.lit(e.command()).withStyle(ChatFormatting.YELLOW))
                        .append(Txt.lit("  " + e.description()).withStyle(ChatFormatting.GRAY));
                src.sendSuccess(() -> row, false);
            }
        }
        return 1;
    }

    /** Annonce visible par tous les joueurs, quelle que soit leur equipe. */
    private static int say(CommandContext<CommandSourceStack> ctx) {
        Component msg = Component.empty()
                .append(Txt.lit("[Annonce] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(fr.cosmicisland.Txt.of(StringArgumentType.getString(ctx, "text")));
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(msg, false);
        return 1;
    }

    // ---------------------------------------------------------------- base et respawn

    /** /ccis base (et /base) : retour a la base de son equipe, impossible pendant une epreuve ou un conseil. */
    private static int goBase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (GameManager.isInGame(player.getUUID())) {
            ctx.getSource().sendFailure(Txt.err(
                    "Tu ne peux pas rentrer à ta base avant la fin de l'épreuve."));
            return 0;
        }
        if (ConseilManager.phase() != ConseilManager.Phase.IDLE && ConseilManager.isCandidate(player.getUUID())) {
            ctx.getSource().sendFailure(Txt.err(
                    "Tu ne peux pas rentrer à ta base avant la fin {v_du_conseil}."));
            return 0;
        }
        String team = TeamManager.teamIdOf(player.getUUID());
        if (team == null) {
            ctx.getSource().sendFailure(Txt.err("Tu n'as pas d'équipe."));
            return 0;
        }
        SpawnManager.Spawn base = SpawnManager.get("base:" + team);
        if (base == null) {
            ctx.getSource().sendFailure(Txt.err(
                    "La base de ton équipe n'est pas définie : préviens un organisateur."));
            return 0;
        }
        if (!SpawnManager.teleport(ctx.getSource().getServer(), player, base)) {
            ctx.getSource().sendFailure(Txt.err("Dimension de la base introuvable."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Txt.ok("Retour à ta base.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int teamSpawnSet(CommandContext<CommandSourceStack> ctx, String prefix) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        SpawnManager.set(prefix + teamArg(ctx).toLowerCase(), player);
        String what = prefix.equals("base:") ? "Base" : "Point de respawn";
        String msg = what + " de l'équipe " + team.name + " défini à ta position.";
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return 1;
    }

    private static int teamSpawnTp(CommandContext<CommandSourceStack> ctx, String prefix) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        SpawnManager.Spawn spawn = SpawnManager.get(prefix + teamArg(ctx).toLowerCase());
        if (spawn == null) {
            ctx.getSource().sendFailure(Txt.err("Spawn non défini pour l'équipe " + team.name + "."));
            return 0;
        }
        if (!SpawnManager.teleport(ctx.getSource().getServer(), player, spawn)) {
            ctx.getSource().sendFailure(Txt.err("Dimension du spawn introuvable : " + spawn.dimension));
            return 0;
        }
        return 1;
    }

    /** Point d'equipe propre a un jeu (remplace le point d'equipe general pour ce jeu). */
    private static int gameZoneSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String game = StringArgumentType.getString(ctx, "game").toLowerCase();
        if (GameRegistry.get(game) == null) {
            ctx.getSource().sendFailure(Txt.err("Jeu inconnu : " + game));
            return 0;
        }
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        SpawnManager.set("zone:" + game + ":" + teamArg(ctx).toLowerCase(), player);
        String msg = "Point de l'équipe " + team.name + " pour le jeu " + game + " défini à ta position.";
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
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
        CampfireManager.Fire fire = CampfireManager.get(teamArg(ctx));
        if (fire == null) {
            ctx.getSource().sendFailure(Txt.err("Aucun feu défini pour l'équipe " + team.name + "."));
            return 0;
        }
        if (!CampfireManager.teleportTo(player, fire)) {
            ctx.getSource().sendFailure(Txt.err("Dimension du feu introuvable."));
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
            ctx.getSource().sendFailure(Txt.err(
                    "Regarde un feu de camp (bloc campfire) à moins de 8 blocs, puis relance la commande."));
            return 0;
        }
        net.minecraft.core.BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (!CampfireManager.isCampfireLike(player.level().getBlockState(pos))) {
            ctx.getSource().sendFailure(Txt.err(
                    "Ce bloc n'est pas un feu de camp. Pose un campfire et regarde-le."));
            return 0;
        }
        CampfireManager.set(teamArg(ctx), player.serverLevel(), pos);
        String msg = "Feu de camp de l'équipe " + team.name + " défini en " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".";
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return 1;
    }

    private static int campfireRemove(CommandContext<CommandSourceStack> ctx) {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        if (!CampfireManager.remove(teamArg(ctx))) {
            ctx.getSource().sendFailure(Txt.err("Aucun feu défini pour l'équipe " + team.name + "."));
            return 0;
        }
        String msg = "Feu de camp de l'équipe " + team.name + " retiré.";
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return 1;
    }

    private static int campfireFuel(CommandContext<CommandSourceStack> ctx) {
        TeamManager.KLTeam team = findTeam(ctx);
        if (team == null) {
            return 0;
        }
        String id = teamArg(ctx);
        if (CampfireManager.get(id) == null) {
            ctx.getSource().sendFailure(Txt.err("Aucun feu défini pour l'équipe " + team.name + "."));
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "value");
        CampfireManager.setFuel(id, value);
        String msg = "Jauge du feu de l'équipe " + team.name + " : " + CampfireManager.percent(CampfireManager.get(id)) + "%.";
        ctx.getSource().sendSuccess(() -> Txt.ok(msg), true);
        return 1;
    }

    private static int teamUnmerge(CommandContext<CommandSourceStack> ctx) {
        TeamManager.unmerge();
        ctx.getSource().sendSuccess(() -> Txt.ok(
                "Équipe de fusion supprimée. Ses joueurs n'ont plus d'équipe : réassigne-les avec /ccis team add."), true);
        return 1;
    }

    private static final int HISTORY_PAGE = 10;

    private static int showHistory(CommandContext<CommandSourceStack> ctx, String archive, int page) {
        SeasonHistory.Data d = archive == null ? SeasonHistory.data() : SeasonHistory.readArchive(archive);
        CommandSourceStack src = ctx.getSource();
        if (d == null) {
            src.sendFailure(Txt.err("Archive introuvable : " + archive));
            return 0;
        }
        int total = d.events.size();
        int pages = Math.max(1, (total + HISTORY_PAGE - 1) / HISTORY_PAGE);
        int p = Math.min(page, pages);
        src.sendSuccess(() -> Txt.lit("══ Historique : " + d.season + " ══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        if (total == 0) {
            src.sendSuccess(() -> Txt.lit("Rien pour le moment.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        int from = total - (p - 1) * HISTORY_PAGE; // plus recent en haut
        int to = Math.max(0, from - HISTORY_PAGE);
        for (int i = from - 1; i >= to; i--) {
            SeasonHistory.Event e = d.events.get(i);
            ChatFormatting color = switch (e.type) {
                case "elimination", "abandon", "defusion" -> ChatFormatting.RED;
                case "collier", "retour", "epreuve" -> ChatFormatting.GREEN;
                case "egalite" -> ChatFormatting.YELLOW;
                case "fusion" -> ChatFormatting.LIGHT_PURPLE;
                case "finale" -> ChatFormatting.GOLD;
                default -> ChatFormatting.WHITE;
            };
            String icon = switch (e.type) {
                case "elimination" -> "\u2620 ";
                case "abandon" -> "\u2718 ";
                case "collier" -> "\u25C6 ";
                case "retour" -> "\u2714 ";
                case "epreuve" -> "\u2605 ";
                case "egalite" -> "\u2696 ";
                case "fusion", "defusion" -> "\u2726 ";
                case "finale" -> "\u265B ";
                default -> "\u25B8 ";
            };
            MutableComponent line = Component.empty()
                    .append(Component.literal(e.date + " ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(icon).withStyle(color))
                    .append(Txt.lit(e.text).withStyle(color));
            src.sendSuccess(() -> line, false);
        }
        src.sendSuccess(() -> Txt.lit("Page " + p + "/" + pages + (pages > 1 ? "  — /ccis history <page>" : "")).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int balanceTest(CommandContext<CommandSourceStack> ctx, int count) {
        String err = fr.cosmicisland.game.BalanceRoulette.test(count);
        if (err != null) {
            ctx.getSource().sendFailure(Txt.err(err));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Txt.ok("Roulette de test lancée (" + count + " joueurs fictifs, personne n'est dispensé)."), false);
        return 1;
    }

    private static int giveSurvival(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        String what = StringArgumentType.getString(ctx, "item").toLowerCase();
        net.minecraft.world.item.Item item = switch (what) {
            case "pot", "cooking_pot" -> fr.cosmicisland.survival.SurvivalRegistry.MARMITE.get();
            case "canteen" -> fr.cosmicisland.survival.SurvivalRegistry.GOURDE.get();
            case "rice", "raw_rice_bag" -> fr.cosmicisland.survival.SurvivalRegistry.SAC_RIZ.get();
            case "shell", "rice_shell" -> fr.cosmicisland.survival.SurvivalRegistry.COQUILLAGE_RIZ.get();
            case "lighter" -> fr.cosmicisland.survival.SurvivalRegistry.ALLUME_FEU.get();
            case "campfire", "team_campfire" -> fr.cosmicisland.survival.SurvivalRegistry.FEU_ITEM.get();
            default -> null;
        };
        if (item == null) {
            ctx.getSource().sendFailure(Txt.err("Objet inconnu : " + what + " (pot, canteen, rice, shell, lighter, campfire)."));
            return 0;
        }
        int n = 0;
        for (ServerPlayer p : net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "players")) {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, item.getMaxStackSize(new net.minecraft.world.item.ItemStack(item)) == 1 ? 1 : count);
            if (item instanceof fr.cosmicisland.survival.GourdeItem) {
                fr.cosmicisland.survival.GourdeItem.setSips(stack, fr.cosmicisland.survival.GourdeItem.maxSips(), false);
            }
            int times = stack.getCount() == count ? 1 : count;
            for (int i = 0; i < times; i++) {
                net.minecraft.world.item.ItemStack copy = stack.copy();
                if (!p.getInventory().add(copy)) {
                    p.drop(copy, false);
                }
            }
            n++;
        }
        int done = n;
        ctx.getSource().sendSuccess(() -> Txt.ok("Objet donné à " + done + " joueur(s)."), true);
        return n;
    }
}
