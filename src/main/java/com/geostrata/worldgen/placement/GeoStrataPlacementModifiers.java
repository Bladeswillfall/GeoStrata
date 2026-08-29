package com.geostrata.worldgen.placement;

import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

/** Defines GeoStrata-owned placement modifier types; loader adapters own registry timing. */
public final class GeoStrataPlacementModifiers {
    public static final PlacementModifierType<SedimentSuitabilityPlacementModifier> SEDIMENT_SUITABILITY =
            () -> SedimentSuitabilityPlacementModifier.CODEC;
    public static final PlacementModifierType<SubsurfaceAnchorPlacementModifier> SUBSURFACE_ANCHOR =
            () -> SubsurfaceAnchorPlacementModifier.CODEC;

    private GeoStrataPlacementModifiers() {
    }
}
