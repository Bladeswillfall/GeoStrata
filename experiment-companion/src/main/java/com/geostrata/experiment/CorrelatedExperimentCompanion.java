package com.geostrata.experiment;

import com.geostrata.GeoStrata;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
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
            vanillaFeature("ore_coal_upper"),
            vanillaFeature("ore_coal_lower"),
            vanillaFeature("ore_iron_upper"),
            vanillaFeature("ore_iron_middle"),
            vanillaFeature("ore_iron_small"),
            vanillaFeature("ore_gold_extra"),
            vanillaFeature("ore_gold"),
            vanillaFeature("ore_gold_lower"),
            vanillaFeature("ore_redstone"),
            vanillaFeature("ore_redstone_lower"),
            vanillaFeature("ore_diamond"),
            vanillaFeature("ore_diamond_medium"),
            vanillaFeature("ore_diamond_large"),
            vanillaFeature("ore_diamond_buried"),
            vanillaFeature("ore_lapis"),
            vanillaFeature("ore_lapis_buried"),
            vanillaFeature("ore_emerald"),
            vanillaFeature("ore_copper"),
            vanillaFeature("ore_copper_large")
    );

    @Override
    public void onInitialize() {
        BiomeModifications.create(GeoStrata.id("experimental_ore_validation"))
                .add(
                        ModificationPhase.REMOVALS,
                        BiomeSelectors.tag(REGISTRATION_BIOMES),
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
        GeoStrata.LOGGER.info(
                "GeoStrata experimental worldgen companion enabled; vanilla Overworld ores suppressed for validation"
        );
    }

    private static RegistryKey<PlacedFeature> vanillaFeature(String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, new Identifier("minecraft", path));
    }
}
