package com.geostrata.experiment;

import com.geostrata.GeoStrata;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.OrePlacedFeatures;

import java.util.List;

/** Optional debug companion; ore and diamond geology now run in the core mod. */
public final class CorrelatedExperimentCompanion implements ModInitializer {
    private static final String BENCHMARK_SUPPRESS_DIAMOND_ENV = "GEOSTRATA_BENCHMARK_SUPPRESS_VANILLA_DIAMOND";

    @Override
    public void onInitialize() {
        boolean benchmarkSuppressVanillaDiamond = Boolean.parseBoolean(System.getenv(BENCHMARK_SUPPRESS_DIAMOND_ENV));
        if (benchmarkSuppressVanillaDiamond) {
            BiomeModifications.create(GeoStrata.id("benchmark_diamond_worldgen_ownership"))
                    .add(
                            ModificationPhase.REMOVALS,
                            BiomeSelectors.foundInOverworld(),
                            context -> List.of(
                                            OrePlacedFeatures.ORE_DIAMOND,
                                            OrePlacedFeatures.ORE_DIAMOND_LARGE,
                                            OrePlacedFeatures.ORE_DIAMOND_BURIED
                                    ).forEach(feature -> context.getGenerationSettings().removeFeature(
                                            GenerationStep.Feature.UNDERGROUND_ORES,
                                            feature
                                    ))
                    );
            GeoStrata.LOGGER.info("GeoStrata benchmark mode: vanilla diamond generation suppressed for attribution");
        }
        OreDebugCommands.register();
        GeoStrata.LOGGER.info("GeoStrata experiment companion enabled; debug tools active");
    }
}
