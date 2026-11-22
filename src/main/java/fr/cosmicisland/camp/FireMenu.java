package fr.cosmicisland.camp;

import fr.cosmicisland.survival.GourdeItem;
import fr.cosmicisland.survival.MarmiteItem;
import fr.cosmicisland.survival.SurvivalRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.function.Predicate;

/**
 * Interface du feu de camp (ecran dedie : marmite, ingredient, gourde, sortie, 4 cases de grill).
 * Le fond est le PNG textures/gui/fire.png : il suffit de le remplacer pour changer le design.
 */
public class FireMenu extends AbstractContainerMenu {
    // positions des cases (coin haut-gauche de l'objet) : doivent correspondre au PNG
    public static final int[][] POS = {
            {36, 53},   // 0 marmite
            {-9999, -9999},   // 1 (ancienne case riz : supprimee, le riz se verse depuis le sac)
            {124, 53},  // 2 gourde
            {124, 29},  // 3 sortie
            {71, 35}, {89, 35}, {71, 53}, {89, 53},   // 4-7 grill (centre sur 88)
            {36, 29}    // 8 bois
    };
    // indices des donnees synchronisees
    public static final int SLOT_GRILL_FIRST = 4;
    public static final int D_LIT = 0;
    public static final int D_PCT = 1;
    public static final int D_COOK = 2;       // % de cuisson du riz, -1 = aucune
    public static final int D_FLASK = 3;      // -1 aucune, 0 vide, 1 sale, 2 purifiee
    public static final int D_BOIL = 4;       // % de purification
    public static final int D_BLOCKED = 5;
    public static final int D_GRILL = 6;      // 6..9 : % de cuisson de chaque case de grill, -1 = rien
    public static final int DATA_COUNT = 10;

    private final Container container;
    private final ContainerData data;
    private final Level level;

    /** Serveur. */
    public FireMenu(int id, Inventory inventory, TeamCampfireBlockEntity fire) {
        this(id, inventory, new FireContainer(fire), new FireData(fire));
    }

    /** Client (fabrique du type de menu : les donnees arrivent par synchronisation). */
    public FireMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(TeamCampfireBlockEntity.SIZE), new SimpleContainerData(DATA_COUNT));
    }

    private FireMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(SurvivalRegistry.FIRE_MENU.get(), id);
        this.container = container;
        this.data = data;
        this.level = inventory.player.level();
        for (int i = 0; i < TeamCampfireBlockEntity.SIZE; i++) {
            addSlot(slotFor(i));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 162));
        }
        addDataSlots(data);
    }

    public int data(int index) {
        return data.get(index);
    }

    private boolean cookable(ItemStack stack) {
        return !stack.isEmpty() && level.getRecipeManager()
                .getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SimpleContainer(stack), level).isPresent();
    }

    private Slot slotFor(int i) {
        int x = POS[i][0];
        int y = POS[i][1];
        return switch (i) {
            case TeamCampfireBlockEntity.SLOT_POT -> new FireSlot(container, i, x, y, s -> s.getItem() instanceof MarmiteItem, true, 1);
            case TeamCampfireBlockEntity.SLOT_FUEL -> new Slot(container, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean isActive() {
                    return false;
                }
            };
            case TeamCampfireBlockEntity.SLOT_WOOD -> new FireSlot(container, i, x, y,
                    s -> CampfireManager.fuelPoints(s) != null, true, 64);
            case TeamCampfireBlockEntity.SLOT_FLASK -> new FireSlot(container, i, x, y, s -> s.getItem() instanceof GourdeItem, true, 1);
            case TeamCampfireBlockEntity.SLOT_OUT -> new FireSlot(container, i, x, y, null, true, 64);
            default -> new FireSlot(container, i, x, y, this::cookable, true, 1);
        };
    }

    /** Case de l'interface : acceptation, retrait et taille de pile controles. */
    private static final class FireSlot extends Slot {
        private final Predicate<ItemStack> accept;
        private final boolean takeable;
        private final int max;

        FireSlot(Container container, int index, int x, int y, Predicate<ItemStack> accept, boolean takeable, int max) {
            super(container, index, x, y);
            this.accept = accept;
            this.takeable = takeable;
            this.max = max;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return accept != null && accept.test(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return takeable;
        }

        @Override
        public int getMaxStackSize() {
            return max;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int size = TeamCampfireBlockEntity.SIZE;
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int target = 0; target < size && !moved; target++) {
                if (target == TeamCampfireBlockEntity.SLOT_OUT) {
                    continue;
                }
                if (slots.get(target).mayPlace(stack) && moveItemStackTo(stack, target, target + 1, false)) {
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /** Donnees synchronisees du feu (lues sur le bloc cote serveur). */
    private static final class FireData implements ContainerData {
        private final TeamCampfireBlockEntity be;

        FireData(TeamCampfireBlockEntity be) {
            this.be = be;
        }

        @Override
        public int get(int i) {
            return be.menuData(i);
        }

        @Override
        public void set(int i, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
