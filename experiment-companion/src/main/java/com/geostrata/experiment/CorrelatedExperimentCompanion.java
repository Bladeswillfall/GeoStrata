package com.geostrata.experiment;

import com.geostrata.GeoStrata;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.List;

/** Explicit opt-in companion for experimental ore/diamond worldgen and debug tools. */
public final class CorrelatedExperimentCompanion implements ModInitializer {
    private static final String BENCHMARK_SUPPRESS_DIAMOND_ENV = "GEOSTRATA_BENCHMARK_SUPPRESS_VANILLA_DIAMOND";
    private static final Identifier CREATE_RAW_ZINC = new Identifier("create", "raw_zinc");
    private static final RegistryKey<PlacedFeature> CREATE_ZINC_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            new Identifier("create", "zinc_ore")
    );
    private static final List<RegistryKey<PlacedFeature>> REPLACED_VANILLA_OVERWORLD_ORES = List.of(
            OrePlacedFeatures.ORE_COAL_UPPER,
            OrePlacedFeatures.ORE_COAL_LOWER,
            OrePlacedFeatures.ORE_IRON_UPPER,
            OrePlacedFeatures.ORE_IRON_MIDDLE,
            OrePlacedFeatures.ORE_IRON_SMALL,
            OrePlacedFeatures.ORE_COPPER,
            OrePlacedFeatures.ORE_COPPER_LARGE
    );
    private static final List<RegistryKey<PlacedFeature>> BENCHMARK_DIAMOND_ORES = List.of(
            OrePlacedFeatures.ORE_DIAMOND,
            OrePlacedFeatures.ORE_DIAMOND_LARGE,
            OrePlacedFeatures.ORE_DIAMOND_BURIED
    );

    @Override
    public void onInitialize() {
        boolean benchmarkSuppressVanillaDiamond = Boolean.parseBoolean(System.getenv(BENCHMARK_SUPPRESS_DIAMOND_ENV));
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
                            if (Registries.ITEM.containsId(CREATE_RAW_ZINC)) {
                                context.getGenerationSettings().removeFeature(
                                        GenerationStep.Feature.UNDERGROUND_ORES,
                                        CREATE_ZINC_ORE
                                );
                            }
                            if (benchmarkSuppressVanillaDiamond) {
                                BENCHMARK_DIAMOND_ORES.forEach(feature ->
                                        context.getGenerationSettings().removeFeature(
                                                GenerationStep.Feature.UNDERGROUND_ORES,
                                                feature
                                        ));
                            }
                        }
                );
        OreDebugCommands.register();
        GeoStrata.LOGGER.info(
                "GeoStrata experimental worldgen companion enabled; proven common overworld ores and detected Create zinc replace native generation"
        );
        if (benchmarkSuppressVanillaDiamond) {
            GeoStrata.LOGGER.info("GeoStrata benchmark mode: vanilla diamond generation suppressed for attribution");
        }
    }
}
