package com.geostrata.worldgen.feature;

import com.geostrata.GeoStrata;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

/** Registers GeoStrata-owned feature types before configured features are decoded. */
public final class GeoStrataFeatures {
    public static final Feature<StrataLensConfig> STRATA_LENS = new StrataLensFeature(StrataLensConfig.CODEC);
    public static final Feature<DefaultFeatureConfig> CORRELATED_SEDIMENTARY = new CorrelatedSedimentaryFeature();
    public static final Feature<DefaultFeatureConfig> ORE_DEPOSIT = new OreDepositFeature();

    private GeoStrataFeatures() {
    }

    public static void register() {
        Registry.register(Registries.FEATURE, GeoStrata.id("strata_lens"), STRATA_LENS);
        Registry.register(Registries.FEATURE, GeoStrata.id("correlated_sedimentary"), CORRELATED_SEDIMENTARY);
        Registry.register(Registries.FEATURE, GeoStrata.id("ore_deposit"), ORE_DEPOSIT);
    }
}
