package fr.cosmicisland.camp;

import fr.cosmicisland.KLFiles;
import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.Txt;
import fr.cosmicisland.conseil.ConseilMessages;
import fr.cosmicisland.game.GameConfig;
import fr.cosmicisland.game.GameManager;
import fr.cosmicisland.game.SpawnManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feu de camp de chaque equipe : un bloc "campfire" place dans le monde, suivi par une jauge de carburant.
 * La jauge baisse avec le temps ; les joueurs de l'equipe l'alimentent en clic droit avec du bois (reglable).
 * A 0 le feu s'eteint (visuellement) et un malus s'applique aux epreuves.
 * Reglages : campfire.json. Etat (positions et niveaux) : campfires.json.
 */
public final class CampfireManager {

    public static class Fire {
        public String dimension = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
        public double fuel = 100.0;
    }

    private static class Store {
        Map<String, Fire> fires = new LinkedHashMap<>(); // equipe -> feu
    }

    private static CampfireConfig config = new CampfireConfig();
    private static Store store = new Store();
    private static MinecraftServer server;

    private static final Map<String, ServerBossEvent> bars = new HashMap<>();
    private static final Set<String> out = new HashSet<>();     // equipes dont le feu est eteint
    private static final Set<String> warned = new HashSet<>();  // equipes deja prevenues que le feu faiblit
    private static boolean dirty = false;
    private static int saveSeconds = 0;

    private CampfireManager() {}

    // ------------------------------------------------------------ fichiers et cycle de vie

    public static void load() {
        config = KLFiles.load("campfire.json", CampfireConfig.class, CampfireConfig::new);
        store = KLFiles.load("campfires.json", Store.class, Store::new);
        out.clear();
        warned.clear();
        for (Map.Entry<String, Fire> e : store.fires.entrySet()) {
            if (e.getValue().fuel <= 0.0) {
                out.add(e.getKey());
            } else if (percent(e.getValue()) <= config.warnBelowPercent) {
                warned.add(e.getKey());
            }
        }
    }

    private static void save() {
        KLFiles.save("campfires.json", store);
        dirty = false;
    }

    public static void init(MinecraftServer s) {
        server = s;
    }

    public static void shutdown() {
        if (dirty) {
            save();
        }
        for (ServerBossEvent bar : bars.values()) {
            bar.removeAllPlayers();
        }
        bars.clear();
        server = null;
    }

    // ------------------------------------------------------------ acces

    public static CampfireConfig config() {
        return config;
    }

    public static Fire get(String team) {
        return team == null ? null : store.fires.get(team.toLowerCase());
    }

    public static double maxFuel() {
        return config.maxFuel;
    }

    public static int percent(Fire f) {
        return (int) Math.round(f.fuel / Math.max(1.0, config.maxFuel) * 100.0);
    }

    /** Le feu de cette equipe est-il eteint (malus actif pendant les epreuves) ? Sans feu defini : jamais. */
    public static boolean isOut(String team) {
        if (team == null || !config.enabled) {
            return false;
        }
        Fire f = get(team);
        return f != null && f.fuel <= config.debuffBelow;
    }

    // ------------------------------------------------------------ gestion (commandes et menu)

    /** Definit le feu d'une equipe sur un bloc campfire. */
    public static void set(String team, ServerLevel level, BlockPos pos) {
        Fire f = new Fire();
        f.dimension = level.dimension().location().toString();
        f.x = pos.getX();
        f.y = pos.getY();
        f.z = pos.getZ();
        f.fuel = Math.min(config.startFuel, config.maxFuel);
        String key = team.toLowerCase();
        store.fires.put(key, f);
        out.remove(key);
        warned.remove(key);
        save();
        refresh(key, f);
    }

    public static boolean remove(String team) {
        String key = team.toLowerCase();
        if (store.fires.remove(key) == null) {
            return false;
        }
        out.remove(key);
        warned.remove(key);
        ServerBossEvent bar = bars.remove(key);
        if (bar != null) {
            bar.removeAllPlayers();
        }
        save();
        return true;
    }

    public static void setFuel(String team, double value) {
        String key = team.toLowerCase();
        Fire f = store.fires.get(key);
        if (f == null) {
            return;
        }
        f.fuel = Math.max(0.0, Math.min(config.maxFuel, value));
        save();
        refresh(key, f);
    }

    public static boolean teleportTo(ServerPlayer player, Fire f) {
        if (server == null) {
            return false;
        }
        ServerLevel level = SpawnManager.level(server, f.dimension);
        if (level == null) {
            return false;
        }
        player.teleportTo(level, f.x + 0.5, f.y + 1.0, f.z + 0.5, player.getYRot(), player.getXRot());
        return true;
    }

    private static void refresh(String team, Fire f) {
        if (server == null) {
            return;
        }
        transitions(team, f);
        updateLit(f);
        updateBar(team, f);
    }

    // ------------------------------------------------------------ alimentation (clic droit)

    /**
     * Tente d'alimenter un feu avec l'objet en main. Retourne true si l'action est geree
     * (le clic est alors annule : pas d'interaction vanilla avec le feu).
     */
    public static boolean tryFeed(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        if (!config.enabled || stack.isEmpty()) {
            return false;
        }
        String dimension = level.dimension().location().toString();
        String team = null;
        Fire fire = null;
        for (Map.Entry<String, Fire> e : store.fires.entrySet()) {
            Fire f = e.getValue();
            if (f.x == pos.getX() && f.y == pos.getY() && f.z == pos.getZ() && f.dimension.equals(dimension)) {
                team = e.getKey();
                fire = f;
                break;
            }
        }
        if (fire == null) {
            return false;
        }
        ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(stack.getItem());
        Double points = itemKey == null ? null : config.fuelItems.get(itemKey.toString());
        if (points == null || points <= 0.0) {
            return false;
        }
        if (GameManager.isInGame(player.getUUID())) {
            return false; // pas de ravitaillement pendant une epreuve
        }
        boolean staff = KLPermissions.levelOf(player) >= KLPermissions.ORGANISATEUR;
        if (!staff && !team.equals(TeamManager.teamIdOf(player.getUUID()))) {
            ConseilMessages.send(config.message("notYours"), player, Map.of());
            return true;
        }
        if (fire.fuel >= config.maxFuel - 0.001) {
            ConseilMessages.send(config.message("full"), player, Map.of());
            return true;
        }

        double before = fire.fuel;
        fire.fuel = Math.min(config.maxFuel, fire.fuel + points);
        double added = fire.fuel - before;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        dirty = true;

        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                10, 0.2, 0.1, 0.2, 0.02);
        ConseilMessages.send(config.message("fed"), player, Map.of(
                "points", String.format("%.0f", added),
                "percent", String.valueOf(percent(fire))));
        refresh(team, fire);
        return true;
    }

    // ------------------------------------------------------------ tick (1 fois par seconde)

    public static void tick() {
        if (server == null || !config.enabled || server.getTickCount() % 20 != 0) {
            return;
        }
        for (Map.Entry<String, Fire> e : new ArrayList<>(store.fires.entrySet())) {
            String team = e.getKey();
            Fire f = e.getValue();
            if (f.fuel > 0.0) {
                f.fuel = Math.max(0.0, f.fuel - config.drainPerMinute / 60.0);
                dirty = true;
            }
            transitions(team, f);
            updateLit(f);
            updateBar(team, f);
        }
        saveSeconds++;
        if (dirty && saveSeconds >= 30) {
            saveSeconds = 0;
            save();
        }
    }

    /** Messages quand le feu faiblit, s'eteint ou reprend. */
    private static void transitions(String team, Fire f) {
        boolean isOut = f.fuel <= 0.0;
        int pct = percent(f);
        if (isOut && !out.contains(team)) {
            out.add(team);
            sendToTeam(team, "out", pct);
        } else if (!isOut && out.contains(team)) {
            out.remove(team);
            sendToTeam(team, "reignited", pct);
        }
        if (!isOut && pct <= config.warnBelowPercent) {
            if (warned.add(team)) {
                sendToTeam(team, "low", pct);
            }
        } else if (pct > config.warnBelowPercent) {
            warned.remove(team);
        }
    }

    private static void sendToTeam(String team, String key, int pct) {
        GameConfig.Msg msg = config.message(key);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (team.equals(TeamManager.teamIdOf(p.getUUID()))) {
                ConseilMessages.send(msg, p, Map.of("percent", String.valueOf(pct)));
            }
        }
    }

    /** Allume ou eteint le bloc campfire selon la jauge. */
    private static void updateLit(Fire f) {
        ServerLevel level = SpawnManager.level(server, f.dimension);
        if (level == null) {
            return;
        }
        BlockPos pos = new BlockPos(f.x, f.y, f.z);
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT)) {
            boolean lit = f.fuel > 0.0;
            if (state.getValue(CampfireBlock.LIT) != lit) {
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, lit), 3);
            }
        }
    }

    private static void updateBar(String team, Fire f) {
        if (!config.showBar) {
            ServerBossEvent old = bars.remove(team);
            if (old != null) {
                old.removeAllPlayers();
            }
            return;
        }
        ServerBossEvent bar = bars.computeIfAbsent(team, k ->
                new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS));
        int pct = percent(f);
        bar.setName(Txt.of("&6&lFeu de camp &7- &f" + pct + "%"));
        bar.setProgress((float) Math.max(0.0, Math.min(1.0, f.fuel / Math.max(1.0, config.maxFuel))));
        bar.setColor(pct > 50 ? BossEvent.BossBarColor.GREEN
                : pct > config.warnBelowPercent ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (team.equals(TeamManager.teamIdOf(p.getUUID()))) {
                bar.addPlayer(p);
            } else {
                bar.removePlayer(p);
            }
        }
    }

    // ------------------------------------------------------------ malus pendant les epreuves

    private static MobEffect effectOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
    }

    /** Applique les effets de malus si le feu de l'equipe du joueur est eteint (appele chaque seconde en epreuve). */
    public static void applyDebuff(ServerPlayer player) {
        if (!config.enabled || !config.applyEffects) {
            return;
        }
        if (!isOut(TeamManager.teamIdOf(player.getUUID()))) {
            return;
        }
        for (CampfireConfig.EffectEntry entry : config.debuffEffects) {
            MobEffect effect = effectOf(entry.effect);
            if (effect != null) {
                player.addEffect(new MobEffectInstance(effect, 60, Math.max(0, entry.amplifier), false, true, true));
            }
        }
    }

    /** Retire les effets de malus a la fin de l'epreuve. */
    public static void clearDebuff(ServerPlayer player) {
        if (!isOut(TeamManager.teamIdOf(player.getUUID()))) {
            return;
        }
        for (CampfireConfig.EffectEntry entry : config.debuffEffects) {
            MobEffect effect = effectOf(entry.effect);
            if (effect != null) {
                player.removeEffect(effect);
            }
        }
    }

    // ------------------------------------------------------------ affichage

    public static List<Component> statusLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("=== Feux de camp ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!config.enabled) {
            lines.add(Component.literal("Désactivés dans campfire.json").withStyle(ChatFormatting.RED));
            return lines;
        }
        for (Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            Fire f = store.fires.get(e.getKey());
            Component value;
            if (f == null) {
                value = Component.literal("aucun feu défini").withStyle(ChatFormatting.RED);
            } else {
                String text = String.format("%.1f/%.0f (%d%%)  %d %d %d", f.fuel, config.maxFuel, percent(f), f.x, f.y, f.z)
                        + (isOut(e.getKey()) ? "  MALUS ACTIF" : "");
                value = Component.literal(text).withStyle(isOut(e.getKey()) ? ChatFormatting.RED : ChatFormatting.GREEN);
            }
            lines.add(Component.empty()
                    .append(Component.literal(e.getValue().name + " : ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
                    .append(value));
        }
        return lines;
    }
}
