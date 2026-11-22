package fr.cosmicisland.survival;

import fr.cosmicisland.CosmicIsland;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Evenements serveur de la soif. */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID)
public final class ThirstEvents {
    private ThirstEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer p) {
            ThirstManager.tick(p);
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ThirstManager.addExhaustion(p, p.isSprinting()
                    ? ThirstManager.config().sprintJumpAmount : ThirstManager.config().jumpAmount);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p) {
            ThirstManager.addExhaustion(p, ThirstManager.config().miningAmount);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ThirstManager.addExhaustion(p, ThirstManager.config().attackAmount);
        }
    }

    @SubscribeEvent
    public static void onFinishUse(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ThirstManager.onConsumed(p, event.getItem());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ThirstManager.config().resetOnRespawn && !event.isEndConquered()) {
            ThirstManager.resetFull(p);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ThirstManager.sync(p, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ThirstManager.forget(event.getEntity().getUUID());
    }
}
