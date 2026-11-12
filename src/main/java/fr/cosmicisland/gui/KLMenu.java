package fr.cosmicisland.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

/**
 * Menu en forme de coffre, 100 % cote serveur (les joueurs n'ont rien a installer de plus).
 * Chaque clic dans le menu est transmis au gestionnaire (joueur, numero de case, bouton : 0 gauche, 1 droit) ;
 * aucun objet ne peut etre deplace.
 */
public final class KLMenu extends ChestMenu {

    /** Gestionnaire de clic : bouton 0 = clic gauche, 1 = clic droit. */
    @FunctionalInterface
    public interface Click {
        void on(ServerPlayer player, int slot, int button);
    }

    private final Click onClick;
    private final String tag;

    private KLMenu(MenuType<?> type, int id, Inventory inventory, Container container, int rows,
                   String tag, Click onClick) {
        super(type, id, inventory, container, rows);
        this.tag = tag;
        this.onClick = onClick;
    }

    /** Identifiant du menu (permet de le retrouver pour le rafraichir). */
    public String getTag() {
        return tag;
    }

    /** Ouvre un menu dont le gestionnaire ne regarde que la case cliquee. */
    public static void open(ServerPlayer player, Component title, int rows, SimpleContainer container,
                            String tag, BiConsumer<ServerPlayer, Integer> onClick) {
        openWith(player, title, rows, container, tag, (p, slot, button) -> onClick.accept(p, slot));
    }

    /** Ouvre un menu dont le gestionnaire connait aussi le bouton de souris utilise. */
    public static void openWith(ServerPlayer player, Component title, int rows, SimpleContainer container,
                                String tag, Click onClick) {
        int r = Math.max(1, Math.min(6, rows));
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new KLMenu(typeFor(r), id, inventory, container, r, tag, onClick), title));
    }

    private static MenuType<?> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (slotId >= 0 && slotId < getContainer().getContainerSize()) {
                onClick.on(serverPlayer, slotId, button);
            }
            // Le clic n'est jamais transmis a la logique vanilla : on resynchronise simplement l'affichage
            if (serverPlayer.containerMenu == this) {
                sendAllDataToRemote();
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
