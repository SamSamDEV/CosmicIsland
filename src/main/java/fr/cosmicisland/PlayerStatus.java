package fr.cosmicisland;

import com.mojang.authlib.GameProfile;
import fr.cosmicisland.conseil.ConseilManager;
import fr.cosmicisland.game.SpawnManager;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;

/** Abandon volontaire d'un joueur et retour d'un elimine dans l'aventure. */
public final class PlayerStatus {
    private PlayerStatus() {}

    /** Demande de confirmation cliquable (on n'abandonne pas par erreur). */
    public static String askAbandon(ServerPlayer player) {
        String error = canAbandon(player);
        if (error != null) {
            return error;
        }
        player.sendSystemMessage(Component.empty()
                .append(Txt.of("&6[Aventure] &7Veux-tu vraiment &cabandonner&7 ? Tu seras spectateur. "))
                .append(Txt.lit("[OUI, J'ABANDONNE]").withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ccis abandon confirm"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Txt.of("&7Quitter définitivement l'aventure"))))));
        return null;
    }

    private static String canAbandon(ServerPlayer player) {
        if (RoleManager.effective(player) != KLRole.PARTICIPANT) {
            return "Tu ne participes pas à l'aventure.";
        }
        if (ConseilManager.phase() != ConseilManager.Phase.IDLE && ConseilManager.isCandidate(player.getUUID())) {
            return "Tu ne peux pas abandonner pendant {v_le_conseil} : demande à un organisateur.";
        }
        return null;
    }

    /** Abandon confirme : le joueur passe spectateur et rejoint le point d'elimination. */
    public static String abandon(ServerPlayer player) {
        String error = canAbandon(player);
        if (error != null) {
            return error;
        }
        GameProfile profile = player.getGameProfile();
        RoleManager.set(profile, KLRole.SPECTATEUR);
        RoleManager.apply(player, KLRole.SPECTATEUR);
        SpawnManager.Spawn out = SpawnManager.get("elimination");
        MinecraftServer server = player.getServer();
        if (server != null) {
            if (out != null) {
                SpawnManager.teleport(server, player, out);
            }
            server.getPlayerList().broadcastSystemMessage(
                    Txt.of("&6[Aventure] &f" + profile.getName() + " &7a abandonné l'aventure."), false);
        }
        return null;
    }

    /**
     * Etape 1 du retour : propose en cliquant l'equipe dans laquelle remettre le joueur.
     * Rien n'est modifie tant que l'organisateur n'a pas choisi.
     */
    public static void askTeam(net.minecraft.commands.CommandSourceStack source, GameProfile profile) {
        net.minecraft.network.chat.MutableComponent line = Component.empty()
                .append(Txt.of("&6[Aventure] &7Dans quelle équipe remettre &f" + profile.getName() + " &7? "));
        for (java.util.Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            TeamManager.KLTeam team = e.getValue();
            line.append(Txt.of(team.name).copy().withStyle(Style.EMPTY
                    .withColor(team.format()).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/ccis revive " + profile.getName() + " " + e.getKey()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Txt.of("&7Remettre " + profile.getName() + " dans l'équipe " + team.name)))));
            line.append(Txt.lit("  "));
        }
        source.sendSystemMessage(line);
    }

    /**
     * Etape 2 : le joueur quitte son equipe actuelle, rejoint celle choisie, redevient participant
     * et est renvoye a la base de cette equipe. Retourne une erreur ou null.
     */
    public static String revive(MinecraftServer server, GameProfile profile, String teamIdOrName) {
        String teamId = TeamManager.resolveId(teamIdOrName);
        if (teamId == null) {
            return "Équipe inconnue : " + teamIdOrName;
        }
        TeamManager.assign(teamId, profile); // retire d'abord le joueur de son ancienne equipe
        RoleManager.set(profile, KLRole.PARTICIPANT);
        ServerPlayer online = server.getPlayerList().getPlayer(profile.getId());
        TeamManager.KLTeam team = TeamManager.get(teamId);
        if (online == null) {
            return null; // il sera remis en jeu a sa connexion (role participant + equipe)
        }
        RoleManager.apply(online, KLRole.PARTICIPANT);
        SpawnManager.Spawn base = SpawnManager.baseSpawn(teamId);
        if (base != null) {
            SpawnManager.teleport(server, online, base);
        }
        online.sendSystemMessage(Txt.of("&6[Aventure] &aTu reviens dans l'aventure, équipe " + team.name + " !"));
        server.getPlayerList().broadcastSystemMessage(
                Txt.of("&6[Aventure] &f" + profile.getName() + " &7est de retour dans l'aventure (équipe " + team.name + ")."), false);
        return null;
    }
}
