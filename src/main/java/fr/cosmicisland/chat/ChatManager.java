package fr.cosmicisland.chat;

import fr.cosmicisland.KLFiles;

public final class ChatManager {
    private static ChatConfig config = new ChatConfig();

    private ChatManager() {}

    public static void load() {
        config = KLFiles.load("chat.json", ChatConfig.class, ChatConfig::new);
    }

    public static ChatConfig config() {
        return config;
    }
}
