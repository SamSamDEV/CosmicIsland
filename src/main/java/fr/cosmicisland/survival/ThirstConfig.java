package fr.cosmicisland.survival;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reglages de la soif : config/cosmicisland/thirst.json */
public class ThirstConfig {

    /** Une boisson ou un aliment qui modifie la soif (hydration negative = deshydrate). */
    public static class Drink {
        public int hydration = 2;
        public float saturation = 0.5f;
        public double effectChance = 0.0;   // probabilite d'appliquer la Deshydratation
        public int effectSeconds = 15;

        public Drink() {}

        public Drink(int hydration, float saturation, double effectChance, int effectSeconds) {
            this.hydration = hydration;
            this.saturation = saturation;
            this.effectChance = effectChance;
            this.effectSeconds = effectSeconds;
        }
    }

    public boolean enabled = true;
    public String appliesTo = "participants";   // participants | everyone (everyone = pratique pour tester seul)
    public int maxHydration = 20;
    public double exhaustionPerPoint = 4.0;     // epuisement necessaire pour perdre 1 goutte
    public double idlePerSecond = 0.02;         // usure naturelle
    public double sprintPerSecond = 0.9;        // en sprint
    public double jumpAmount = 0.05;
    public double sprintJumpAmount = 0.2;
    public double miningAmount = 0.01;
    public double attackAmount = 0.1;
    public double hotBiomeMultiplier = 1.5;     // biome chaud (temperature >= hotTemperature) en plein jour a l'air libre
    public double hotTemperature = 1.0;
    public double dehydrationMultiplier = 3.0;  // pendant l'effet Deshydratation
    public int noSprintBelow = 6;               // plus de sprint a ce niveau ou moins (-1 = jamais)
    public int damageIntervalTicks = 80;        // degats toutes les 4 s a 0 goutte
    public double damageAmount = 1.0;
    public double damageMinHealth = 2.0;        // les degats s'arretent a cette vie (0 = peut tuer)
    public boolean resetOnRespawn = true;

    // Boire directement dans l'eau (main vide, accroupi, clic droit)
    public boolean drinkFromWater = true;
    public boolean drinkRequiresSneak = true;
    public int waterHydration = 1;
    public float waterSaturation = 0.0f;
    public double waterEffectChance = 0.5;
    public int waterEffectSeconds = 15;

    // Gourde du mod
    public int gourdeSips = 10;
    public int gourdeHydration = 1;
    public float gourdeSaturation = 0.0f;
    public double gourdeEffectChance = 0.5;
    public int gourdeEffectSeconds = 15;
    public int gourdePurifiedHydration = 6;   // 6 = 3 gouttes (une goutte = 2 points)
    public int configVersion = 0;
    public float gourdePurifiedSaturation = 1.0f;

    // Riz cru : retire de la soif (les valeurs de faim sont fixees dans le code : cru 1 / cuit 2)
    public int rawRiceHydration = -2;

    /** Sac de riz pose : true = remplissage de marmite illimite ; false = 4 remplissages par sac. */
    public boolean riceSackInfinite = false;

    /**
     * Regles par TAG d'objet (s'appliquent aux aliments des autres mods qui portent ce tag, quand l'objet n'a pas sa propre regle).
     * Les mods de cuisine et de peche (Farmer's Delight, etc.) utilisent les tags forge:raw_fishes, forge:fruits...
     */
    public Map<String, Drink> tags = defaultTags();

    private static Map<String, Drink> defaultTags() {
        Map<String, Drink> m = new LinkedHashMap<>();
        m.put("forge:raw_fishes", new Drink(-1, 0f, 0.35, 15));
        m.put("forge:raw_meats", new Drink(-1, 0f, 0.35, 15));
        m.put("forge:fruits", new Drink(2, 0.5f, 0, 0));
        m.put("forge:berries", new Drink(2, 0.5f, 0, 0));
        m.put("forge:vegetables", new Drink(1, 0.3f, 0, 0));
        return m;
    }

    /** Autres aliments et boissons (identifiant d'objet -> effet sur la soif). Valeurs inspirees de Legendary Survival Overhaul. */
    public Map<String, Drink> drinks = defaultDrinks();

    private static Map<String, Drink> defaultDrinks() {
        Map<String, Drink> m = new LinkedHashMap<>();
        m.put("minecraft:water_bottle", new Drink(3, 0f, 0.75, 15));
        m.put("minecraft:milk_bucket", new Drink(5, 1f, 0, 0));
        m.put("minecraft:apple", new Drink(2, 0.5f, 0, 0));
        m.put("minecraft:glow_berries", new Drink(2, 0.5f, 0, 0));
        m.put("minecraft:sweet_berries", new Drink(2, 0.5f, 0, 0));
        m.put("minecraft:melon_slice", new Drink(2, 1f, 0, 0));
        m.put("minecraft:rotten_flesh", new Drink(-4, 0f, 1.0, 15));
        m.put("minecraft:spider_eye", new Drink(-4, 0f, 1.0, 15));
        m.put("legendarysurvivaloverhaul:purified_water_bottle", new Drink(6, 1.5f, 0, 0));
        m.put("legendarysurvivaloverhaul:water_plant_bag", new Drink(3, 0f, 0, 0));
        m.put("legendarysurvivaloverhaul:apple_juice", new Drink(6, 3f, 0, 0));
        m.put("legendarysurvivaloverhaul:beetroot_juice", new Drink(8, 4f, 0, 0));
        m.put("legendarysurvivaloverhaul:cactus_juice", new Drink(9, 3f, 0, 0));
        m.put("legendarysurvivaloverhaul:carrot_juice", new Drink(4, 2f, 0, 0));
        m.put("legendarysurvivaloverhaul:melon_juice", new Drink(7, 4f, 0, 0));
        m.put("legendarysurvivaloverhaul:pumpkin_juice", new Drink(6, 3.5f, 0, 0));
        m.put("legendarysurvivaloverhaul:sweet_berries_juice", new Drink(9, 5f, 0, 0));
        m.put("legendarysurvivaloverhaul:glow_berries_juice", new Drink(4, 6f, 0, 0));
        m.put("legendarysurvivaloverhaul:golden_apple_juice", new Drink(10, 12f, 0, 0));
        m.put("legendarysurvivaloverhaul:golden_carrot_juice", new Drink(8, 6f, 0, 0));
        m.put("legendarysurvivaloverhaul:glistering_melon_juice", new Drink(6, 6f, 0, 0));
        m.put("legendarysurvivaloverhaul:chorus_fruit_juice", new Drink(12, 14f, 0, 0));
        return m;
    }
}
