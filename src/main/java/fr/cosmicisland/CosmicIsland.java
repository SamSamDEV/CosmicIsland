package fr.cosmicisland;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CosmicIsland.MODID)
public class CosmicIsland {
    public static final String MODID = "cosmicisland";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CosmicIsland() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        KLCommand.register(event.getDispatcher());
    }

    // Chargement des fichiers JSON (admins, roles, equipes)
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        KLFiles.loadAll();
    }

    // Le scoreboard n'existe qu'une fois le monde charge
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        TeamManager.sync(event.getServer());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RoleManager.onJoin(player);
        }
    }
}
