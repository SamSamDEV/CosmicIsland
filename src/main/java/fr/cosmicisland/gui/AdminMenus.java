package fr.cosmicisland.gui;

import com.mojang.authlib.GameProfile;
import fr.cosmicisland.KLFiles;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.KLRole;
import fr.cosmicisland.RoleManager;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import fr.cosmicisland.camp.CampfireManager;
import fr.cosmicisland.conseil.ConseilAdminMenu;
import fr.cosmicisland.conseil.ConseilManager;
import fr.cosmicisland.game.GameConfig;
import fr.cosmicisland.game.GameManager;
import fr.cosmicisland.game.GameRegistry;
import fr.cosmicisland.game.RankingManager;
import fr.cosmicisland.game.SpawnManager;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Menu admin (/ccis admin) : toutes les commandes principales en interface.
 * Les actions sensibles (roles, fusion, reset, spawns, activation des jeux, reload) sont reservees aux admins.
 * Ce qui demande de taper du texte (nom ou tag d'equipe, annonces) reste en commande.
 */
public final class AdminMenus {

    private record ColorChoice(Item item, ChatFormatting format, String label) {}

    private static final ColorChoice[] COLORS = {
            new ColorChoice(Items.RED_WOOL, ChatFormatting.RED, "Rouge"),
            new ColorChoice(Items.ORANGE_WOOL, ChatFormatting.GOLD, "Or"),
            new ColorChoice(Items.YELLOW_WOOL, ChatFormatting.YELLOW, "Jaune"),
            new ColorChoice(Items.LIME_WOOL, ChatFormatting.GREEN, "Vert clair"),
            new ColorChoice(Items.GREEN_WOOL, ChatFormatting.DARK_GREEN, "Vert"),
            new ColorChoice(Items.LIGHT_BLUE_WOOL, ChatFormatting.AQUA, "Bleu clair"),
            new ColorChoice(Items.CYAN_WOOL, ChatFormatting.DARK_AQUA, "Cyan"),
            new ColorChoice(Items.BLUE_WOOL, ChatFormatting.BLUE, "Bleu"),
            new ColorChoice(Items.PINK_WOOL, ChatFormatting.LIGHT_PURPLE, "Rose"),
            new ColorChoice(Items.PURPLE_WOOL, ChatFormatting.DARK_PURPLE, "Violet"),
            new ColorChoice(Items.WHITE_WOOL, ChatFormatting.WHITE, "Blanc"),
            new ColorChoice(Items.LIGHT_GRAY_WOOL, ChatFormatting.GRAY, "Gris clair"),
            new ColorChoice(Items.GRAY_WOOL, ChatFormatting.DARK_GRAY, "Gris"),
            new ColorChoice(Items.BROWN_WOOL, ChatFormatting.DARK_RED, "Rouge foncé")
    };

    private AdminMenus() {}

    // ------------------------------------------------------------ utilitaires

    private static void msg(ServerPlayer p, String text) {
        p.sendSystemMessage(Txt.of(text));
    }

    private static boolean needAdmin(ServerPlayer p) {
        if (KLPermissions.levelOf(p) >= KLPermissions.ADMIN) {
            return true;
        }
        msg(p, "&cAction réservée aux admins.");
        return false;
    }

    private static void clear(Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            c.setItem(i, ItemStack.EMPTY);
        }
    }

    private static ItemStack backItem(String where) {
        return MenuItems.named(Items.ARROW, "&7&lRetour", List.of("&8" + where));
    }

    private static Item bannerFor(ChatFormatting format) {
        return switch (format) {
            case RED, DARK_RED -> Items.RED_BANNER;
            case GOLD -> Items.ORANGE_BANNER;
            case YELLOW -> Items.YELLOW_BANNER;
            case GREEN -> Items.LIME_BANNER;
            case DARK_GREEN -> Items.GREEN_BANNER;
            case AQUA -> Items.LIGHT_BLUE_BANNER;
            case DARK_AQUA -> Items.CYAN_BANNER;
            case BLUE, DARK_BLUE -> Items.BLUE_BANNER;
            case LIGHT_PURPLE -> Items.PINK_BANNER;
            case DARK_PURPLE -> Items.PURPLE_BANNER;
            case GRAY -> Items.LIGHT_GRAY_BANNER;
            case DARK_GRAY, BLACK -> Items.GRAY_BANNER;
            default -> Items.WHITE_BANNER;
        };
    }

    /** Equipes affichees dans les menus (8 au maximum). */
    private static List<String> teamIds() {
        List<String> list = new ArrayList<>(TeamManager.ids());
        return list.size() > 8 ? new ArrayList<>(list.subList(0, 8)) : list;
    }

    private static String colorCode(TeamManager.KLTeam team) {
        return "&" + team.format().getChar();
    }

    private static void confirm(ServerPlayer player, String title, String description,
                                Runnable onConfirm, Runnable onCancel) {
        SimpleContainer c = new SimpleContainer(27);
        c.setItem(11, MenuItems.named(Items.LIME_CONCRETE, "&a&lCONFIRMER", List.of("&7" + description)));
        c.setItem(13, MenuItems.named(Items.PAPER, "&6&l" + title, List.of("&7" + description)));
        c.setItem(15, MenuItems.named(Items.RED_CONCRETE, "&c&lANNULER", List.of("&7Revenir en arrière")));
        KLMenu.openWith(player, Txt.of("&6&l" + title), 3, c, "admin-confirm", (p, slot, button) -> {
            if (slot == 11) {
                onConfirm.run();
            } else if (slot == 15) {
                onCancel.run();
            }
        });
    }

    // ------------------------------------------------------------ menu principal

    public static void openMain(ServerPlayer player) {
        SimpleContainer c = new SimpleContainer(27);
        c.setItem(10, MenuItems.named(Items.COMPASS, "&6&lÉpreuves", List.of(
                "&7Démarrer, lancer, geler, arrêter,", "&7activer et désactiver les jeux")));
        c.setItem(11, MenuItems.named(Items.WHITE_BANNER, "&6&lÉquipes", List.of(
                "&7Couleur, gras du tag, {v_reunification}")));
        c.setItem(12, MenuItems.named(Items.PLAYER_HEAD, "&6&lJoueurs et rôles", List.of(
                "&7Rôle et équipe des joueurs connectés")));
        c.setItem(13, MenuItems.named(Items.CAMPFIRE, "&6&lFeux de camp", List.of(
                "&7Jauge de chaque équipe, ajout et retrait")));
        c.setItem(14, MenuItems.named(Items.LECTERN, "&6&l{v_conseil}", List.of(
                "&7Lancer ou piloter {v_le_conseil}")));
        c.setItem(15, MenuItems.named(Items.GOLD_INGOT, "&6&lClassement", List.of(
                "&7Top 10 et remises à zéro")));
        c.setItem(16, MenuItems.named(Items.ENDER_PEARL, "&6&lSpawns", List.of(
                "&7Zones de jeu, bases, respawn,", "&7{v_le_conseil} et {v_elimination}")));
        c.setItem(22, MenuItems.named(Items.REDSTONE, "&c&lRecharger les configs", List.of(
                "&7Relit tous les fichiers de config.", "&8Admin uniquement")));
        c.setItem(26, MenuItems.named(Items.BARRIER, "&c&lFermer", List.of("&7Ferme le menu")));

        KLMenu.openWith(player, Txt.of("&6&lCosmic Island &8- &7Menu admin"), 3, c, "admin-main",
                (p, slot, button) -> {
                    switch (slot) {
                        case 10 -> openGames(p);
                        case 11 -> openTeams(p);
                        case 12 -> openPlayers(p);
                        case 13 -> openCampfires(p);
                        case 14 -> openConseil(p);
                        case 15 -> openRanking(p);
                        case 16 -> openSpawns(p);
                        case 22 -> {
                            if (needAdmin(p)) {
                                KLFiles.loadAll();
                                TeamManager.sync(p.getServer());
                                msg(p, "&aConfigs rechargées.");
                            }
                        }
                        case 26 -> p.closeContainer();
                        default -> { }
                    }
                });
    }

    // ------------------------------------------------------------ epreuves

    private static void openGames(ServerPlayer player) {
        List<String> ids = new ArrayList<>(GameRegistry.ids());
        int rows = Math.max(2, Math.min(6, (ids.size() + 8) / 9 + 1));
        SimpleContainer c = new SimpleContainer(rows * 9);
        fillGames(c, ids, rows);
        KLMenu.openWith(player, Txt.of("&6&lÉpreuves"), rows, c, "admin-games", (p, slot, button) -> {
            int base = (rows - 1) * 9;
            if (slot < base) {
                if (slot < ids.size()) {
                    String id = ids.get(slot);
                    if (button == 1) {
                        GameConfig cfg = GameRegistry.config(id);
                        if (cfg != null && needAdmin(p)) {
                            cfg.enabled = !cfg.enabled;
                            GameRegistry.saveConfig(id);
                            msg(p, "&7Jeu &f" + cfg.displayName + "&7 : " + (cfg.enabled ? "&aactivé" : "&cdésactivé"));
                        }
                    } else {
                        String err = GameManager.start(id);
                        if (err != null) {
                            msg(p, "&c" + err);
                        }
                    }
                }
            } else if (slot == base + 2) {
                String err = GameManager.go();
                if (err != null) {
                    msg(p, "&c" + err);
                }
            } else if (slot == base + 3) {
                if (GameManager.isFrozen()) {
                    GameManager.unfreeze();
                    msg(p, "&aParticipants libérés.");
                } else {
                    String err = GameManager.freeze();
                    if (err != null) {
                        msg(p, "&c" + err);
                    }
                }
            } else if (slot == base + 4) {
                if (GameManager.state() == GameManager.State.IDLE) {
                    msg(p, "&cAucune épreuve en cours.");
                } else {
                    GameManager.finish(true);
                }
            } else if (slot == base + 8) {
                openMain(p);
                return;
            }
            fillGames(c, ids, rows);
        });
    }

    private static void fillGames(Container c, List<String> ids, int rows) {
        clear(c);
        int base = (rows - 1) * 9;
        for (int i = 0; i < ids.size() && i < base; i++) {
            String id = ids.get(i);
            GameConfig cfg = GameRegistry.config(id);
            if (cfg == null) {
                continue;
            }
            boolean active = GameManager.isActive(id);
            List<String> lore = new ArrayList<>();
            lore.add("&7Identifiant : &f" + id);
            lore.add("&7État : " + (cfg.enabled ? "&aactivé" : "&cdésactivé"));
            if (active) {
                lore.add("&eÉpreuve en cours : &f" + GameManager.describe());
            }
            lore.add("");
            lore.add("&8Clic gauche : démarrer (invitations)");
            lore.add("&8Clic droit : activer ou désactiver (admin)");
            ItemStack stack = MenuItems.named(cfg.enabled ? Items.LIME_DYE : Items.GRAY_DYE,
                    "&f&l" + cfg.displayName, lore);
            if (active) {
                MenuItems.glow(stack);
            }
            c.setItem(i, stack);
        }
        c.setItem(base, MenuItems.named(Items.PAPER, "&6&lÉtat", List.of(
                "&7Épreuve : &f" + GameManager.describe(),
                "&7Participants gelés : " + (GameManager.isFrozen() ? "&boui" : "&7non"))));
        c.setItem(base + 2, MenuItems.named(Items.EMERALD_BLOCK, "&a&lGO : lancer le décompte", List.of(
                "&7Départ de l'épreuve (décompte 3-2-1).")));
        c.setItem(base + 3, GameManager.isFrozen()
                ? MenuItems.named(Items.PACKED_ICE, "&b&lLibérer les participants", List.of("&7Ils pourront à nouveau bouger."))
                : MenuItems.named(Items.ICE, "&b&lImmobiliser les participants", List.of("&7Pour un départ simultané.")));
        c.setItem(base + 4, MenuItems.named(Items.RED_CONCRETE, "&c&lArrêter l'épreuve", List.of(
                "&7Arrête et restaure les inventaires.")));
        c.setItem(base + 8, backItem("Menu principal"));
    }

    // ------------------------------------------------------------ equipes

    private static void openTeams(ServerPlayer player) {
        SimpleContainer c = new SimpleContainer(27);
        List<String> ids = teamIds();
        fillTeams(c, ids);
        KLMenu.openWith(player, Txt.of("&6&lÉquipes"), 3, c, "admin-teams", (p, slot, button) -> {
            if (slot < 9 && slot < ids.size()) {
                openTeamDetail(p, ids.get(slot));
            } else if (slot == 22) {
                if (KLPermissions.levelOf(p) < KLPermissions.ORGANISATEUR) {
                    msg(p, "&cRéservé aux organisateurs et admins.");
                } else {
                    confirm(p, "{v_reunification} des équipes",
                            "Annonce à tous, puis tout le monde passe dans l'équipe unique.",
                            () -> {
                                TeamManager.merge(null);
                                msg(p, "&a{v_reunification} effectuée.");
                                openTeams(p);
                            },
                            () -> openTeams(p));
                }
            } else if (slot == 26) {
                openMain(p);
            }
        });
    }

    private static void fillTeams(Container c, List<String> ids) {
        clear(c);
        for (int i = 0; i < ids.size() && i < 9; i++) {
            TeamManager.KLTeam t = TeamManager.get(ids.get(i));
            if (t == null) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add("&7Identifiant : &f" + ids.get(i));
            lore.add("&7Tag : " + colorCode(t) + t.tag);
            lore.add("&7Membres : &f" + t.members.size());
            int shown = 0;
            for (String member : t.members.values()) {
                if (shown++ >= 8) {
                    lore.add("&8...");
                    break;
                }
                lore.add("&8- &7" + member);
            }
            lore.add("");
            lore.add("&8Clic : couleur et gras du tag");
            lore.add("&8Nom et tag : /ccis team name|tag");
            c.setItem(i, MenuItems.named(bannerFor(t.format()), colorCode(t) + "&l" + t.name, lore));
        }
        c.setItem(22, MenuItems.named(Items.NETHER_STAR, "&d&l{v_reunification} des équipes", List.of(
                "&7État : " + (TeamManager.isMerged() ? "&afusionnées" : "&7séparées"),
                "&7Tous les joueurs passent dans l'équipe unique.",
                "&8Organisateur ou admin (nom : /ccis fusion <nom>)")));
        c.setItem(26, backItem("Menu principal"));
    }

    private static void openTeamDetail(ServerPlayer player, String teamId) {
        TeamManager.KLTeam team = TeamManager.get(teamId);
        if (team == null) {
            return;
        }
        SimpleContainer c = new SimpleContainer(36);
        fillTeamDetail(c, teamId);
        KLMenu.openWith(player, Txt.of("&6&lÉquipe : " + colorCode(team) + team.name), 4, c, "admin-team",
                (p, slot, button) -> {
                    TeamManager.KLTeam t = TeamManager.get(teamId);
                    if (t == null) {
                        return;
                    }
                    if (slot < COLORS.length) {
                        t.color = COLORS[slot].format().getName();
                        TeamManager.changed();
                    } else if (slot == 27) {
                        t.bold = !t.bold;
                        TeamManager.changed();
                    } else if (slot == 35) {
                        openTeams(p);
                        return;
                    }
                    fillTeamDetail(c, teamId);
                });
    }

    private static void fillTeamDetail(Container c, String teamId) {
        clear(c);
        TeamManager.KLTeam t = TeamManager.get(teamId);
        if (t == null) {
            return;
        }
        for (int i = 0; i < COLORS.length; i++) {
            ColorChoice choice = COLORS[i];
            boolean current = t.format() == choice.format();
            ItemStack stack = MenuItems.named(choice.item(),
                    "&" + choice.format().getChar() + "&l" + choice.label(),
                    List.of(current ? "&a&lCouleur actuelle" : "&7Clique pour choisir cette couleur"));
            if (current) {
                MenuItems.glow(stack);
            }
            c.setItem(i, stack);
        }
        c.setItem(27, MenuItems.named(Items.BOOK, "&f&lTag en gras : " + (t.bold ? "&aOUI" : "&cNON"),
                List.of("&7Clique pour changer")));
        c.setItem(31, MenuItems.named(Items.PAPER, "&6&lAperçu", List.of(
                "&7Nom : " + colorCode(t) + t.name,
                "&7Tag : " + colorCode(t) + (t.bold ? "&l" : "") + t.tag,
                "",
                "&8Nom et tag : /ccis team name " + teamId + " <texte>",
                "&8                /ccis team tag " + teamId + " <texte>")));
        c.setItem(35, backItem("Liste des équipes"));
    }

    // ------------------------------------------------------------ joueurs et roles

    private static void openPlayers(ServerPlayer player) {
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ServerPlayer sp : player.getServer().getPlayerList().getPlayers()) {
            ids.add(sp.getUUID());
            names.add(sp.getGameProfile().getName());
        }
        int rows = Math.max(2, Math.min(6, (ids.size() + 8) / 9 + 1));
        SimpleContainer c = new SimpleContainer(rows * 9);
        int base = (rows - 1) * 9;
        for (int i = 0; i < ids.size() && i < base; i++) {
            KLRole role = RoleManager.get(ids.get(i));
            String teamId = TeamManager.teamIdOf(ids.get(i));
            String team = teamId == null ? "aucune" : TeamManager.get(teamId).name;
            c.setItem(i, MenuItems.head(names.get(i), "&f&l" + names.get(i), List.of(
                    "&7Rôle : &f" + (role == null ? "non enregistré" : role.id()),
                    "&7Équipe : &f" + team,
                    "",
                    "&8Clic : changer le rôle et l'équipe")));
        }
        c.setItem(base + 8, backItem("Menu principal"));
        KLMenu.openWith(player, Txt.of("&6&lJoueurs connectés"), rows, c, "admin-players", (p, slot, button) -> {
            if (slot < base && slot < ids.size()) {
                openPlayer(p, ids.get(slot), names.get(slot));
            } else if (slot == base + 8) {
                openMain(p);
            }
        });
    }

    private static void openPlayer(ServerPlayer viewer, UUID id, String name) {
        SimpleContainer c = new SimpleContainer(36);
        fillPlayer(c, id, name);
        KLMenu.openWith(viewer, Txt.of("&6&lJoueur : &f" + name), 4, c, "admin-player", (p, slot, button) -> {
            GameProfile profile = new GameProfile(id, name);
            List<String> teams = teamIds();
            if (slot >= 10 && slot <= 12) {
                if (!needAdmin(p)) {
                    return;
                }
                KLRole role = KLRole.values()[slot - 10];
                RoleManager.set(profile, role);
                ServerPlayer online = p.getServer().getPlayerList().getPlayer(id);
                if (online != null) {
                    RoleManager.apply(online, role);
                }
                msg(p, "&a" + name + " : rôle &f" + role.id());
            } else if (slot >= 18 && slot < 26) {
                int i = slot - 18;
                if (i < teams.size()) {
                    TeamManager.assign(teams.get(i), profile);
                    msg(p, "&a" + name + " : équipe &f" + TeamManager.get(teams.get(i)).name);
                }
            } else if (slot == 26) {
                TeamManager.remove(profile);
                msg(p, "&a" + name + " : retiré de son équipe.");
            } else if (slot == 35) {
                openPlayers(p);
                return;
            }
            fillPlayer(c, id, name);
        });
    }

    private static void fillPlayer(Container c, UUID id, String name) {
        clear(c);
        KLRole role = RoleManager.get(id);
        String currentTeam = TeamManager.teamIdOf(id);
        c.setItem(4, MenuItems.head(name, "&f&l" + name, List.of(
                "&7Rôle : &f" + (role == null ? "non enregistré (spectateur par défaut)" : role.id()),
                "&7Équipe : &f" + (currentTeam == null ? "aucune" : TeamManager.get(currentTeam).name))));

        Item[] roleItems = {Items.LIME_CONCRETE, Items.LIGHT_BLUE_CONCRETE, Items.ORANGE_CONCRETE};
        String[] roleLabels = {"Participant", "Spectateur", "Organisateur"};
        for (int i = 0; i < 3; i++) {
            boolean current = role == KLRole.values()[i];
            ItemStack stack = MenuItems.named(roleItems[i], "&f&l" + roleLabels[i], List.of(
                    current ? "&a&lRôle actuel" : "&7Clique pour attribuer ce rôle", "&8Admin uniquement"));
            if (current) {
                MenuItems.glow(stack);
            }
            c.setItem(10 + i, stack);
        }

        List<String> teams = teamIds();
        for (int i = 0; i < teams.size(); i++) {
            TeamManager.KLTeam t = TeamManager.get(teams.get(i));
            if (t == null) {
                continue;
            }
            boolean current = teams.get(i).equals(currentTeam);
            ItemStack stack = MenuItems.named(bannerFor(t.format()), colorCode(t) + "&l" + t.name, List.of(
                    current ? "&a&lÉquipe actuelle" : "&7Clique pour mettre dans cette équipe"));
            if (current) {
                MenuItems.glow(stack);
            }
            c.setItem(18 + i, stack);
        }
        c.setItem(26, MenuItems.named(Items.BARRIER, "&c&lAucune équipe", List.of("&7Retire le joueur de son équipe")));
        c.setItem(35, backItem("Liste des joueurs"));
    }

    // ------------------------------------------------------------ conseil

    private static void openConseil(ServerPlayer player) {
        if (ConseilManager.phase() != ConseilManager.Phase.IDLE) {
            ConseilAdminMenu.open(player);
            return;
        }
        List<String> ids = teamIds();
        SimpleContainer c = new SimpleContainer(27);
        boolean spawns = SpawnManager.get("conseil") != null && SpawnManager.get("elimination") != null;
        c.setItem(4, MenuItems.named(Items.PAPER, "&6&lLancer {v_un_conseil}", List.of(
                "&7Choisis l'équipe qui va {v_au_conseil}.",
                "&7Spawns {v_du_conseil} et {v_du_elimination} : " + (spawns ? "&adéfinis" : "&cmanquants (menu Spawns)"),
                "",
                "&8Clic gauche : {v_le_conseil} réel",
                "&8Clic droit : {v_le_conseil} TEST (3 joueurs fictifs)")));
        for (int i = 0; i < ids.size() && i < 9; i++) {
            TeamManager.KLTeam t = TeamManager.get(ids.get(i));
            if (t == null) {
                continue;
            }
            c.setItem(9 + i, MenuItems.named(bannerFor(t.format()), colorCode(t) + "&l" + t.name, List.of(
                    "&7Membres : &f" + t.members.size(),
                    "&8Lancer {v_le_conseil} pour cette équipe")));
        }
        c.setItem(26, backItem("Menu principal"));
        KLMenu.openWith(player, Txt.of("&6&l{v_conseil}"), 3, c, "admin-conseil", (p, slot, button) -> {
            int i = slot - 9;
            if (slot >= 9 && slot < 18 && i < ids.size()) {
                String err = button == 1 ? ConseilManager.start(ids.get(i), 3) : ConseilManager.start(ids.get(i));
                if (err != null) {
                    msg(p, "&c" + err);
                } else {
                    ConseilAdminMenu.open(p);
                }
            } else if (slot == 26) {
                openMain(p);
            }
        });
    }

    // ------------------------------------------------------------ classement

    private static void openRanking(ServerPlayer player) {
        List<RankingManager.Row> top = RankingManager.top(null, 10);
        List<String> gameIds = new ArrayList<>(GameRegistry.ids());
        SimpleContainer c = new SimpleContainer(45);
        for (int i = 0; i < top.size(); i++) {
            RankingManager.Row r = top.get(i);
            String name = r.name() == null ? "?" : r.name();
            String wins = r.wins() + (r.wins() > 1 ? " victoires" : " victoire");
            c.setItem(i, MenuItems.head(name, "&e&l#" + (i + 1) + " &f&l" + name, List.of("&a" + wins)));
        }
        c.setItem(36, MenuItems.named(Items.TNT, "&c&lRemise à zéro : tout", List.of(
                "&7Efface toutes les victoires.", "&7Copie de secours automatique.", "&8Admin uniquement")));
        for (int i = 0; i < gameIds.size() && i < 6; i++) {
            GameConfig cfg = GameRegistry.config(gameIds.get(i));
            String label = cfg == null ? gameIds.get(i) : cfg.displayName;
            c.setItem(37 + i, MenuItems.named(Items.RED_TERRACOTTA, "&c&lRemise à zéro : " + label, List.of(
                    "&7Efface les victoires de ce jeu.", "&8Admin uniquement")));
        }
        c.setItem(44, backItem("Menu principal"));
        KLMenu.openWith(player, Txt.of("&6&lClassement"), 5, c, "admin-ranking", (p, slot, button) -> {
            if (slot == 36) {
                askReset(p, "all", "Tout le classement");
            } else if (slot >= 37 && slot < 43 && slot - 37 < gameIds.size()) {
                askReset(p, gameIds.get(slot - 37), "Le classement de " + gameIds.get(slot - 37));
            } else if (slot == 44) {
                openMain(p);
            }
        });
    }

    private static void askReset(ServerPlayer player, String target, String label) {
        if (!needAdmin(player)) {
            return;
        }
        confirm(player, "Remise à zéro", label + " (copie de secours automatique)",
                () -> {
                    String key = player.getUUID().toString();
                    RankingManager.requestReset(key, target);
                    RankingManager.ResetResult r = RankingManager.confirmReset(key);
                    if (r == null) {
                        msg(player, "&cLa remise à zéro a échoué.");
                    } else {
                        msg(player, "&aClassement remis à zéro (" + r.target() + "). Sauvegarde : "
                                + (r.backup() == null ? "aucune" : r.backup()));
                    }
                    openRanking(player);
                },
                () -> openRanking(player));
    }

    // ------------------------------------------------------------ feux de camp

    private static void openCampfires(ServerPlayer player) {
        SimpleContainer c = new SimpleContainer(27);
        List<String> ids = teamIds();
        fillCampfires(c, ids);
        KLMenu.openWith(player, Txt.of("&6&lFeux de camp"), 3, c, "admin-campfires", (p, slot, button) -> {
            int i = slot - 9;
            if (slot >= 9 && slot < 18 && i < ids.size()) {
                String team = ids.get(i);
                CampfireManager.Fire fire = CampfireManager.get(team);
                if (fire == null) {
                    msg(p, "&cAucun feu défini pour cette équipe (regarde un campfire : /ccis campfire set <équipe>).");
                } else if (needAdmin(p)) {
                    CampfireManager.setFuel(team, fire.fuel + (button == 1 ? -10.0 : 10.0));
                }
                fillCampfires(c, ids);
            } else if (slot == 26) {
                openMain(p);
            }
        });
    }

    private static void fillCampfires(Container c, List<String> ids) {
        clear(c);
        c.setItem(4, MenuItems.named(Items.PAPER, "&6&lFeux de camp", List.of(
                "&7Clic gauche : +10 de carburant (admin)",
                "&7Clic droit : -10 de carburant (admin)",
                "&7Les joueurs alimentent leur feu en clic droit avec du bois.")));
        for (int i = 0; i < ids.size() && i < 9; i++) {
            TeamManager.KLTeam t = TeamManager.get(ids.get(i));
            if (t == null) {
                continue;
            }
            CampfireManager.Fire f = CampfireManager.get(ids.get(i));
            List<String> lore = new ArrayList<>();
            Item item;
            if (f == null) {
                item = Items.GRAY_DYE;
                lore.add("&cAucun feu défini");
                lore.add("&8/ccis campfire set " + ids.get(i) + " (en regardant un campfire)");
            } else {
                int pct = CampfireManager.percent(f);
                int filled = Math.max(0, Math.min(20, pct / 5));
                String bar = "&a" + "|".repeat(filled) + "&8" + "|".repeat(20 - filled);
                boolean out = CampfireManager.isOut(ids.get(i));
                item = out ? Items.COAL : Items.CAMPFIRE;
                lore.add(bar);
                lore.add(String.format("&7Carburant : &f%.1f&7/&f%.0f &7(%d%%)", f.fuel, CampfireManager.maxFuel(), pct));
                lore.add("&7Position : &f" + f.x + " " + f.y + " " + f.z);
                lore.add(out ? "&c&lMALUS ACTIF" : "&aFeu en vie");
            }
            c.setItem(9 + i, MenuItems.named(item, colorCode(t) + "&l" + t.name, lore));
        }
        c.setItem(26, backItem("Menu principal"));
    }

    // ------------------------------------------------------------ spawns

    private record SpawnEntry(String key, String label, int slot) {}

    private static List<SpawnEntry> spawnEntries() {
        List<SpawnEntry> list = new ArrayList<>();
        List<String> teams = teamIds();
        for (int i = 0; i < teams.size(); i++) {
            TeamManager.KLTeam t = TeamManager.get(teams.get(i));
            if (t == null) {
                continue;
            }
            String name = colorCode(t) + "&l" + t.name;
            list.add(new SpawnEntry(teams.get(i), name + " &7(zone de jeu)", 9 + i));
            list.add(new SpawnEntry("base:" + teams.get(i), name + " &7(base)", 18 + i));
            list.add(new SpawnEntry("respawn:" + teams.get(i), name + " &7(respawn mort)", 27 + i));
        }
        list.add(new SpawnEntry("conseil", "&6&l{v_conseil}", 36));
        list.add(new SpawnEntry("elimination", "&c&l{v_elimination}", 37));
        return list;
    }

    private static void openSpawns(ServerPlayer player) {
        SimpleContainer c = new SimpleContainer(45);
        fillSpawns(c);
        KLMenu.openWith(player, Txt.of("&6&lSpawns"), 5, c, "admin-spawns", (p, slot, button) -> {
            if (slot == 44) {
                openMain(p);
                return;
            }
            for (SpawnEntry entry : spawnEntries()) {
                if (entry.slot() != slot) {
                    continue;
                }
                if (button == 1) {
                    SpawnManager.Spawn spawn = SpawnManager.get(entry.key());
                    if (spawn == null) {
                        msg(p, "&cSpawn non défini.");
                    } else if (!SpawnManager.teleport(p.getServer(), p, spawn)) {
                        msg(p, "&cDimension du spawn introuvable.");
                    } else {
                        p.closeContainer();
                        return;
                    }
                } else if (needAdmin(p)) {
                    SpawnManager.set(entry.key(), p);
                    msg(p, "&aSpawn défini à ta position.");
                }
                break;
            }
            fillSpawns(c);
        });
    }

    private static void fillSpawns(Container c) {
        clear(c);
        c.setItem(4, MenuItems.named(Items.PAPER, "&6&lSpawns", List.of(
                "&7Clic gauche : définir à ta position (admin)",
                "&7Clic droit : se téléporter au spawn",
                "",
                "&7Ligne 1 : zone de jeu (départ et fin d'épreuve)",
                "&7Ligne 2 : base de l'équipe (/ccis base)",
                "&7Ligne 3 : respawn après une mort")));
        for (SpawnEntry entry : spawnEntries()) {
            SpawnManager.Spawn s = SpawnManager.get(entry.key());
            List<String> lore = new ArrayList<>();
            if (s == null) {
                lore.add("&cNon défini");
            } else {
                lore.add("&7" + s.dimension);
                lore.add(String.format("&a%.1f %.1f %.1f", s.x, s.y, s.z));
            }
            Item item;
            if (s == null) {
                item = Items.GRAY_DYE;
            } else if (entry.key().startsWith("base:")) {
                item = Items.CAMPFIRE;
            } else if (entry.key().startsWith("respawn:")) {
                item = Items.RED_BED;
            } else if (entry.key().equals("conseil")) {
                item = Items.BELL;
            } else if (entry.key().equals("elimination")) {
                item = Items.SKELETON_SKULL;
            } else {
                item = Items.ENDER_PEARL;
            }
            c.setItem(entry.slot(), MenuItems.named(item, entry.label(), lore));
        }
        c.setItem(44, backItem("Menu principal"));
    }
}
