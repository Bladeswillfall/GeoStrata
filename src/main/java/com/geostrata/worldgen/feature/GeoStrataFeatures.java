package com.geostrata.worldgen.feature;

import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

/** Defines GeoStrata-owned feature types; loader adapters own registry timing. */
public final class GeoStrataFeatures {
    public static final Feature<StrataLensConfig> STRATA_LENS = new StrataLensFeature(StrataLensConfig.CODEC);
    public static final Feature<DefaultFeatureConfig> CORRELATED_SEDIMENTARY = new CorrelatedSedimentaryFeature();
    public static final Feature<DefaultFeatureConfig> PROVINCE_BACKGROUND = new ProvinceBackgroundFeature();
    public static final Feature<DefaultFeatureConfig> ORE_DEPOSIT = new OreDepositFeature();
    public static final Feature<DiamondPipeConfig> DIAMOND_PIPE = new DiamondPipeFeature(DiamondPipeConfig.CODEC);
    public static final Feature<DefaultFeatureConfig> DIAMOND_STRUCTURAL = new DiamondStructuralFeature();

    private GeoStrataFeatures() {
    }
}
