package com.geostrata.experiment;

import com.geostrata.GeoStrata;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.List;

/** Explicit opt-in companion that makes GeoStrata's experimental worldgen reachable. */
public final class CorrelatedExperimentCompanion implements ModInitializer {
    private static final TagKey<Biome> REGISTRATION_BIOMES = TagKey.of(
            RegistryKeys.BIOME,
            GeoStrata.id("has_common_rocks")
    );
    private static final RegistryKey<PlacedFeature> CORRELATED_FEATURE = geostrataFeature("correlated_sedimentary_experiment");
    private static final RegistryKey<PlacedFeature> BACKGROUND_FEATURE = geostrataFeature("province_background_experiment");
    private static final List<RegistryKey<PlacedFeature>> REPLACED_VANILLA_OVERWORLD_ORES = List.of(
            OrePlacedFeatures.ORE_COAL_UPPER,
            OrePlacedFeatures.ORE_COAL_LOWER,
            OrePlacedFeatures.ORE_IRON_UPPER,
            OrePlacedFeatures.ORE_IRON_MIDDLE,
            OrePlacedFeatures.ORE_IRON_SMALL,
            OrePlacedFeatures.ORE_COPPER,
            OrePlacedFeatures.ORE_COPPER_LARGE,
            OrePlacedFeatures.ORE_GOLD_EXTRA,
            OrePlacedFeatures.ORE_GOLD,
            OrePlacedFeatures.ORE_GOLD_LOWER,
            OrePlacedFeatures.ORE_EMERALD,
            OrePlacedFeatures.ORE_DIAMOND,
            OrePlacedFeatures.ORE_DIAMOND_LARGE,
            OrePlacedFeatures.ORE_DIAMOND_BURIED
    );
    private static final List<RegistryKey<PlacedFeature>> REPLACED_GEOSTRATA_FALLBACK_ROCKS = List.of(
            geostrataFeature("limestone_ore"),
            geostrataFeature("shale_ore"),
            geostrataFeature("mudstone_ore"),
            geostrataFeature("basalt_ore"),
            geostrataFeature("chalk_ore"),
            geostrataFeature("siltstone_ore"),
            geostrataFeature("conglomerate_ore"),
            geostrataFeature("slate_ore"),
            geostrataFeature("marble_ore"),
            geostrataFeature("quartzite_ore"),
            geostrataFeature("schist_ore"),
            geostrataFeature("gneiss_ore"),
            geostrataFeature("rhyolite_ore"),
            geostrataFeature("breccia_ore")
    );

    @Override
    public void onInitialize() {
        BiomeModifications.create(GeoStrata.id("experimental_worldgen_ownership"))
                .add(
                        ModificationPhase.REMOVALS,
                        BiomeSelectors.foundInOverworld(),
                        context -> {
                            REPLACED_VANILLA_OVERWORLD_ORES.forEach(feature ->
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            feature
                                    ));
                            REPLACED_GEOSTRATA_FALLBACK_ROCKS.forEach(feature ->
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            feature
                                    ));
                        }
                );
        BiomeModifications.addFeature(
                BiomeSelectors.tag(REGISTRATION_BIOMES),
                GenerationStep.Feature.TOP_LAYER_MODIFICATION,
                CORRELATED_FEATURE
        );
        BiomeModifications.addFeature(
                BiomeSelectors.tag(REGISTRATION_BIOMES),
                GenerationStep.Feature.TOP_LAYER_MODIFICATION,
                BACKGROUND_FEATURE
        );
        OreDebugCommands.register();
        GeoStrata.LOGGER.info(
                "GeoStrata experimental worldgen companion enabled; authoritative geology and owned overworld ores replace fallback rock blobs and native ore generation"
        );
    }

    private static RegistryKey<PlacedFeature> geostrataFeature(String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, GeoStrata.id(path));
    }
}
