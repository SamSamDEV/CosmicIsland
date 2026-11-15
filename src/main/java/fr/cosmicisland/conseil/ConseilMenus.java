package fr.cosmicisland.conseil;

import fr.cosmicisland.Txt;
import fr.cosmicisland.gui.KLMenu;
import fr.cosmicisland.gui.MenuItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menus de vote des joueurs : liste des tetes, puis confirmation. */
public final class ConseilMenus {

    private ConseilMenus() {}

    /** Ouvre la liste des joueurs votables. Retourne un message d'erreur ou null. */
    public static String openVote(ServerPlayer player) {
        if (ConseilManager.phase() != ConseilManager.Phase.VOTING) {
            return "Le vote n'est pas ouvert.";
        }
        UUID me = player.getUUID();
        if (!ConseilManager.isCandidate(me)) {
            return "Tu ne participes pas à {v_au_conseil}.";
        }
        boolean self = ConseilManager.config().allowSelfVote;
        List<Map.Entry<UUID, String>> list = new ArrayList<>();
        for (Map.Entry<UUID, String> e : ConseilManager.candidates().entrySet()) {
            if ((self || !e.getKey().equals(me)) && !ConseilManager.isTotemProtected(e.getKey())) {
                list.add(e);
            }
        }

        int rows = Math.max(2, Math.min(6, (list.size() + 8) / 9 + 1));
        SimpleContainer container = new SimpleContainer(rows * 9);
        UUID current = ConseilManager.voteOf(me);
        Map<Integer, UUID> slots = new HashMap<>();

        for (int i = 0; i < list.size() && i < (rows - 1) * 9; i++) {
            UUID id = list.get(i).getKey();
            String name = list.get(i).getValue();
            boolean chosen = id.equals(current);
            List<String> lore = new ArrayList<>();
            lore.add("&7Clique pour voter contre ce joueur");
            if (chosen) {
                lore.add("");
                lore.add("&a&lTON VOTE ACTUEL");
            }
            ItemStack head = MenuItems.head(name, (chosen ? "&a&l" : "&f&l") + name, lore);
            if (chosen) {
                MenuItems.glow(head);
            }
            container.setItem(i, head);
            slots.put(i, id);
        }

        String recap = current == null
                ? "&7Tu n'as pas encore voté."
                : "&7Ton vote : &c&l" + ConseilManager.candidates().getOrDefault(current, "?");
        container.setItem(rows * 9 - 5, MenuItems.named(Items.PAPER, "&6&lTon choix",
                List.of(recap, "", "&8Tu peux changer ton vote jusqu'à la clôture.")));

        KLMenu.open(player, Txt.of("&6&l{v_conseil} &8- &7Vote"), rows, container, "conseil-vote", (p, slot) -> {
            UUID target = slots.get(slot);
            if (target != null) {
                openConfirm(p, target);
            }
        });
        return null;
    }

    private static void openConfirm(ServerPlayer player, UUID target) {
        String name = ConseilManager.candidates().get(target);
        if (name == null) {
            return;
        }
        SimpleContainer container = new SimpleContainer(27);
        container.setItem(11, MenuItems.named(Items.LIME_CONCRETE, "&a&lCONFIRMER",
                List.of("&7Voter contre &f" + name)));
        container.setItem(13, MenuItems.head(name, "&f&l" + name, List.of("&7Joueur visé par ton vote")));
        container.setItem(15, MenuItems.named(Items.RED_CONCRETE, "&c&lANNULER",
                List.of("&7Revenir à la liste")));

        KLMenu.open(player, Txt.of("&6&lConfirmer ton vote"), 3, container, "conseil-confirm", (p, slot) -> {
            if (slot == 11) {
                String err = ConseilManager.castVote(p, target);
                if (err != null) {
                    p.sendSystemMessage(Txt.of("&c" + err));
                }
                p.closeContainer();
            } else if (slot == 15) {
                openVote(p);
            }
        });
    }
}
