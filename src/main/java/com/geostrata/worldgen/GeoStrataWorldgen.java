package com.geostrata.worldgen;

import com.geostrata.GeoStrata;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class GeoStrataWorldgen {
    private static final List<String> ROCK_FEATURES = List.of("limestone_ore", "chalk_ore", "shale_ore", "slate_ore", "mudstone_ore", "siltstone_ore", "marble_ore", "quartzite_ore", "schist_ore", "gneiss_ore", "basalt_ore", "rhyolite_ore", "conglomerate_ore", "breccia_ore");
    private static final List<String> EARTH_FEATURES = List.of("clay_loam_patch", "sandy_loam_patch", "silty_loam_patch", "peat_soil_patch", "wet_mud_patch", "compacted_mud_patch", "blue_clay_patch", "red_clay_patch");
    private static final RegistryKey<PlacedFeature> LIMESTONE_ORE = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            com.geostrata.GeoStrata.id("limestone_ore")
    );

    private GeoStrataWorldgen() {
    }

    public static void register() {
        ROCK_FEATURES.forEach(name -> BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES,
                key(name)
        ));

        EARTH_FEATURES.forEach(name -> BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES,
                key(name)
        ));
    }

    private static RegistryKey<PlacedFeature> key(String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, GeoStrata.id(path));
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES,
                LIMESTONE_ORE
        );
    }
}
