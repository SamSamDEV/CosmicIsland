package fr.cosmicisland.survival;

import fr.cosmicisland.KLFiles;
import fr.cosmicisland.KLRole;
import fr.cosmicisland.RoleManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Soif du mod : jauge 0-20 par joueur, usure, degats a sec, boissons. Les donnees sont dans les donnees persistantes du joueur. */
public final class ThirstManager {
    private static final String TAG = "cosmicisland_thirst";
    private static final Random RANDOM = new Random();

    private static ThirstConfig config = new ThirstConfig();
    private static final Map<UUID, Integer> lastDrinkTick = new HashMap<>();
    private static final Map<UUID, Boolean> hot = new HashMap<>();
    private static final Map<UUID, int[]> lastSent = new HashMap<>();   // niveau, saturation*10, actif

    private ThirstManager() {}

    public static void load() {
        config = KLFiles.load("thirst.json", ThirstConfig.class, ThirstConfig::new);
        if (config.drinks == null) {
            config.drinks = new ThirstConfig().drinks;
        }
        if (config.tags == null) {
            config.tags = new ThirstConfig().tags;
        }
        if (config.configVersion < 1) {
            // v1 : la gorgee de gourde purifiee passe de 1,5 a 3 gouttes
            if (config.gourdePurifiedHydration <= 3) {
                config.gourdePurifiedHydration = 6;
            }
            config.configVersion = 1;
            KLFiles.save("thirst.json", config);
        }
    }

    public static ThirstConfig config() {
        return config;
    }

    public static void setEnabled(boolean on) {
        config.enabled = on;
        KLFiles.save("thirst.json", config);
    }

    // ------------------------------------------------------------ donnees du joueur

    private static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) {
            CompoundTag t = new CompoundTag();
            t.putInt("level", config.maxHydration);
            t.putFloat("sat", 5.0f);
            t.putFloat("exh", 0f);
            t.putInt("dmg", 0);
            root.put(TAG, t);
        }
        return root.getCompound(TAG);
    }

    public static int level(ServerPlayer p) {
        return data(p).getInt("level");
    }

    public static void setLevel(ServerPlayer p, int value) {
        CompoundTag t = data(p);
        t.putInt("level", Math.max(0, Math.min(config.maxHydration, value)));
        t.putFloat("sat", Math.min(t.getFloat("sat"), t.getInt("level")));
        sync(p, true);
    }

    public static boolean applies(ServerPlayer p) {
        if (!config.enabled || p.isCreative() || p.isSpectator()) {
            return false;
        }
        return "everyone".equalsIgnoreCase(config.appliesTo) || RoleManager.effective(p) == KLRole.PARTICIPANT;
    }

    public static boolean canDrinkNow(Player p) {
        return true;
    }

    // ------------------------------------------------------------ boire

    /** Applique une boisson (hydration negative = deshydrate). L'effet Deshydratation est tire avec effectChance. */
    public static void drink(ServerPlayer p, int hydration, float saturation, double effectChance, int effectSeconds) {
        CompoundTag t = data(p);
        int level = Math.max(0, Math.min(config.maxHydration, t.getInt("level") + hydration));
        t.putInt("level", level);
        t.putFloat("sat", Math.max(0f, Math.min(level, t.getFloat("sat") + saturation)));
        if (effectChance > 0 && RANDOM.nextDouble() < effectChance && effectSeconds > 0) {
            p.addEffect(new MobEffectInstance(SurvivalRegistry.DEHYDRATION.get(), effectSeconds * 20, 0));
        }
        sync(p, true);
    }

    /** Boire directement dans l'eau (paquet du client : main vide, regard sur de l'eau). */
    public static void drinkFromWater(ServerPlayer p) {
        if (!config.drinkFromWater || !p.getMainHandItem().isEmpty()) {
            return;
        }
        if (config.drinkRequiresSneak && !p.isShiftKeyDown()) {
            return;
        }
        int now = p.tickCount;
        Integer last = lastDrinkTick.get(p.getUUID());
        if (last != null && now - last < 15) {
            return;
        }
        HitResult hit = p.pick(4.5, 1.0f, true);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult bhr)
                || !p.level().getFluidState(bhr.getBlockPos()).is(FluidTags.WATER)) {
            return;
        }
        lastDrinkTick.put(p.getUUID(), now);
        drink(p, config.waterHydration, config.waterSaturation, config.waterEffectChance, config.waterEffectSeconds);
        p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.7f, 1.0f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    /** Aliment ou boisson de la table (config) consomme jusqu'au bout. */
    public static void onConsumed(ServerPlayer p, ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || key.getNamespace().equals("cosmicisland")) {
            return;
        }
        String id = key.toString();
        if (id.equals("minecraft:potion") && PotionUtils.getPotion(stack) == Potions.WATER) {
            id = "minecraft:water_bottle";
        }
        ThirstConfig.Drink d = rule(stack);
        if (d != null) {
            drink(p, d.hydration, d.saturation, d.effectChance, d.effectSeconds);
        }
    }

    /** Regle d'un objet : sa propre ligne (identifiant) d'abord, sinon la premiere regle de tag qui correspond. */
    public static ThirstConfig.Drink rule(ItemStack stack) {
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return null;
        }
        String id = key.toString();
        if (id.equals("minecraft:potion") && PotionUtils.getPotion(stack) == Potions.WATER) {
            id = "minecraft:water_bottle";
        }
        ThirstConfig.Drink d = config.drinks.get(id);
        if (d != null) {
            return d;
        }
        for (Map.Entry<String, ThirstConfig.Drink> e : config.tags.entrySet()) {
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(e.getKey());
            if (loc != null && stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, loc))) {
                return e.getValue();
            }
        }
        return null;
    }

    public static void saveConfig() {
        KLFiles.save("thirst.json", config);
    }

    // ------------------------------------------------------------ usure

    public static void addExhaustion(ServerPlayer p, double amount) {
        if (!applies(p)) {
            return;
        }
        CompoundTag t = data(p);
        t.putFloat("exh", t.getFloat("exh") + (float) (amount * multiplier(p)));
    }

    private static double multiplier(ServerPlayer p) {
        double m = 1.0;
        if (Boolean.TRUE.equals(hot.get(p.getUUID()))) {
            m *= config.hotBiomeMultiplier;
        }
        if (p.hasEffect(SurvivalRegistry.DEHYDRATION.get())) {
            m *= config.dehydrationMultiplier;
        }
        return m;
    }

    public static void tick(ServerPlayer p) {
        if (p.tickCount % 20 == 0) {
            boolean isHot = p.level().getBiome(p.blockPosition()).value().getBaseTemperature() >= config.hotTemperature
                    && p.level().isDay() && p.level().canSeeSky(p.blockPosition());
            hot.put(p.getUUID(), isHot);
        }
        if (!applies(p)) {
            sync(p, false);
            return;
        }
        CompoundTag t = data(p);
        double amount = config.idlePerSecond / 20.0;
        if (p.isSprinting()) {
            amount += config.sprintPerSecond / 20.0;
        }
        t.putFloat("exh", t.getFloat("exh") + (float) (amount * multiplier(p)));

        while (t.getFloat("exh") >= config.exhaustionPerPoint) {
            t.putFloat("exh", (float) (t.getFloat("exh") - config.exhaustionPerPoint));
            if (t.getFloat("sat") > 0f) {
                t.putFloat("sat", Math.max(0f, t.getFloat("sat") - 1f));
            } else {
                t.putInt("level", Math.max(0, t.getInt("level") - 1));
            }
        }

        int level = t.getInt("level");
        if (config.noSprintBelow >= 0 && level <= config.noSprintBelow && p.isSprinting()) {
            p.setSprinting(false);
        }
        if (level <= 0) {
            int dmg = t.getInt("dmg") + 1;
            if (dmg >= config.damageIntervalTicks) {
                dmg = 0;
                if (p.getHealth() > config.damageMinHealth) {
                    p.hurt(p.damageSources().starve(), (float) config.damageAmount);
                }
            }
            t.putInt("dmg", dmg);
        } else {
            t.putInt("dmg", 0);
        }
        sync(p, false);
    }

    public static void resetFull(ServerPlayer p) {
        CompoundTag t = data(p);
        t.putInt("level", config.maxHydration);
        t.putFloat("sat", 5.0f);
        t.putFloat("exh", 0f);
        t.putInt("dmg", 0);
        sync(p, true);
    }

    // ------------------------------------------------------------ synchronisation client

    public static void sync(ServerPlayer p, boolean force) {
        boolean active = applies(p);
        CompoundTag t = data(p);
        int[] now = {t.getInt("level"), Math.round(t.getFloat("sat") * 10f), active ? 1 : 0};
        int[] before = lastSent.get(p.getUUID());
        if (!force && before != null && before[0] == now[0] && before[1] == now[1] && before[2] == now[2]) {
            return;
        }
        lastSent.put(p.getUUID(), now);
        ThirstNetwork.toPlayer(p, new ThirstNetwork.ThirstPacket(active, now[0], config.maxHydration));
    }

    public static void forget(UUID id) {
        lastSent.remove(id);
        hot.remove(id);
        lastDrinkTick.remove(id);
    }

    public static String status(ServerPlayer p) {
        CompoundTag t = data(p);
        return p.getGameProfile().getName() + " : " + t.getInt("level") + "/" + config.maxHydration
                + " (saturation " + String.format("%.1f", t.getFloat("sat")) + ")"
                + (applies(p) ? "" : " — non concerné (" + config.appliesTo + ")");
    }
}
