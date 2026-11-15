package fr.cosmicisland;

import com.mojang.logging.LogUtils;
import fr.cosmicisland.camp.CampfireEvents;
import fr.cosmicisland.camp.CampfireManager;
import fr.cosmicisland.chat.ChatEvents;
import fr.cosmicisland.conseil.ConseilManager;
import fr.cosmicisland.conseil.ConseilRegistry;
import fr.cosmicisland.game.GameEvents;
import fr.cosmicisland.game.GameManager;
import fr.cosmicisland.game.GameRegistry;
import fr.cosmicisland.game.InventoryStore;
import fr.cosmicisland.game.TestGame;
import fr.cosmicisland.games.poteaux.PoteauxGame;
import fr.cosmicisland.games.poteaux.PoteauxRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CosmicIsland.MODID)
public class CosmicIsland {
    public static final String MODID = "cosmicisland";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CosmicIsland() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new GameEvents());
        MinecraftForge.EVENT_BUS.register(new ChatEvents());
        MinecraftForge.EVENT_BUS.register(new CampfireEvents());
        // Blocs et reseau des poteaux (bus d'evenements du mod)
        PoteauxRegistry.init(FMLJavaModLoadingContext.get().getModEventBus());
        ConseilRegistry.init(FMLJavaModLoadingContext.get().getModEventBus());
        // Jeux integres (les addons appellent GameRegistry.register de la meme facon)
        GameRegistry.register(new TestGame());
        GameRegistry.register(PoteauxGame.INSTANCE);
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
        GameManager.init(event.getServer());
        ConseilManager.init(event.getServer());
        CampfireManager.init(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        GameManager.shutdown();
        ConseilManager.shutdown();
        CampfireManager.shutdown();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Recuperation apres crash : inventaire sauvegarde mais le joueur n'est plus en epreuve
            if (InventoryStore.has(player.getUUID()) && !GameManager.isInGame(player.getUUID())) {
                InventoryStore.restore(player.getServer(), player);
            }
            RoleManager.onJoin(player);
            Vocab.sendTo(player);
            GameManager.onLogin(player);
            fr.cosmicisland.conseil.ConseilManager.onLogin(player);
            fr.cosmicisland.conseil.TotemManager.onLogin(player);
        }
    }
}
