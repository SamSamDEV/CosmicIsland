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
        /** Carburant ajoute mais pas encore verse dans la jauge (elle se remplit progressivement). */
        public double pending = 0.0;
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
    private static final Set<String> wasLit = new HashSet<>();   // equipes dont le bloc etait allume a la derniere verification
    private static final Set<String> unlit = new HashSet<>();   // equipes dont le bloc est eteint / absent
    private static final Map<UUID, String> zoneOf = new HashMap<>();         // joueur -> equipe dont il est dans la zone
    private static boolean dirty = false;
    private static int saveSeconds = 0;

    private CampfireManager() {}

    // ------------------------------------------------------------ fichiers et cycle de vie

    public static void load() {
        config = KLFiles.load("campfire.json", CampfireConfig.class, CampfireConfig::new);
        if (config.msgVersion < 1) {
            // v1 : plus de bonus/malus ; seulement 3 titres (10 %, eteint, allume)
            config.messages = CampfireConfig.defaultMessages();
            config.warnBelowPercent = 10.0;
            config.msgVersion = 1;
            KLFiles.save("campfire.json", config);
        }
        if (config.msgVersion < 2) {
            // v2 : nouveau minijeu (charrue a feu, alternance gauche / droite)
            config.igniteGainPerTap = 0.04;
            config.igniteDecayPerSecond = 0.15;
            config.msgVersion = 2;
            KLFiles.save("campfire.json", config);
        }
        if (config.msgVersion < 3) {
            // v3 : frottement a la souris (balayages plus rares, donc gain plus grand)
            config.igniteGainPerTap = 0.07;
            config.igniteDecayPerSecond = 0.15;
            config.msgVersion = 3;
            KLFiles.save("campfire.json", config);
        }
        if (config.msgVersion < 4) {
            // v4 : combustible facon four (charbon = 15 %), remplissage progressif a 2 % par seconde
            if (config.fuelItems != null) {
                config.fuelItems.put("minecraft:coal", 15.0);
                config.fuelItems.put("minecraft:charcoal", 15.0);
            }
            config.msgVersion = 4;
            KLFiles.save("campfire.json", config);
        }
        if (config.msgVersion < 5) {
            // v5 : le combustible se verse a 110 % de la vitesse de baisse
            config.fillSpeedFactor = 1.1;
            config.msgVersion = 5;
            KLFiles.save("campfire.json", config);
        }
        if (config.msgVersion < 6) {
            // v6 : la jauge doit nettement monter quand un combustible brule (x4 la vitesse de baisse)
            config.fillSpeedFactor = 4.0;
            config.msgVersion = 6;
            KLFiles.save("campfire.json", config);
        }
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
        ServerBossEvent bar = bars.remove(key);
        if (bar != null) {
            bar.removeAllPlayers();
        }
        save();
        return true;
    }

    /**
     * Fusion : les feux des anciennes equipes s'eteignent pour de bon et ne sont plus suivis (plus de jauge ni de barre).
     * Les blocs restent en place, sans proprietaire : tout le monde peut les ouvrir pour recuperer ses affaires.
     * L'equipe reunie pose ensuite son nouveau feu (un campfire d'equipe, comme au depart).
     */
    public static int retireAllExcept(String keepTeam) {
        int retired = 0;
        for (Map.Entry<String, Fire> e : new ArrayList<>(store.fires.entrySet())) {
            String team = e.getKey();
            if (team.equals(keepTeam)) {
                continue;
            }
            Fire f = e.getValue();
            f.fuel = 0.0;
            f.pending = 0.0;
            if (server != null) {
                updateLit(team, f);   // le bloc s'eteint
            }
            remove(team);
            retired++;
        }
        if (retired > 0 && server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (keepTeam.equals(TeamManager.teamIdOf(p.getUUID()))) {
                    p.sendSystemMessage(Txt.of(fr.cosmicisland.Tr.s("camp.merge.retired")));
                }
            }
        }
        return retired;
    }

    public static void setFuel(String team, double value) {
        String key = team.toLowerCase();
        Fire f = store.fires.get(key);
        if (f == null) {
            return;
        }
        f.fuel = Math.max(0.0, Math.min(config.maxFuel, value));
        f.pending = 0.0;
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
        if (isTeamFire(level.getBlockState(pos))) {
            return false; // le feu du mod s'alimente par son interface
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
        if (fire.fuel <= 0.0) {
            ConseilMessages.send(config.message("needLit"), player, Map.of());
            return true;
        }
        if (fire.fuel + fire.pending >= config.maxFuel - 0.001) {
            ConseilMessages.send(config.message("full"), player, Map.of());
            return true;
        }

        double added = Math.min(points, config.maxFuel - fire.fuel - fire.pending);
        fire.pending += added;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        dirty = true;

        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                10, 0.2, 0.1, 0.2, 0.02);
        ConseilMessages.send(config.message("fed"), player, Map.of(
                "points", String.format("%.0f", added),
                "percent", String.valueOf((int) Math.round((fire.fuel + fire.pending) / Math.max(1.0, config.maxFuel) * 100.0))));
        refresh(team, fire);
        return true;
    }

    // ------------------------------------------------------------ feu de camp d'equipe (bloc du mod)

    public static ServerLevel levelOf(String dimension) {
        return server == null ? null : SpawnManager.level(server, dimension);
    }

    /** Equipe dont le feu enregistre se trouve exactement a cette position, ou null. */
    public static String teamAt(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        for (Map.Entry<String, Fire> e : store.fires.entrySet()) {
            Fire f = e.getValue();
            if (f.x == pos.getX() && f.y == pos.getY() && f.z == pos.getZ() && f.dimension.equals(dimension)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Pourcentage de la jauge du feu a cette position, ou -1 si ce n'est pas un feu enregistre. */
    public static int percentAt(ServerLevel level, BlockPos pos) {
        String team = teamAt(level, pos);
        return team == null ? -1 : percent(store.fires.get(team));
    }

    /** Points de jauge donnes par un objet combustible (bois, charbon...), ou null s'il n'en donne pas. */
    public static Double fuelPoints(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        Double points = key == null ? null : config.fuelItems.get(key.toString());
        return points == null || points <= 0.0 ? null : points;
    }

    /** Ajoute du carburant au feu enregistre a cette position (refuse si eteint ou deja plein). */
    public static boolean addFuelAt(ServerLevel level, BlockPos pos, double points) {
        String team = teamAt(level, pos);
        Fire f = team == null ? null : store.fires.get(team);
        if (f == null || f.fuel <= 0.0 || f.pending > 0.001 || f.fuel >= config.maxFuel - 0.001) {
            return false;   // facon four : on attend que l'objet precedent ait fini de bruler
        }
        f.pending += Math.min(points, config.maxFuel - f.fuel);
        dirty = true;
        refresh(team, f);
        return true;
    }

    /** Allumage reussi : la jauge demarre au niveau de depart reglable. */
    public static void ignite(String team, ServerLevel level, BlockPos pos) {
        Fire f = store.fires.get(team);
        if (f == null) {
            return;
        }
        f.fuel = Math.max(f.fuel, config.maxFuel * config.igniteStartPercent / 100.0);
        dirty = true;
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 20, 0.25, 0.15, 0.25, 0.03);
        refresh(team, f);
        save();
    }

    /**
     * Un joueur pose un feu de camp d'equipe. Retourne false si la pose doit etre refusee (l'equipe en a deja un en place).
     * Le feu devient le feu unique de l'equipe, eteint, a 0 %. Un ancien feu (vanilla) est remplace.
     */
    public static boolean onTeamFirePlaced(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!config.enabled || server == null) {
            return true;
        }
        String team = TeamManager.teamIdOf(player.getUUID());
        if (team == null) {
            return true; // staff / sans equipe : bloc libre, a enregistrer avec /ccis campfire set
        }
        Fire existing = get(team);
        if (existing != null) {
            ServerLevel old = SpawnManager.level(server, existing.dimension);
            BlockPos oldPos = new BlockPos(existing.x, existing.y, existing.z);
            if (old != null && (!old.isLoaded(oldPos) || isTeamFire(old.getBlockState(oldPos)))) {
                if (!(existing.x == pos.getX() && existing.y == pos.getY() && existing.z == pos.getZ()
                        && existing.dimension.equals(level.dimension().location().toString()))) {
                    ConseilMessages.send(config.message("alreadyHave"), player, Map.of());
                    return false;
                }
            } else if (old != null && old.isLoaded(oldPos) && isCampfireLike(old.getBlockState(oldPos))) {
                old.destroyBlock(oldPos, false);   // ancien feu vanilla remplace
            }
        }
        registerNew(team, level, pos);
        return true;
    }

    /** Enregistre un nouveau feu a 0 % (eteint) pour l'equipe. */
    public static void registerNew(String team, ServerLevel level, BlockPos pos) {
        Fire f = new Fire();
        f.dimension = level.dimension().location().toString();
        f.x = pos.getX();
        f.y = pos.getY();
        f.z = pos.getZ();
        f.fuel = 0.0;
        String key = team.toLowerCase();
        store.fires.put(key, f);
        out.add(key);          // il demarre eteint : pas de message "le feu s'est eteint"
        warned.remove(key);
        save();
        refresh(key, f);
        sendToTeam(key, "registered", 0);
    }

    // ------------------------------------------------------------ tick (1 fois par seconde)

    public static void tick() {
        if (server != null) {
            FireIgnition.tick(server);
        }
        if (server != null && config.enabled) {
            fillTick();
        }
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

    /** Combustible qui brule : la jauge se remplit un peu plus vite (x fillSpeedFactor) qu'elle ne baisse. */
    private static void fillTick() {
        double rate = config.drainPerMinute / 60.0 / 20.0 * Math.max(1.0, config.fillSpeedFactor);   // points par tick
        for (Map.Entry<String, Fire> e : store.fires.entrySet()) {
            Fire f = e.getValue();
            if (f.pending <= 0.0) {
                continue;
            }
            if (f.fuel <= 0.0) {
                f.pending = 0.0;   // le feu s'est eteint : le carburant en attente est perdu
                continue;
            }
            int before = percent(f);
            double give = Math.min(rate, f.pending);
            f.pending -= give;
            f.fuel = Math.min(config.maxFuel, f.fuel + give);
            if (f.pending < 0.0005) {
                f.pending = 0.0;
            }
            dirty = true;
            if (percent(f) != before) {
                updateBar(e.getKey(), f);
            }
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

    /** Le bloc est-il le feu de camp d'equipe du mod ? */
    /**
     * Le feu est-il sous la pluie ? Il est protege si un bloc (dalle comprise) couvre toute la zone
     * rainCoverSize x rainCoverSize centree au-dessus de lui ; sinon la pluie l'eteint.
     */
    public static boolean exposedToRain(ServerLevel level, BlockPos pos) {
        if (!level.isRaining() || !level.getBiome(pos).value().hasPrecipitation()) {
            return false;
        }
        int r = Math.max(1, config.rainCoverSize) / 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos col = pos.offset(dx, 0, dz);
                if (!level.isLoaded(col)) {
                    continue;
                }
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ());
                // le feu occupe y : la hauteur libre est y+1 s'il n'y a rien au-dessus
                if (top <= pos.getY() + 1) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isTeamFire(BlockState state) {
        return state.getBlock() instanceof TeamCampfireBlock;
    }

    /** Feu de camp vanilla ou d'un autre mod (desactive pour les joueurs). */
    public static boolean isForeignCampfire(BlockState state) {
        return !isTeamFire(state) && isCampfireLike(state);
    }

    /** Un bloc est-il un feu de camp (celui du mod, vanilla ou d'un mod : Build Your Campfire, etc.) ? */
    public static boolean isCampfireLike(BlockState state) {
        if (state.getBlock() instanceof TeamCampfireBlock) {
            return true;
        }
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
        if (isTeamFire(state)) {
            // Le bloc du mod suit la jauge : allume tant qu'il reste du carburant, eteint a 0 %
            if (config.rainExtinguish && f.fuel > 0.0 && exposedToRain(level, pos)) {
                f.fuel = 0.0;
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.25, 0.2, 0.25, 0.02);
            }
            boolean burning = f.fuel > 0.0;
            if (state.getValue(BlockStateProperties.LIT) != burning) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, burning), 3);
            }
            if (burning) {
                wasLit.add(team);
                unlit.remove(team);
            } else {
                wasLit.remove(team);
                unlit.add(team);
            }
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
            // Un autre mod (torches realistes, feu qui se consume...) a eteint le feu sans pluie ni eau, alors
            // qu'il reste du carburant : on le rallume. Seuls la pluie, l'eau et la jauge a 0 doivent l'eteindre.
            if (!lit && wasLit.contains(team) && f.fuel > 0.0 && config.protectFromOtherMods) {
                boolean wet = state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && state.getValue(BlockStateProperties.WATERLOGGED);
                boolean rain = level.isRainingAt(pos.above());
                if (!wet && !rain) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 3);
                    lit = true;
                }
            }
            if (lit) {
                wasLit.add(team);
            } else {
                wasLit.remove(team);
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

    // ------------------------------------------------------------ pose / fabrication par les joueurs

    /** Un joueur pose un feu de camp : s'il est dans une equipe sans feu valide, ce feu devient celui de l'equipe. */
    public static void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!config.enabled || !config.autoRegister || server == null || !isCampfireLike(state) || isTeamFire(state)) {
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

    /** Le joueur est-il assez pres du feu (meme dimension, rayon barRadius a plat) pour voir la barre ? */
    private static boolean isNearFire(ServerPlayer p, Fire f) {
        if (config.barRadius <= 0.0) {
            return true;
        }
        if (!p.level().dimension().location().toString().equals(f.dimension)) {
            return false;
        }
        double dx = p.getX() - (f.x + 0.5);
        double dz = p.getZ() - (f.z + 0.5);
        return dx * dx + dz * dz <= config.barRadius * config.barRadius;
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
        TeamManager.KLTeam kt = TeamManager.get(team);
        ChatFormatting tc = kt == null ? ChatFormatting.GOLD : kt.format();
        bar.setName(Txt.of("&" + tc.getChar() + "&l" + fr.cosmicisland.Tr.s("camp.bar.title") + " &7- &f" + pct + "%"
                + (pct <= config.warnBelowPercent ? " &c\u26A0" : "")
                + (dark ? " &c" + fr.cosmicisland.Tr.s("camp.bar.unlit") : "")));
        bar.setProgress((float) Math.max(0.0, Math.min(1.0, f.fuel / Math.max(1.0, config.maxFuel))));
        bar.setColor(barColor(tc));
        // Seuls les membres de l'equipe voient la barre de LEUR feu, et seulement a proximite
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (team.equals(TeamManager.teamIdOf(p.getUUID())) && isNearFire(p, f)) {
                bar.addPlayer(p);
            } else {
                bar.removePlayer(p);
            }
        }
    }

    /** Couleur de barre la plus proche de la couleur de l'equipe (le jeu n'en propose que 7). */
    private static BossEvent.BossBarColor barColor(ChatFormatting c) {
        return switch (c) {
            case RED, DARK_RED -> BossEvent.BossBarColor.RED;
            case BLUE, DARK_BLUE, AQUA, DARK_AQUA -> BossEvent.BossBarColor.BLUE;
            case GREEN, DARK_GREEN -> BossEvent.BossBarColor.GREEN;
            case YELLOW, GOLD -> BossEvent.BossBarColor.YELLOW;
            case LIGHT_PURPLE -> BossEvent.BossBarColor.PINK;
            case DARK_PURPLE -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.WHITE;
        };
    }

    // ------------------------------------------------------------ affichage

    public static List<Component> statusLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Txt.lit("=== Feux de camp ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!config.enabled) {
            lines.add(Txt.lit("Désactivés dans campfire.json").withStyle(ChatFormatting.RED));
            return lines;
        }
        for (Map.Entry<String, TeamManager.KLTeam> e : TeamManager.all().entrySet()) {
            Fire f = store.fires.get(e.getKey());
            Component value;
            if (f == null) {
                value = Txt.lit("aucun feu défini").withStyle(ChatFormatting.RED);
            } else {
                String text = String.format("%.1f/%.0f (%d%%)  %d %d %d", f.fuel, config.maxFuel, percent(f), f.x, f.y, f.z)
                        + (unlit.contains(e.getKey()) && f.fuel > 0.0 ? "  ÉTEINT (à rallumer)" : "")
                        + (isOut(e.getKey()) ? "  ÉTEINT" : "");
                value = Txt.lit(text).withStyle(isOut(e.getKey()) ? ChatFormatting.RED : ChatFormatting.GREEN);
            }
            lines.add(Component.empty()
                    .append(Txt.lit(e.getValue().name + " : ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
                    .append(value));
        }
        return lines;
    }
}
