package fr.cosmicisland.chat;

import com.mojang.brigadier.ParseResults;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.KLRole;
import fr.cosmicisland.RoleManager;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Chat par equipe : chaque equipe a son propre chat. Un message n'est lu que par :
 * - l'equipe de l'expediteur,
 * - le staff (organisateurs et admins) et les spectateurs (reglable dans chat.json),
 * - tout le monde si l'expediteur est du staff sans equipe.
 * Les messages d'un spectateur ne vont qu'aux spectateurs et au staff.
 */
public class ChatEvents {

    private static String channelOf(ServerPlayer player) {
        if (RoleManager.effective(player) == KLRole.SPECTATEUR && ChatManager.config().spectatorChat) {
            return "spectateurs";
        }
        if (ChatManager.config().globalDuringConseil
                && fr.cosmicisland.conseil.ConseilManager.phase() != fr.cosmicisland.conseil.ConseilManager.Phase.IDLE) {
            return "global";
        }
        String team = TeamManager.teamIdOf(player.getUUID());
        if (team != null) {
            return "equipe:" + team;
        }
        return "global";
    }

    private static boolean canRead(ServerPlayer reader, ServerPlayer sender, String channel) {
        ChatConfig cfg = ChatManager.config();
        if (reader == sender) {
            return true;
        }
        if (cfg.staffReadsAll && KLPermissions.levelOf(reader) >= KLPermissions.ORGANISATEUR) {
            return true;
        }
        if (cfg.spectatorsReadAll && RoleManager.effective(reader) == KLRole.SPECTATEUR) {
            return true;
        }
        if (channel.equals("global")) {
            return true;
        }
        return channelOf(reader).equals(channel);
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (!ChatManager.config().teamChat) {
            return;
        }
        ServerPlayer sender = event.getPlayer();
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        event.setCanceled(true);

        String channel = channelOf(sender);
        Component line = Component.translatable("chat.type.text", sender.getDisplayName(), event.getMessage());
        if (channel.equals("spectateurs")) {
            line = Component.empty().append(Txt.of(ChatManager.config().spectatorPrefix)).append(line);
        }
        for (ServerPlayer reader : server.getPlayerList().getPlayers()) {
            if (canRead(reader, sender, channel)) {
                reader.sendSystemMessage(line);
            }
        }
        server.sendSystemMessage(line); // la console voit tout
    }

    /** Interdit certaines commandes vanilla (messages prives...) aux joueurs qui ne sont pas du staff. */
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        if (!(parse.getContext().getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (KLPermissions.levelOf(player) >= KLPermissions.ORGANISATEUR) {
            return;
        }
        String input = parse.getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        String name = input.split("\\s+", 2)[0].toLowerCase();
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        if (ChatManager.config().blockedCommands.contains(name)) {
            event.setCanceled(true);
            player.sendSystemMessage(Txt.of(ChatManager.config().blockedMessage));
        }
    }
}
