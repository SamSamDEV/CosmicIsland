package fr.cosmicisland;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Garde le TAB (equipes, organisateurs) et les pseudos masques a jour : connexion, deconnexion, changement de role. */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID)
public final class TabEvents {
    private TabEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            TeamManager.sync(p.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            TeamManager.sync(p.getServer());
        }
    }

    /** Toutes les 5 secondes : prend en compte un changement de role fait entre-temps. */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 100 == 0) {
            TeamManager.sync(event.getServer());
        }
    }
}
