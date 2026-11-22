package fr.cosmicisland.conseil;

import fr.cosmicisland.Txt;
import fr.cosmicisland.gui.KLMenu;
import fr.cosmicisland.gui.MenuItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu de l'organisation pour piloter le conseil :
 * tetes des joueurs (votes recus, immunite : clic pour activer ou retirer) et boutons
 * Clore le vote, Lancer le reveal, Nouveau vote, Valider et clore, Annuler.
 */
public final class ConseilAdminMenu {

    private ConseilAdminMenu() {}

    /** Ouvre le menu. Retourne un message d'erreur ou null. */
    public static String open(ServerPlayer player) {
        if (ConseilManager.phase() == ConseilManager.Phase.IDLE) {
            return "{v_conseil} : rien en cours (lance-en un avec /ccis council start <équipe>).";
        }
        int count = ConseilManager.candidates().size();
        int rows = Math.max(2, Math.min(6, (count + 8) / 9 + 1));
        SimpleContainer container = new SimpleContainer(rows * 9);
        fill(container);
        List<UUID> order = new ArrayList<>(ConseilManager.candidates().keySet());
        KLMenu.open(player, Txt.of("&6&l{v_conseil} &8- &7Organisation"), rows, container, "conseil-admin",
                (p, slot) -> onClick(p, container, rows, order, slot));
        return null;
    }

    private static void onClick(ServerPlayer player, SimpleContainer container, int rows, List<UUID> order, int slot) {
        int base = (rows - 1) * 9;
        String error = null;
        if (slot < base) {
            if (slot < order.size()) {
                error = ConseilManager.toggleImmune(order.get(slot));
            }
        } else if (slot == base + 2) {
            error = ConseilManager.closeVote();
        } else if (slot == base + 3) {
            error = ConseilManager.reveal();
        } else if (slot == base + 4) {
            error = ConseilManager.revote();
        } else if (slot == base + 6) {
            error = ConseilManager.close();
        } else if (slot == base + 8) {
            error = ConseilManager.cancel();
        }
        if (error != null) {
            player.sendSystemMessage(Txt.of("&c" + error));
        }
        if (ConseilManager.phase() == ConseilManager.Phase.IDLE) {
            player.closeContainer();
        } else {
            fill(container);
        }
    }

    /** (Re)dessine le contenu du menu a partir de l'etat actuel du conseil. */
    public static void fill(Container container) {
        int size = container.getContainerSize();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        if (ConseilManager.phase() == ConseilManager.Phase.IDLE) {
            return;
        }

        List<Map.Entry<UUID, String>> list = new ArrayList<>(ConseilManager.candidates().entrySet());
        for (int i = 0; i < list.size() && i < (rows - 1) * 9; i++) {
            UUID id = list.get(i).getKey();
            String name = list.get(i).getValue();
            boolean immune = ConseilManager.isImmune(id);
            List<String> lore = new ArrayList<>();
            lore.add("&7Votes reçus : &f" + ConseilManager.votesReceived(id));
            lore.add("&7A voté : " + (ConseilManager.hasVoted(id) ? "&aoui" : "&cnon"));
            lore.add("&7Immunité : " + (immune ? "&a&lOUI" : "&cnon"));
            if (ConseilManager.hasCollier(id)) {
                lore.add("&eA {v_un_collier} dans son inventaire");
            }
            lore.add("");
            lore.add("&8Clic : activer ou retirer l'immunité");
            ItemStack head = MenuItems.head(name, (immune ? "&a&l" : "&f&l") + name, lore);
            if (immune) {
                MenuItems.glow(head);
            }
            container.setItem(i, head);
        }

        int base = (rows - 1) * 9;
        ConseilManager.Phase phase = ConseilManager.phase();
        container.setItem(base, MenuItems.named(Items.PAPER, "&6&lÉtat {v_du_conseil}", List.of(
                "&7Phase : &f" + ConseilManager.phaseLabel(),
                "&7Tour : &f" + ConseilManager.round(),
                "&7Votes reçus : &f" + ConseilManager.votesCount() + "&7/&f" + ConseilManager.candidates().size(),
                "&7Résultat : &f" + ConseilManager.resultLabel())));
        container.setItem(base + 2, MenuItems.named(Items.REDSTONE_BLOCK, "&c&lClore le vote", List.of(
                "&7Plus aucun vote possible.",
                phase == ConseilManager.Phase.VOTING ? "&aDisponible" : "&8Indisponible")));
        container.setItem(base + 3, MenuItems.named(Items.NOTE_BLOCK, "&6&lLancer le reveal", List.of(
                "&7Révèle les votes un par un (anonymes).",
                (phase == ConseilManager.Phase.CLOSED || phase == ConseilManager.Phase.COLLIER)
                        ? "&aDisponible" + (phase == ConseilManager.Phase.COLLIER ? " &7(clic = passer le choix {v_du_collier})" : "")
                        : "&8Indisponible")));
        container.setItem(base + 4, MenuItems.named(Items.CLOCK, "&b&lNouveau vote", List.of(
                "&7Rouvre le vote (égalité ou aucun vote valide).",
                (phase == ConseilManager.Phase.CLOSED || phase == ConseilManager.Phase.REVEALED)
                        ? "&aDisponible" : "&8Indisponible")));
        container.setItem(base + 6, MenuItems.named(Items.EMERALD_BLOCK, "&a&lValider et clore {v_le_conseil}", List.of(
                "&7L'éliminé part (spectateur), les autres retournent au camp.",
                phase == ConseilManager.Phase.REVEALED ? "&aDisponible" : "&8Indisponible")));
        container.setItem(base + 8, MenuItems.named(Items.BARRIER, "&4&lAnnuler {v_le_conseil}", List.of(
                "&7Annule tout, sans éliminé.")));
    }
}
