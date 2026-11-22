package fr.cosmicisland.camp;

import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Acces de l'interface aux 8 cases reelles du feu de camp (marmite, ingredient, gourde, sortie, 4 cases de grill). */
public final class FireContainer implements Container {
    private final TeamCampfireBlockEntity be;

    public FireContainer(TeamCampfireBlockEntity be) {
        this.be = be;
    }

    @Override
    public int getContainerSize() {
        return TeamCampfireBlockEntity.SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : be.items()) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return be.items().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(be.items(), slot, amount);
        if (!result.isEmpty()) {
            be.setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(be.items(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        be.items().set(slot, stack);
        be.setChanged();
    }

    @Override
    public void setChanged() {
        be.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(be, player);
    }

    @Override
    public void clearContent() {
        be.items().clear();
    }
}
