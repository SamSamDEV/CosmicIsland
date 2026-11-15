package fr.cosmicisland.camp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Alimentation du feu (clic droit avec du bois) + detection de la pose / fabrication d'un feu de camp. */
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
        if (event.getEntity() instanceof ServerPlayer player) {
            CampfireManager.onPlaced(player, level, event.getPos(), event.getPlacedBlock());
        }
    }

    @SubscribeEvent
    public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getCrafting().getItem() instanceof BlockItem bi
                && CampfireManager.isCampfireLike(bi.getBlock().defaultBlockState())) {
            CampfireManager.onCrafted(player);
        }
    }
}
