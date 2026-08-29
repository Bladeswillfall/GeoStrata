package com.geostrata.experiment;

import com.geostrata.GeoStrata;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/** Explicit opt-in companion that makes GeoStrata's experimental worldgen reachable. */
public final class CorrelatedExperimentCompanion implements ModInitializer {
    private static final TagKey<Biome> REGISTRATION_BIOMES = TagKey.of(
            RegistryKeys.BIOME,
            GeoStrata.id("has_common_rocks")
    );
    private static final RegistryKey<PlacedFeature> CORRELATED_FEATURE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            GeoStrata.id("correlated_sedimentary_experiment")
    );

    @Override
    public void onInitialize() {
        BiomeModifications.addFeature(
                BiomeSelectors.tag(REGISTRATION_BIOMES),
                GenerationStep.Feature.UNDERGROUND_DECORATION,
                CORRELATED_FEATURE
        );
        GeoStrata.LOGGER.info("GeoStrata experimental worldgen companion enabled");
    }
}
