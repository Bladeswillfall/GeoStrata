package com.geostrata.platform.fabric;

import com.geostrata.GeoStrata;
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

/** Fabric biome-modification adapter for GeoStrata's shared data-driven placed features. */
public final class FabricWorldgenRegistration {
    private static final String BENCHMARK_DISABLE_COMMON_OWNERSHIP_ENV =
            "GEOSTRATA_BENCHMARK_DISABLE_CORE_COMMON_ORE_OWNERSHIP";
    private static final List<RegistryKey<PlacedFeature>> REPLACED_VANILLA_COMMON_ORES = List.of(
            OrePlacedFeatures.ORE_COAL_UPPER,
            OrePlacedFeatures.ORE_COAL_LOWER,
            OrePlacedFeatures.ORE_IRON_UPPER,
            OrePlacedFeatures.ORE_IRON_MIDDLE,
            OrePlacedFeatures.ORE_IRON_SMALL,
            OrePlacedFeatures.ORE_COPPER,
            OrePlacedFeatures.ORE_COPPER_LARGE
    );
    private static final TagKey<Biome> HAS_COMMON_ROCKS = biomeTag("has_common_rocks");
    private static final TagKey<Biome> HAS_COASTAL_ROCKS = biomeTag("has_coastal_rocks");
    private static final TagKey<Biome> HAS_SURFACE_SEDIMENTS = biomeTag("has_surface_sediments");
    private static final TagKey<Biome> HAS_BADLANDS_SOILS = biomeTag("has_badlands_soils");
    private static final TagKey<Biome> HAS_EXPERIMENTAL_ORE_DEPOSITS = biomeTag("has_experimental_ore_deposits");
    private static final TagKey<Biome> HAS_EXPERIMENTAL_DIAMOND_GEOLOGY = biomeTag("has_experimental_diamond_geology");
    private static final List<RegistryKey<PlacedFeature>> REPLACED_VANILLA_IGNEOUS_BLOBS = List.of(
            OrePlacedFeatures.ORE_GRANITE_UPPER,
            OrePlacedFeatures.ORE_GRANITE_LOWER,
            OrePlacedFeatures.ORE_DIORITE_UPPER,
            OrePlacedFeatures.ORE_DIORITE_LOWER,
            OrePlacedFeatures.ORE_ANDESITE_UPPER,
            OrePlacedFeatures.ORE_ANDESITE_LOWER
    );

    private FabricWorldgenRegistration() {
    }

    public static void register() {
        if (commonOreOwnershipEnabled()) {
            removeCommonVanillaOres();
        }
        removeVanillaIgneousBlobs();
        addToTag("correlated_sedimentary_experiment", HAS_COMMON_ROCKS, GenerationStep.Feature.TOP_LAYER_MODIFICATION);
        addToTag("province_background_experiment", HAS_COMMON_ROCKS, GenerationStep.Feature.TOP_LAYER_MODIFICATION);

        addToTag("clay_loam_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("silty_loam_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("peat_soil_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("wet_mud_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("compacted_mud_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("sandy_loam_patch", HAS_COASTAL_ROCKS);

        addToTag("blue_clay_patch", HAS_COMMON_ROCKS);
        addToTag("blue_clay_background_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_background_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_badlands_patch", HAS_BADLANDS_SOILS);

        addToTag(
                "ore_deposit_experiment",
                HAS_EXPERIMENTAL_ORE_DEPOSITS,
                GenerationStep.Feature.UNDERGROUND_DECORATION
        );
        addToTag(
                "kimberlite_pipe",
                HAS_EXPERIMENTAL_DIAMOND_GEOLOGY,
                GenerationStep.Feature.UNDERGROUND_DECORATION
        );
        addToTag(
                "lamproite_pipe",
                HAS_EXPERIMENTAL_DIAMOND_GEOLOGY,
                GenerationStep.Feature.UNDERGROUND_DECORATION
        );
        addToTag(
                "diamond_structural_experiment",
                HAS_EXPERIMENTAL_DIAMOND_GEOLOGY,
                GenerationStep.Feature.UNDERGROUND_DECORATION
        );
    }

    static boolean commonOreOwnershipEnabled() {
        return !Boolean.parseBoolean(System.getenv(BENCHMARK_DISABLE_COMMON_OWNERSHIP_ENV));
    }

    private static void removeCommonVanillaOres() {
        BiomeModifications.create(GeoStrata.id("common_ore_worldgen_ownership"))
                .add(
                        ModificationPhase.REMOVALS,
                        BiomeSelectors.foundInOverworld(),
                        context -> REPLACED_VANILLA_COMMON_ORES.forEach(feature ->
                                context.getGenerationSettings().removeFeature(
                                        GenerationStep.Feature.UNDERGROUND_ORES,
                                        feature
                                ))
                );
    }

    private static void removeVanillaIgneousBlobs() {
        BiomeModifications.create(GeoStrata.id("core_igneous_ownership"))
                .add(
                        ModificationPhase.REMOVALS,
                        BiomeSelectors.foundInOverworld(),
                        context -> REPLACED_VANILLA_IGNEOUS_BLOBS.forEach(feature ->
                                context.getGenerationSettings().removeFeature(
                                        GenerationStep.Feature.UNDERGROUND_ORES,
                                        feature
                                ))
                );
    }

    private static void addToTag(String feature, TagKey<Biome> tag) {
        addToTag(feature, tag, GenerationStep.Feature.UNDERGROUND_ORES);
    }

    private static void addToTag(String feature, TagKey<Biome> tag, GenerationStep.Feature step) {
        BiomeModifications.addFeature(BiomeSelectors.tag(tag), step, key(feature));
    }

    private static RegistryKey<PlacedFeature> key(String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, GeoStrata.id(path));
    }

    private static TagKey<Biome> biomeTag(String name) {
        return TagKey.of(RegistryKeys.BIOME, GeoStrata.id(name));
    }
}
