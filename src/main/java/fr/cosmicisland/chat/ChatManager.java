package fr.cosmicisland.chat;

import fr.cosmicisland.KLFiles;

public final class ChatManager {
    private static ChatConfig config = new ChatConfig();

    private ChatManager() {}

    public static void load() {
        config = KLFiles.load("chat.json", ChatConfig.class, ChatConfig::new);
        for (String c : new ChatConfig().blockedCommands) { // les commandes de messages prives restent toujours bloquees
            if (!config.blockedCommands.contains(c)) {
                config.blockedCommands.add(c);
            }
        }
    }

    /** Chat global ON = tout le monde se lit ; OFF = chat par equipe. */
    public static void setGlobal(boolean on) {
        config.teamChat = !on;
        KLFiles.save("chat.json", config);
    }

    /** Message d'un joueur a l'organisation (seul message prive autorise). */
    public static int toStaff(net.minecraft.server.level.ServerPlayer player, String message) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        String text = config.orgaFormat.replace("{joueur}", player.getGameProfile().getName()).replace("{message}", message);
        net.minecraft.network.chat.MutableComponent line = fr.cosmicisland.Txt.of(text).copy().withStyle(net.minecraft.network.chat.Style.EMPTY
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                        "/ccis reply " + player.getGameProfile().getName() + " "))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        fr.cosmicisland.Txt.lit("Clique pour répondre"))));
        int n = 0;
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (fr.cosmicisland.KLPermissions.levelOf(p) >= fr.cosmicisland.KLPermissions.ORGANISATEUR) {
                p.sendSystemMessage(line);
                n++;
            }
        }
        server.sendSystemMessage(line);
        if (n == 0) {
            player.sendSystemMessage(fr.cosmicisland.Txt.of("&c\u2718 Aucun organisateur n'est connecté pour le moment."));
            return 0;
        }
        player.sendSystemMessage(fr.cosmicisland.Txt.of("&a\u2714 &7Message envoyé à l'organisation."));
        return 1;
    }

    /** Reponse de l'organisation a un joueur. */
    public static boolean reply(net.minecraft.server.level.ServerPlayer target, String author, String message) {
        target.sendSystemMessage(fr.cosmicisland.Txt.of(config.orgaReplyFormat.replace("{auteur}", author).replace("{message}", message)));
        return true;
    }

    public static ChatConfig config() {
        return config;
    }
}
