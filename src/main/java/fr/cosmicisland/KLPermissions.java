package fr.cosmicisland;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Niveaux staff Koh-Lanta, lus dans config/cosmicisland/admins.json */
public final class KLPermissions {
    public static final int NONE = 0;
    public static final int ORGANISATEUR = 1;
    public static final int ADMIN = 2;

    public static class AdminConfig {
        public List<Entry> staff = new ArrayList<>();

        public static class Entry {
            public String name = "";
            public String uuid = "";   // optionnel mais recommande (plus sur que le pseudo)
            public String level = "ADMIN"; // ADMIN ou ORGANISATEUR
        }
    }

    private static AdminConfig config = new AdminConfig();

    private KLPermissions() {}

    public static void load() {
        config = KLFiles.load("admins.json", AdminConfig.class, () -> {
            AdminConfig c = new AdminConfig();
            AdminConfig.Entry e = new AdminConfig.Entry();
            e.name = "TonPseudo";
            e.level = "ADMIN";
            c.staff.add(e);
            return c;
        });
    }

    public static int levelOf(String name, UUID uuid) {
        int best = NONE;
        for (AdminConfig.Entry e : config.staff) {
            boolean byUuid = e.uuid != null && !e.uuid.isBlank() && e.uuid.equalsIgnoreCase(uuid.toString());
            boolean byName = e.name != null && e.name.equalsIgnoreCase(name);
            if (!byUuid && !byName) {
                continue;
            }
            int level = "ADMIN".equalsIgnoreCase(e.level) ? ADMIN : ORGANISATEUR;
            best = Math.max(best, level);
        }
        return best;
    }

    public static int levelOf(ServerPlayer player) {
        return levelOf(player.getGameProfile().getName(), player.getUUID());
    }

    /** Joueur : niveau staff requis. Console / autre source : permission 4 (console du serveur). */
    public static boolean has(CommandSourceStack source, int needed) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return levelOf(player) >= needed;
        }
        return source.hasPermission(4);
    }
}
