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
    private static final RegistryKey<PlacedFeature> CORRELATED_FEATURE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            GeoStrata.id("correlated_sedimentary_experiment")
    );
    private static final RegistryKey<PlacedFeature> BACKGROUND_FEATURE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            GeoStrata.id("province_background_experiment")
    );
    private static final List<RegistryKey<PlacedFeature>> VANILLA_OVERWORLD_ORES = List.of(
            OrePlacedFeatures.ORE_COAL_UPPER,
            OrePlacedFeatures.ORE_COAL_LOWER,
            OrePlacedFeatures.ORE_IRON_UPPER,
            OrePlacedFeatures.ORE_IRON_MIDDLE,
            OrePlacedFeatures.ORE_IRON_SMALL,
            OrePlacedFeatures.ORE_GOLD_EXTRA,
            OrePlacedFeatures.ORE_GOLD,
            OrePlacedFeatures.ORE_GOLD_LOWER,
            OrePlacedFeatures.ORE_REDSTONE,
            OrePlacedFeatures.ORE_REDSTONE_LOWER,
            OrePlacedFeatures.ORE_DIAMOND,
            OrePlacedFeatures.ORE_DIAMOND_LARGE,
            OrePlacedFeatures.ORE_DIAMOND_BURIED,
            OrePlacedFeatures.ORE_LAPIS,
            OrePlacedFeatures.ORE_LAPIS_BURIED,
            OrePlacedFeatures.ORE_EMERALD,
            OrePlacedFeatures.ORE_COPPER,
            OrePlacedFeatures.ORE_COPPER_LARGE
    );

    @Override
    public void onInitialize() {
        BiomeModifications.create(GeoStrata.id("experimental_ore_validation"))
                .add(
                        ModificationPhase.REMOVALS,
                        BiomeSelectors.foundInOverworld(),
                        context -> VANILLA_OVERWORLD_ORES.forEach(feature -> context.getGenerationSettings()
                                .removeFeature(GenerationStep.Feature.UNDERGROUND_ORES, feature))
                );
        BiomeModifications.addFeature(
                BiomeSelectors.tag(REGISTRATION_BIOMES),
                GenerationStep.Feature.UNDERGROUND_DECORATION,
                CORRELATED_FEATURE
        );
        BiomeModifications.addFeature(
                BiomeSelectors.tag(REGISTRATION_BIOMES),
                GenerationStep.Feature.UNDERGROUND_DECORATION,
                BACKGROUND_FEATURE
        );
        OreDebugCommands.register();
        GeoStrata.LOGGER.info(
                "GeoStrata experimental worldgen companion enabled; vanilla Overworld ores suppressed for validation"
        );
    }
}
