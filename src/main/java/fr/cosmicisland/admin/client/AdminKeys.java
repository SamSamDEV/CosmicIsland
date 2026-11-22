package fr.cosmicisland.admin.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.cosmicisland.CosmicIsland;
import fr.cosmicisland.admin.AdminNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Touche d'ouverture de la fenetre d'administration (K par defaut, modifiable dans les options) + retour des commandes. */
public final class AdminKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.cosmicisland.admin", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.KEY_K, "key.categories.cosmicisland");

    private AdminKeys() {}

    @Mod.EventBusSubscriber(modid = CosmicIsland.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    @Mod.EventBusSubscriber(modid = CosmicIsland.MODID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    AdminNetwork.CHANNEL.sendToServer(new AdminNetwork.RequestPacket(true));
                }
            }
        }

        /** Les reponses des commandes s'affichent aussi en bas de la fenetre. */
        @SubscribeEvent
        public static void onChat(ClientChatReceivedEvent event) {
            if (Minecraft.getInstance().screen instanceof AdminScreen) {
                AdminClient.setFeedback(event.getMessage());
            }
        }
    }
}
