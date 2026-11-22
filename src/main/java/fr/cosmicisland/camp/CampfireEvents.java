package fr.cosmicisland.camp;

import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.conseil.ConseilMessages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;

/**
 * Alimentation du feu (clic droit avec du bois sur un ancien feu), pose du feu de camp d'equipe,
 * interdiction des feux de camp classiques et message a la fabrication.
 */
public class CampfireEvents {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (CampfireManager.tryFeed(player, level, event.getPos(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        boolean staff = KLPermissions.levelOf(player) >= KLPermissions.ORGANISATEUR;
        // Feux de camp vanilla / d'autres mods : interdits pour les joueurs
        if (CampfireManager.isForeignCampfire(placed) && CampfireManager.config().enabled
                && CampfireManager.config().disableOtherCampfires && !staff) {
            event.setCanceled(true);
            ConseilMessages.send(CampfireManager.config().message("otherDisabled"), player, Map.of());
            return;
        }
        // Feu de camp d'equipe : devient le feu unique de l'equipe (refuse si elle en a deja un)
        if (CampfireManager.isTeamFire(placed)) {
            if (!CampfireManager.onTeamFirePlaced(player, level, event.getPos())) {
                event.setCanceled(true);
            }
            return;
        }
        CampfireManager.onPlaced(player, level, event.getPos(), placed);
    }

    @SubscribeEvent
    public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getCrafting().getItem() instanceof BlockItem bi
                && CampfireManager.isTeamFire(bi.getBlock().defaultBlockState())) {
            CampfireManager.onCrafted(player);
        }
    }
}
