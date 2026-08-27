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
    private static final TagKey<Biome> HAS_RIVER_SOILS = biomeTag("has_river_soils");
    private static final TagKey<Biome> HAS_SWAMP_SOILS = biomeTag("has_swamp_soils");
    private static final TagKey<Biome> HAS_JUNGLE_SOILS = biomeTag("has_jungle_soils");
    private static final TagKey<Biome> HAS_BADLANDS_SOILS = biomeTag("has_badlands_soils");

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

        addToTag("clay_loam_patch", HAS_RIVER_SOILS);
        addToTag("sandy_loam_patch", HAS_COASTAL_ROCKS);
        addToTag("silty_loam_patch", HAS_RIVER_SOILS);
        addToTag("blue_clay_patch", HAS_RIVER_SOILS);

        addToTag("peat_soil_patch", HAS_SWAMP_SOILS);
        addToTag("wet_mud_patch", HAS_SWAMP_SOILS);
        addToTag("compacted_mud_patch", HAS_JUNGLE_SOILS);
        addToTag("red_clay_patch", HAS_BADLANDS_SOILS);
    }

    private static void addToTag(String feature, TagKey<Biome> tag) {
        BiomeModifications.addFeature(
                BiomeSelectors.tag(tag),
                GenerationStep.Feature.UNDERGROUND_ORES,
                key(feature)
        );
    }

    private static RegistryKey<PlacedFeature> key(String path) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, GeoStrata.id(path));
    }

    private static TagKey<Biome> biomeTag(String name) {
        return TagKey.of(RegistryKeys.BIOME, GeoStrata.id(name));
    }
}
