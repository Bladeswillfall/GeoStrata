package com.geostrata.worldgen.feature;

import com.geostrata.GeoStrata;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.OreFeatureConfig;

/** Registers GeoStrata-owned feature types before configured features are decoded. */
public final class GeoStrataFeatures {
    public static final Feature<OreFeatureConfig> STRATA_LENS = new StrataLensFeature(OreFeatureConfig.CODEC);

    private GeoStrataFeatures() {
    }

    public static void register() {
        Registry.register(Registries.FEATURE, GeoStrata.id("strata_lens"), STRATA_LENS);
    }
}
