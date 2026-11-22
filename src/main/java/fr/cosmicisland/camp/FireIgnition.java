package fr.cosmicisland.camp;

import fr.cosmicisland.KLPermissions;
import fr.cosmicisland.TeamManager;
import fr.cosmicisland.conseil.ConseilMessages;
import fr.cosmicisland.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minijeu d'allumage (charrue a feu) : clic droit avec l'allume-feu sur le feu eteint ouvre un ecran ou le joueur
 * frotte le baton sur la planche avec la souris (clic gauche maintenu, va-et-vient), a bon rythme.
 * La jauge est partagee entre les equipiers autour du meme feu ; elle redescend toute seule si on arrete de taper.
 * Quand elle est pleine, le feu s'allume (niveau de depart reglable dans campfire.json).
 */
public final class FireIgnition {
    private static final class Target {
        final String dim;
        final BlockPos pos;

        Target(String dim, BlockPos pos) {
            this.dim = dim;
            this.pos = pos;
        }

        String key() {
            return dim + "@" + pos.asLong();
        }
    }

    private static final Map<UUID, Target> active = new HashMap<>();
    private static final Map<String, Double> progress = new HashMap<>();
    private static final Map<UUID, Long> lastTap = new HashMap<>();

    private FireIgnition() {}

    /** Debut de l'allumage (clic droit avec l'allume-feu sur un feu eteint). */
    public static boolean begin(ServerPlayer player, ServerLevel level, BlockPos pos, InteractionHand hand) {
        CampfireConfig cfg = CampfireManager.config();
        if (!cfg.enabled || GameManager.isInGame(player.getUUID())) {
            return false;
        }
        String team = CampfireManager.teamAt(level, pos);
        if (team == null) {
            ConseilMessages.send(cfg.message("noTeamFire"), player, Map.of());
            return false;
        }
        boolean staff = KLPermissions.levelOf(player) >= KLPermissions.ORGANISATEUR;
        if (!staff && !team.equals(TeamManager.teamIdOf(player.getUUID()))) {
            ConseilMessages.send(cfg.message("notYours"), player, Map.of());
            return false;
        }
        active.put(player.getUUID(), new Target(level.dimension().location().toString(), pos.immutable()));
        progress.putIfAbsent(active.get(player.getUUID()).key(), 0.0);
        FireNetwork.toPlayer(player, new FireNetwork.OpenPacket());
        return true;
    }

    private static boolean valid(ServerPlayer p, Target t) {
        if (!(p.getMainHandItem().getItem() instanceof LighterItem) && !(p.getOffhandItem().getItem() instanceof LighterItem)) {
            return false;
        }
        if (!p.level().dimension().location().toString().equals(t.dim)) {
            return false;
        }
        double range = CampfireManager.config().igniteRange;
        if (p.distanceToSqr(t.pos.getX() + 0.5, t.pos.getY() + 0.5, t.pos.getZ() + 0.5) > range * range) {
            return false;
        }
        BlockState state = p.level().getBlockState(t.pos);
        return state.getBlock() instanceof TeamCampfireBlock && !state.getValue(TeamCampfireBlock.LIT);
    }

    private static final Map<UUID, Integer> lastSide = new HashMap<>();
    private static final Map<UUID, Long> lastStroke = new HashMap<>();
    private static final Map<UUID, Integer> combo = new HashMap<>();

    /**
     * Un coup de frottement (charrue a feu) : il faut alterner gauche / droite, a bon rythme.
     * Bon rythme (3 a 9 ticks entre deux coups) = gain complet, avec un bonus de serie ; trop lent = gain reduit
     * et la serie retombe ; trop vite (spam) = ignore.
     */
    public static void stroke(ServerPlayer player, int side) {
        if (side == 2) {   // l'ecran a ete ferme
            active.remove(player.getUUID());
            lastSide.remove(player.getUUID());
            combo.remove(player.getUUID());
            return;
        }
        Target t = active.get(player.getUUID());
        if (t == null || !valid(player, t)) {
            return;
        }
        UUID id = player.getUUID();
        long now = player.serverLevel().getGameTime();
        Integer prevSide = lastSide.get(id);
        Long last = lastStroke.get(id);
        if (prevSide != null && prevSide == side && last != null && now - last < 30) {
            return;   // il faut alterner
        }
        long interval = last == null ? 99 : now - last;
        if (interval <= 3) {
            return;   // trop vite
        }
        lastSide.put(id, side);
        lastStroke.put(id, now);
        double gain = CampfireManager.config().igniteGainPerTap;
        int c = combo.getOrDefault(id, 0);
        if (interval <= 12) {
            c = Math.min(10, c + 1);
            gain *= 1.0 + 0.04 * c;
        } else if (interval <= 22) {
            c = 0;
            gain *= 0.6;
        } else {
            c = 0;
            gain *= 0.3;
        }
        combo.put(id, c);
        double v = progress.merge(t.key(), gain, Double::sum);
        effects(player, t, Math.min(1.0, v), side);
        if (v >= 1.0) {
            complete(player, t);
        }
    }

    /** Bras, son et particules : fumee, puis braises, puis etincelles. */
    private static void effects(ServerPlayer player, Target t, double v, int side) {
        ServerLevel level = player.serverLevel();
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, t.pos, net.minecraft.sounds.SoundEvents.GRINDSTONE_USE, net.minecraft.sounds.SoundSource.BLOCKS,
                0.18f, 1.5f + (float) v * 0.6f + (side == 0 ? 0f : 0.1f));
        double x = t.pos.getX() + 0.5, y = t.pos.getY() + 0.35, z = t.pos.getZ() + 0.5;
        if (v > 0.12) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y + 0.1, z, 1 + (int) (v * 4), 0.12, 0.05, 0.12, 0.01);
        }
        if (v > 0.45) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.2, z, 1, 0.05, 0.02, 0.05, 0.01);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, x, y, z, 1, 0.15, 0.02, 0.15, 0.0);
        }
        if (v > 0.75) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, x, y + 0.15, z, 2, 0.1, 0.03, 0.1, 0.01);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, x, y + 0.1, z, 2, 0.15, 0.03, 0.15, 0.05);
        }
    }

    private static void complete(ServerPlayer finisher, Target t) {
        progress.remove(t.key());
        ServerLevel level = CampfireManager.levelOf(t.dim);
        if (level != null) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, t.pos.getX() + 0.5, t.pos.getY() + 0.5, t.pos.getZ() + 0.5,
                    24, 0.2, 0.15, 0.2, 0.03);
        }
        // l'allume-feu du dernier joueur s'use
        InteractionHand hand = finisher.getMainHandItem().getItem() instanceof LighterItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = finisher.getItemInHand(hand);
        if (stack.getItem() instanceof LighterItem) {
            stack.hurtAndBreak(1, finisher, p -> p.broadcastBreakEvent(hand));
        }
        MinecraftServer server = finisher.getServer();
        Set<UUID> users = new HashSet<>();
        for (Map.Entry<UUID, Target> e : active.entrySet()) {
            if (e.getValue().key().equals(t.key())) {
                users.add(e.getKey());
            }
        }
        for (UUID id : users) {
            active.remove(id);
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
            if (p != null) {
                FireNetwork.toPlayer(p, new FireNetwork.ProgressPacket(-1f));
            }
        }
        if (level != null) {
            String team = CampfireManager.teamAt(level, t.pos);
            if (team != null) {
                CampfireManager.ignite(team, level, t.pos);
            }
        }
    }

    /** Appele a chaque tick serveur : validite des joueurs, retombee de la jauge, envoi de la progression. */
    public static void tick(MinecraftServer server) {
        if (active.isEmpty() && progress.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Target>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Target> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (!valid(p, e.getValue())) {
                lastSide.remove(e.getKey());
                combo.remove(e.getKey());
                FireNetwork.toPlayer(p, new FireNetwork.ProgressPacket(-1f));
                it.remove();
            }
        }
        double decay = CampfireManager.config().igniteDecayPerSecond / 20.0;
        Iterator<Map.Entry<String, Double>> pit = progress.entrySet().iterator();
        while (pit.hasNext()) {
            Map.Entry<String, Double> e = pit.next();
            double v = e.getValue() - decay;
            if (v <= 0.0) {
                pit.remove();
            } else {
                e.setValue(v);
            }
        }
        if (server.getTickCount() % 2 == 0) {
            for (Map.Entry<UUID, Target> e : active.entrySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    float v = progress.getOrDefault(e.getValue().key(), 0.0).floatValue();
                    FireNetwork.toPlayer(p, new FireNetwork.ProgressPacket(v));
                }
            }
        }
    }
}
