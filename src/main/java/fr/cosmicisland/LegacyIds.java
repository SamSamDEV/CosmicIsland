package fr.cosmicisland;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;

import java.util.Map;

/** Anciens identifiants francais -> identifiants anglais (les objets et blocs deja presents dans un monde sont conserves). */
@Mod.EventBusSubscriber(modid = CosmicIsland.MODID)
public final class LegacyIds {
    private static final Map<String, String> RENAMED = Map.ofEntries(
            Map.entry("marmite", "cooking_pot"), Map.entry("marmite_posee", "placed_pot"), Map.entry("gourde", "canteen"),
            Map.entry("sac_de_riz", "raw_rice_bag"), Map.entry("coquillage_de_riz", "rice_shell"),
            Map.entry("allume_feu", "lighter"), Map.entry("feu_de_camp", "team_campfire"),
            Map.entry("collier", "exemption_bracelet"), Map.entry("bandeau", "headband"), Map.entry("brassard", "armband"),
            Map.entry("poteau", "pole"), Map.entry("totem", "immunity_globe"));

    private LegacyIds() {}

    @SubscribeEvent
    public static void onMissing(MissingMappingsEvent event) {
        remap(event, ForgeRegistries.Keys.ITEMS, ForgeRegistries.ITEMS);
        remap(event, ForgeRegistries.Keys.BLOCKS, ForgeRegistries.BLOCKS);
    }

    private static <T> void remap(MissingMappingsEvent event, ResourceKey<? extends Registry<T>> key, IForgeRegistry<T> registry) {
        for (MissingMappingsEvent.Mapping<T> mapping : event.getMappings(key, CosmicIsland.MODID)) {
            String renamed = RENAMED.get(mapping.getKey().getPath());
            if (renamed != null) {
                T target = registry.getValue(new ResourceLocation(CosmicIsland.MODID, renamed));
                if (target != null) {
                    mapping.remap(target);
                }
            }
        }
    }
}
