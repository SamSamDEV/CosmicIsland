package fr.cosmicisland.game;

import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

/** Envoi des messages d'un jeu : placeholders {jeu} {joueur} {equipe}, canaux chat/title/actionbar. */
public final class GameMessages {

    private GameMessages() {}

    public static String apply(String text, ServerPlayer player, GameConfig cfg) {
        String team = "";
        if (player != null) {
            String teamId = TeamManager.teamIdOf(player.getUUID());
            if (teamId != null) {
                team = TeamManager.get(teamId).name;
            }
        }
        String base = text
                .replace("{jeu}", cfg.displayName)
                .replace("{joueur}", player == null ? "" : player.getGameProfile().getName())
                .replace("{equipe}", team);
        return Placeholders.apply(base, player);
    }

    /** Envoie le message "key" de la config a un joueur. Ne fait rien si le message est vide ou absent. */
    public static void send(GameConfig cfg, String key, ServerPlayer player) {
        sendInternal(cfg, key, player, player, -1, -1, -1);
    }

    /** Envoie le message a "player" en remplacant {joueur} et {equipe} par ceux de "subject". */
    public static void send(GameConfig cfg, String key, ServerPlayer player, ServerPlayer subject) {
        sendInternal(cfg, key, player, subject, -1, -1, -1);
    }

    /** Comme send, mais impose la duree du titre (en ticks) au lieu de celle du message. */
    public static void sendWithTitleTimes(GameConfig cfg, String key, ServerPlayer player,
                                          int fadeIn, int stay, int fadeOut) {
        sendInternal(cfg, key, player, player, fadeIn, stay, fadeOut);
    }

    private static void sendInternal(GameConfig cfg, String key, ServerPlayer player, ServerPlayer subject,
                                     int fadeIn, int stay, int fadeOut) {
        GameConfig.Msg msg = cfg.message(key);
        if (msg == null || msg.text == null || msg.text.isEmpty()) {
            return;
        }
        Component text = Txt.of(apply(msg.text, subject, cfg));
        String channels = msg.channel == null ? "chat" : msg.channel;
        for (String channel : channels.split("\\+")) {
            switch (channel.trim().toLowerCase()) {
                case "chat" -> player.sendSystemMessage(text);
                case "actionbar" -> player.displayClientMessage(text, true);
                case "title" -> {
                    Component sub = (msg.subtitle == null || msg.subtitle.isEmpty())
                            ? Component.empty()
                            : Txt.of(apply(msg.subtitle, subject, cfg));
                    showTitle(player, text, sub,
                            fadeIn >= 0 ? fadeIn : msg.fadeIn,
                            stay >= 0 ? stay : msg.stay,
                            fadeOut >= 0 ? fadeOut : msg.fadeOut);
                }
                default -> { }
            }
        }
        if (msg.sound != null && !msg.sound.isEmpty()) {
            playSound(player, msg.sound, 1.0f);
        }
    }

    public static void showTitle(ServerPlayer player, Component title, Component subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** Efface immediatement le titre affiche a l'ecran du joueur. */
    public static void clearTitle(ServerPlayer player) {
        player.connection.send(new ClientboundClearTitlesPacket(true));
    }

    public static void playSound(ServerPlayer player, String soundId, float pitch) {
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        SoundEvent event = id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (event != null) {
            player.playNotifySound(event, SoundSource.MASTER, 1.0f, pitch);
        }
    }
}
