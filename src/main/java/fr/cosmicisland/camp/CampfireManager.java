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
import net.minecraft.world.food.FoodData;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private static final Set<String> unlit = new HashSet<>();   // equipes dont le bloc est eteint / absent
    private static final Map<String, Integer> tiers = new HashMap<>();      // equipe -> -1 malus / 0 normal / 1 bonus
    private static final Map<UUID, String> zoneOf = new HashMap<>();         // joueur -> equipe dont il est dans la zone
    private static final Map<UUID, Float> lastExhaust = new HashMap<>();
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
        return f != null && (f.fuel <= config.debuffBelow || (config.requireLit && unlit.contains(team)));
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
        tiers.remove(key);
        save();
        refresh(key, f);
        sendToTeam(key, "registered", percent(f));
    }

    public static boolean remove(String team) {
        String key = team.toLowerCase();
        if (store.fires.remove(key) == null) {
            return false;
        }
        out.remove(key);
        warned.remove(key);
        unlit.remove(key);
        tiers.remove(key);
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
        updateLit(team, f);
        transitions(team, f);
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
            updateLit(team, f);
            boolean burning = !config.requireLit || !unlit.contains(team);
            if (f.fuel > 0.0 && burning) {
                f.fuel = Math.max(0.0, f.fuel - config.drainPerMinute / 60.0);
                dirty = true;
            }
            updateLit(team, f);
            transitions(team, f);
            updateBar(team, f);
        }
        playersTick();
        saveSeconds++;
        if (dirty && saveSeconds >= 30) {
            saveSeconds = 0;
            save();
        }
    }

    /** Messages quand le feu faiblit, s'eteint ou reprend. */
    private static void transitions(String team, Fire f) {
        boolean noFuel = f.fuel <= 0.0;
        boolean dark = config.requireLit && unlit.contains(team);
        boolean isOut = noFuel || dark;
        int pct = percent(f);
        if (isOut && !out.contains(team)) {
            out.add(team);
            sendToTeam(team, noFuel ? "out" : "unlit", pct);
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
        int tier = tierOf(f);
        Integer prev = tiers.put(team, tier);
        if (prev != null && prev != tier && config.teamBonuses && !(tier < 0 && isOut)) {
            sendToTeam(team, tier > 0 ? "tierHigh" : tier < 0 ? "tierLow" : "tierNormal", pct);
        }
    }

    private static int tierOf(Fire f) {
        int pct = percent(f);
        if (pct >= config.bonusAbovePercent) {
            return 1;
        }
        return pct < config.malusBelowPercent ? -1 : 0;
    }

    private static void sendToTeam(String team, String key, int pct) {
        GameConfig.Msg msg = config.message(key);
        TeamManager.KLTeam kt = TeamManager.get(team);
        Map<String, String> vars = Map.of("percent", String.valueOf(pct),
                "radius", String.format("%.0f", config.zoneRadius),
                "team", kt == null ? team : kt.name, "tag", kt == null ? "" : kt.tag);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (team.equals(TeamManager.teamIdOf(p.getUUID()))) {
                ConseilMessages.send(msg, p, vars);
            }
        }
    }

    /** Un bloc est-il un feu de camp (vanilla ou d'un mod : Build Your Campfire, etc.) ? */
    public static boolean isCampfireLike(BlockState state) {
        if (state.getBlock() instanceof CampfireBlock) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key != null && (key.getPath().contains("campfire") || key.getPath().contains("bonfire"))
                && state.hasProperty(BlockStateProperties.LIT);
    }

    /**
     * Observe l'etat reel du bloc (les joueurs l'allument eux-memes ; la pluie ou d'autres mods peuvent l'eteindre).
     * Ne rallume jamais de force sauf si forceLit est active.
     */
    private static void updateLit(String team, Fire f) {
        ServerLevel level = SpawnManager.level(server, f.dimension);
        if (level == null) {
            return;
        }
        BlockPos pos = new BlockPos(f.x, f.y, f.z);
        if (!level.isLoaded(pos)) {
            return; // chunk dechage : on garde le dernier etat connu
        }
        BlockState state = level.getBlockState(pos);
        if (!isCampfireLike(state)) {
            unlit.add(team); // bloc casse ou absent
            return;
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            boolean lit = state.getValue(BlockStateProperties.LIT);
            if (config.extinguishAtZero && f.fuel <= 0.0 && lit) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
                lit = false;
            } else if (config.forceLit && f.fuel > 0.0 && !lit) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 3);
                lit = true;
            }
            if (lit) {
                unlit.remove(team);
            } else {
                unlit.add(team);
            }
        } else {
            unlit.remove(team);
        }
    }

    // ------------------------------------------------------------ zones de base et bonus d'equipe

    /** Equipe dont la zone (cercle autour du feu) contient le joueur, ou null. */
    private static String zoneAt(ServerPlayer p) {
        String dimension = p.level().dimension().location().toString();
        String best = null;
        double bestDist = config.zoneRadius * config.zoneRadius;
        for (Map.Entry<String, Fire> e : store.fires.entrySet()) {
            Fire f = e.getValue();
            if (!f.dimension.equals(dimension)) {
                continue;
            }
            double dx = p.getX() - (f.x + 0.5);
            double dz = p.getZ() - (f.z + 0.5);
            double d = dx * dx + dz * dz;
            if (d <= bestDist) {
                bestDist = d;
                best = e.getKey();
            }
        }
        return best;
    }

    private static void playersTick() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String zone = zoneAt(p);
            String prev = zoneOf.get(p.getUUID());
            if (!java.util.Objects.equals(zone, prev)) {
                if (zone == null) {
                    zoneOf.remove(p.getUUID());
                } else {
                    zoneOf.put(p.getUUID(), zone);
                }
                notifyZone(p, prev, zone);
            }
            applyHunger(p);
        }
    }

    private static void notifyZone(ServerPlayer p, String prev, String now) {
        String mode = config.zoneMessages == null ? "staff" : config.zoneMessages.toLowerCase();
        if (mode.equals("none") || (mode.equals("staff") && KLPermissions.levelOf(p) < KLPermissions.ORGANISATEUR)) {
            return;
        }
        if (prev != null) {
            ConseilMessages.send(config.message("zoneLeave"), p, zoneVars(prev));
        }
        if (now != null) {
            ConseilMessages.send(config.message("zoneEnter"), p, zoneVars(now));
        }
    }

    private static Map<String, String> zoneVars(String team) {
        TeamManager.KLTeam kt = TeamManager.get(team);
        return Map.of("team", kt == null ? team : kt.name, "tag", kt == null ? "" : kt.tag,
                "radius", String.format("%.0f", config.zoneRadius));
    }

    /** Bonus (-15 % de faim au-dessus de 90 %) ou malus (+10 % sous 30 %) selon la jauge du feu de l'equipe. */
    private static void applyHunger(ServerPlayer p) {
        Fire f = config.teamBonuses ? get(TeamManager.teamIdOf(p.getUUID())) : null;
        if (f == null || p.isSpectator() || p.isCreative()) {
            lastExhaust.remove(p.getUUID());
            return;
        }
        int tier = tierOf(f);
        double factor = tier > 0 ? -config.bonusHungerReductionPercent / 100.0
                : tier < 0 ? config.malusHungerIncreasePercent / 100.0 : 0.0;
        FoodData food = p.getFoodData();
        float cur = food.getExhaustionLevel();
        Float last = lastExhaust.get(p.getUUID());
        if (last != null && factor != 0.0) {
            float delta = cur - last;
            if (delta > 0.0f) {
                cur = Math.max(0.0f, cur + (float) (delta * factor));
                food.setExhaustion(cur);
            }
        }
        lastExhaust.put(p.getUUID(), cur);
    }

    // ------------------------------------------------------------ pose / fabrication par les joueurs

    /** Un joueur pose un feu de camp : s'il est dans une equipe sans feu valide, ce feu devient celui de l'equipe. */
    public static void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!config.enabled || !config.autoRegister || server == null || !isCampfireLike(state)) {
            return;
        }
        String team = TeamManager.teamIdOf(player.getUUID());
        if (team == null || GameManager.isInGame(player.getUUID())) {
            return;
        }
        Fire existing = get(team);
        if (existing != null) {
            ServerLevel old = SpawnManager.level(server, existing.dimension);
            BlockPos oldPos = new BlockPos(existing.x, existing.y, existing.z);
            boolean valid = old != null && (!old.isLoaded(oldPos) || isCampfireLike(old.getBlockState(oldPos)));
            if (valid) {
                return; // l'equipe a deja un feu en place
            }
        }
        set(team, level, pos); // message "registered" envoye a toute l'equipe ; le feu demarre eteint, a eux de l'allumer
    }

    public static void onCrafted(ServerPlayer player) {
        if (config.enabled && config.autoRegister && TeamManager.teamIdOf(player.getUUID()) != null) {
            ConseilMessages.send(config.message("crafted"), player, Map.of());
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
        boolean dark = config.requireLit && unlit.contains(team) && f.fuel > 0.0;
        bar.setName(Txt.of("&6&lFeu de camp &7- &f" + pct + "%" + (dark ? " &c(éteint, à rallumer)" : "")));
        bar.setProgress((float) Math.max(0.0, Math.min(1.0, f.fuel / Math.max(1.0, config.maxFuel))));
        bar.setColor(pct > 50 ? BossEvent.BossBarColor.GREEN
                : pct > config.warnBelowPercent ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean staffInZone = KLPermissions.levelOf(p) >= KLPermissions.ORGANISATEUR && team.equals(zoneOf.get(p.getUUID()));
            if (team.equals(TeamManager.teamIdOf(p.getUUID())) || staffInZone) {
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
                        + (unlit.contains(e.getKey()) && f.fuel > 0.0 ? "  ÉTEINT (à rallumer)" : "")
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
