package com.geostrata.experiment;

import com.geostrata.GeoStrata;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.List;

/** Explicit opt-in companion for rare ore/diamond experiments and debug tools. */
public final class CorrelatedExperimentCompanion implements ModInitializer {
    private static final String BENCHMARK_SUPPRESS_DIAMOND_ENV = "GEOSTRATA_BENCHMARK_SUPPRESS_VANILLA_DIAMOND";
    private static final List<RegistryKey<PlacedFeature>> BENCHMARK_DIAMOND_ORES = List.of(
            OrePlacedFeatures.ORE_DIAMOND,
            OrePlacedFeatures.ORE_DIAMOND_LARGE,
            OrePlacedFeatures.ORE_DIAMOND_BURIED
    );

    @Override
    public void onInitialize() {
        boolean benchmarkSuppressVanillaDiamond = Boolean.parseBoolean(System.getenv(BENCHMARK_SUPPRESS_DIAMOND_ENV));
        if (benchmarkSuppressVanillaDiamond) {
            BiomeModifications.create(GeoStrata.id("experimental_diamond_benchmark_ownership"))
                    .add(
                            ModificationPhase.REMOVALS,
                            BiomeSelectors.foundInOverworld(),
                            context -> BENCHMARK_DIAMOND_ORES.forEach(feature ->
                                    context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            feature
                                    ))
                    );
        }
        OreDebugCommands.register();
        GeoStrata.LOGGER.info("GeoStrata experiment companion enabled; rare ore/diamond experiments and debug tools active");
        if (benchmarkSuppressVanillaDiamond) {
            GeoStrata.LOGGER.info("GeoStrata benchmark mode: vanilla diamond generation suppressed for attribution");
        }
    }
}
