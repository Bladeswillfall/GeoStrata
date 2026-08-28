package com.geostrata.worldgen;

import com.geostrata.GeoStrata;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Registers GeoStrata's baseline world-generation features.
 *
 * <p>Biome targeting is intentionally driven through GeoStrata-owned biome
 * tags. Vanilla supplies the default values for those tags, while modpacks
 * and compatibility data packs can extend them without adding a Java
 * dependency on GeoStrata.</p>
 */
public final class GeoStrataWorldgen {
    private static final TagKey<Biome> HAS_COMMON_ROCKS = biomeTag("has_common_rocks");
    private static final TagKey<Biome> HAS_MOUNTAIN_ROCKS = biomeTag("has_mountain_rocks");
    private static final TagKey<Biome> HAS_FLUVIAL_ROCKS = biomeTag("has_fluvial_rocks");
    private static final TagKey<Biome> HAS_COASTAL_ROCKS = biomeTag("has_coastal_rocks");
    private static final TagKey<Biome> HAS_SURFACE_SEDIMENTS = biomeTag("has_surface_sediments");
    private static final TagKey<Biome> HAS_BADLANDS_SOILS = biomeTag("has_badlands_soils");
    private static final TagKey<Biome> HAS_EXPERIMENTAL_ORE_DEPOSITS = biomeTag("has_experimental_ore_deposits");

    private GeoStrataWorldgen() {
    }

    public static void register() {
        addToTag("limestone_ore", HAS_COMMON_ROCKS);
        addToTag("shale_ore", HAS_COMMON_ROCKS);
        addToTag("mudstone_ore", HAS_COMMON_ROCKS);
        addToTag("basalt_ore", HAS_COMMON_ROCKS);

        addToTag("chalk_ore", HAS_COASTAL_ROCKS);
        addToTag("siltstone_ore", HAS_FLUVIAL_ROCKS);
        addToTag("conglomerate_ore", HAS_FLUVIAL_ROCKS);

        addToTag("slate_ore", HAS_MOUNTAIN_ROCKS);
        addToTag("marble_ore", HAS_MOUNTAIN_ROCKS);
        addToTag("quartzite_ore", HAS_MOUNTAIN_ROCKS);
        addToTag("schist_ore", HAS_MOUNTAIN_ROCKS);
        addToTag("gneiss_ore", HAS_MOUNTAIN_ROCKS);
        addToTag("rhyolite_ore", HAS_MOUNTAIN_ROCKS);
        addToTag("breccia_ore", HAS_MOUNTAIN_ROCKS);

        // These sediments use actual surface/terrain evidence in their placement modifiers.
        addToTag("clay_loam_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("silty_loam_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("peat_soil_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("wet_mud_patch", HAS_SURFACE_SEDIMENTS);
        addToTag("compacted_mud_patch", HAS_SURFACE_SEDIMENTS);

        // Sandy loam is surface-aware but remains coastally scoped by its registration tag.
        addToTag("sandy_loam_patch", HAS_COASTAL_ROCKS);

        // Clay uses actual water-floor evidence first, with rare shallow background deposits
        // so it remains discoverable away from obvious rivers, lakes and coasts.
        addToTag("blue_clay_patch", HAS_COMMON_ROCKS);
        addToTag("blue_clay_background_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_background_patch", HAS_COMMON_ROCKS);
        addToTag("red_clay_badlands_patch", HAS_BADLANDS_SOILS);

        // Registered in core but dormant until the server-data experiment explicitly opts in.
        // Decoration runs after ordinary ore-stage host geology has been written.
        addToTag(
                "ore_deposit_experiment",
                HAS_EXPERIMENTAL_ORE_DEPOSITS,
                GenerationStep.Feature.UNDERGROUND_DECORATION
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
