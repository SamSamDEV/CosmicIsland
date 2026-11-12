package fr.cosmicisland;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public final class KLCommand {
    private static final SuggestionProvider<CommandSourceStack> TEAM_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(TeamManager.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> ROLES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(KLRole.values()).map(KLRole::id), builder);

    private KLCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kl")
                .then(Commands.literal("info")
                        .executes(KLCommand::info))
                .then(Commands.literal("admin")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ORGANISATEUR))
                        .executes(KLCommand::admin))
                .then(Commands.literal("reload")
                        .requires(s -> KLPermissions.has(s, KLPermissions.ADMIN))
                        .executes(KLCommand::reload))
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
                                .executes(KLCommand::teamMerge))));
    }

    // ---------------------------------------------------------------- general

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int level = KLPermissions.levelOf(player);
        String staff = level == KLPermissions.ADMIN ? "admin"
                : level == KLPermissions.ORGANISATEUR ? "organisateur" : "non";
        String role = RoleManager.effective(player).id();
        String teamId = TeamManager.teamIdOf(player.getUUID());
        String team = teamId == null ? "aucune" : TeamManager.get(teamId).name;
        ctx.getSource().sendSuccess(
                () -> Component.literal("Staff : " + staff + " | Rôle : " + role + " | Équipe : " + team), false);
        return 1;
    }

    private static int admin(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("Menu admin : arrive à la phase 3 (interface). Ton accès staff est bien validé."),
                false);
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
                    .append(Txt.of(t.tag).withStyle(t.format()))
                    .append(Component.literal(" " + t.name + " (" + e.getKey() + ") - "
                            + t.members.size() + " joueur(s) : " + String.join(", ", t.members.values())));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return TeamManager.all().size();
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
}
