package fr.cosmicisland.conseil;

import fr.cosmicisland.Txt;
import fr.cosmicisland.game.GameConfig;
import fr.cosmicisland.game.GameMessages;
import fr.cosmicisland.game.Placeholders;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Envoi d'un message de la config du conseil a un joueur. */
public final class ConseilMessages {

    private ConseilMessages() {}

    private static String fill(String text, ServerPlayer player, Map<String, String> vars) {
        String result = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return Placeholders.apply(result, player);
    }

    public static void send(GameConfig.Msg msg, ServerPlayer player, Map<String, String> vars) {
        if (msg == null || msg.text == null || msg.text.isEmpty()) {
            return;
        }
        Component text = Txt.of(fill(msg.text, player, vars));
        String channels = msg.channel == null ? "chat" : msg.channel;
        for (String channel : channels.split("\\+")) {
            switch (channel.trim().toLowerCase()) {
                case "chat" -> player.sendSystemMessage(text);
                case "actionbar" -> player.displayClientMessage(text, true);
                case "title" -> {
                    Component sub = (msg.subtitle == null || msg.subtitle.isEmpty())
                            ? Component.empty()
                            : Txt.of(fill(msg.subtitle, player, vars));
                    GameMessages.showTitle(player, text, sub, msg.fadeIn, msg.stay, msg.fadeOut);
                }
                default -> { }
            }
        }
        if (msg.sound != null && !msg.sound.isEmpty()) {
            GameMessages.playSound(player, msg.sound, 1.0f);
        }
    }
}
