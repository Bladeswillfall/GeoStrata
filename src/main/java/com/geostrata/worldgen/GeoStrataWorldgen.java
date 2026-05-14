package com.geostrata.worldgen;

import com.geostrata.GeoStrata;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class GeoStrataWorldgen {
    private GeoStrataWorldgen() {
    }

    public static void register() {
        addToOverworld("limestone_ore");
        addToTagged("chalk_ore", BiomeTags.IS_BEACH);
        addToTagged("chalk_ore", BiomeTags.IS_OCEAN);
        addToOverworld("shale_ore");
        addToTagged("slate_ore", BiomeTags.IS_MOUNTAIN);
        addToOverworld("mudstone_ore");
        addToTagged("siltstone_ore", BiomeTags.IS_RIVER);
        addToTagged("marble_ore", BiomeTags.IS_MOUNTAIN);
        addToTagged("quartzite_ore", BiomeTags.IS_MOUNTAIN);
        addToTagged("schist_ore", BiomeTags.IS_MOUNTAIN);
        addToTagged("gneiss_ore", BiomeTags.IS_MOUNTAIN);
        addToOverworld("basalt_ore");
        addToTagged("rhyolite_ore", BiomeTags.IS_MOUNTAIN);
        addToTagged("conglomerate_ore", BiomeTags.IS_RIVER);
        addToTagged("breccia_ore", BiomeTags.IS_MOUNTAIN);

        addToTagged("clay_loam_patch", BiomeTags.IS_RIVER);
        addToTagged("sandy_loam_patch", BiomeTags.IS_BEACH);
        addToTagged("sandy_loam_patch", BiomeTags.IS_RIVER);
        addToTagged("silty_loam_patch", BiomeTags.IS_RIVER);
        addToTagged("peat_soil_patch", BiomeTags.IS_SWAMP);
        addToTagged("wet_mud_patch", BiomeTags.IS_SWAMP);
        addToTagged("compacted_mud_patch", BiomeTags.IS_JUNGLE);
        addToTagged("blue_clay_patch", BiomeTags.IS_RIVER);
        addToTagged("red_clay_patch", BiomeTags.IS_BADLANDS);
    }

    private static void addToOverworld(String feature) {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, key(feature));
    }

    private static void addToTagged(String feature, RegistryKey<Biome> tag) {
        BiomeModifications.addFeature(BiomeSelectors.tag(tag), GenerationStep.Feature.UNDERGROUND_ORES, key(feature));
    }

    private static RegistryKey<PlacedFeature> key(String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, GeoStrata.id(path));
    }
}
