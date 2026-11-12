package fr.cosmicisland.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Applique les flags de la config du jeu aux joueurs qui participent a l'epreuve.
 * Un flag a false = action bloquee. Les joueurs hors epreuve ne sont jamais affectes.
 */
public class GameEvents {

    private static GameConfig.Flags flags(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return GameManager.flagsFor(serverPlayer);
        }
        return null;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            GameManager.tick();
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        GameConfig.Flags f = flags(event.getPlayer());
        if (f != null && !f.breakBlocks) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            GameConfig.Flags f = flags(player);
            if (f != null && !f.placeBlocks) {
                event.setCanceled(true);
            }
        }
    }

    /** PvP et degats : le PvP est regi par "pvp", tout le reste par "damage" (le vide passe toujours). */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        GameConfig.Flags f = flags(victim);
        if (f == null) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player) {
            if (!f.pvp) {
                event.setCanceled(true);
            }
            return;
        }
        if (!f.damage) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onToss(ItemTossEvent event) {
        GameConfig.Flags f = flags(event.getPlayer());
        if (f != null && !f.dropItems) {
            event.setCanceled(true);
            // On rend l'objet au joueur pour ne rien perdre
            event.getPlayer().getInventory().add(event.getEntity().getItem());
        }
    }

    /** Interactions : bloque l'utilisation des blocs (coffres, portes, boutons...) mais pas l'objet en main. */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        GameConfig.Flags f = flags(event.getEntity());
        if (f != null && !f.interact) {
            event.setUseBlock(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        GameConfig.Flags f = flags(event.getEntity());
        if (f != null && !f.interact) {
            event.setCanceled(true);
        }
    }

    /** Mobs : si "mobs" est a false, les monstres ne peuvent pas cibler les participants. */
    @SubscribeEvent
    public void onMobTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player) {
            GameConfig.Flags f = flags(player);
            if (f != null && !f.mobs) {
                event.setNewTarget(null);
            }
        }
    }
}
