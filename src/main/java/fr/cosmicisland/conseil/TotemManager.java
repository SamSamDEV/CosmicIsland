package fr.cosmicisland.conseil;

import fr.cosmicisland.KLFiles;
import fr.cosmicisland.Txt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Totem d'immunite : attribution aux vainqueurs d'epreuves solo, detection et consommation. */
public final class TotemManager {
    /** Totems promis a des joueurs hors ligne : state/totem_pending.json (donnes a la connexion). */
    private static class Pending {
        List<String> players = new ArrayList<>();
    }

    private static final String FILE = "state/totem_pending.json";

    private TotemManager() {}

    public static boolean has(ServerPlayer player) {
        return TotemRegistry.TOTEM_ITEM != null && count(player, TotemRegistry.TOTEM_ITEM) > 0;
    }

    /** Retire un totem de l'inventaire du joueur. */
    public static boolean consume(ServerPlayer player) {
        return TotemRegistry.TOTEM_ITEM != null && removeOne(player, TotemRegistry.TOTEM_ITEM);
    }

    public static boolean hasCollier(ServerPlayer player) {
        return count(player, ConseilRegistry.COLLIER.get()) > 0;
    }

    public static boolean consumeCollier(ServerPlayer player) {
        return removeOne(player, ConseilRegistry.COLLIER.get());
    }

    private static int count(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (ItemStack s : inv.items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        for (ItemStack s : inv.offhand) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static boolean removeOne(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) {
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        for (ItemStack s : inv.offhand) {
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ attribution

    /** Donne un totem a chaque vainqueur (en ligne : directement ; hors ligne : a sa prochaine connexion). */
    public static void award(MinecraftServer server, List<UUID> winners) {
        if (TotemRegistry.TOTEM_ITEM == null) {
            return;
        }
        Pending pending = null;
        for (UUID id : winners) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                give(p);
            } else {
                if (pending == null) {
                    pending = KLFiles.load(FILE, Pending.class, Pending::new);
                }
                if (!pending.players.contains(id.toString())) {
                    pending.players.add(id.toString());
                }
            }
        }
        if (pending != null) {
            KLFiles.save(FILE, pending);
        }
    }

    private static void give(ServerPlayer p) {
        ItemStack stack = new ItemStack(TotemRegistry.TOTEM_ITEM);
        if (!p.getInventory().add(stack)) {
            p.drop(stack, false);
        }
        ConseilMessages.send(ConseilManager.config().message("totemGiven"), p, java.util.Map.of());
    }

    public static void onLogin(ServerPlayer player) {
        if (!java.nio.file.Files.exists(KLFiles.dir().resolve(FILE))) {
            return;
        }
        Pending pending = KLFiles.load(FILE, Pending.class, Pending::new);
        if (pending.players.remove(player.getUUID().toString())) {
            KLFiles.save(FILE, pending);
            give(player);
        }
    }
}
