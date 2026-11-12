package fr.cosmicisland.game;

import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import net.minecraft.network.chat.Component;
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
        return text
                .replace("{jeu}", cfg.displayName)
                .replace("{joueur}", player == null ? "" : player.getGameProfile().getName())
                .replace("{equipe}", team);
    }

    /** Envoie le message "key" de la config a un joueur. Ne fait rien si le message est vide ou absent. */
    public static void send(GameConfig cfg, String key, ServerPlayer player) {
        GameConfig.Msg msg = cfg.message(key);
        if (msg == null || msg.text == null || msg.text.isEmpty()) {
            return;
        }
        Component text = Txt.of(apply(msg.text, player, cfg));
        String channels = msg.channel == null ? "chat" : msg.channel;
        for (String channel : channels.split("\\+")) {
            switch (channel.trim().toLowerCase()) {
                case "chat" -> player.sendSystemMessage(text);
                case "actionbar" -> player.displayClientMessage(text, true);
                case "title" -> {
                    Component sub = (msg.subtitle == null || msg.subtitle.isEmpty())
                            ? Component.empty()
                            : Txt.of(apply(msg.subtitle, player, cfg));
                    showTitle(player, text, sub, 10, 50, 10);
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

    public static void playSound(ServerPlayer player, String soundId, float pitch) {
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        SoundEvent event = id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (event != null) {
            player.playNotifySound(event, SoundSource.MASTER, 1.0f, pitch);
        }
    }
}
