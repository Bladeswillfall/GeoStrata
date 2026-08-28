package com.geostrata.worldgen.placement;

import com.geostrata.GeoStrata;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

/** Registers GeoStrata-owned placement modifier types used by data-driven worldgen. */
public final class GeoStrataPlacementModifiers {
    public static final PlacementModifierType<SedimentSuitabilityPlacementModifier> SEDIMENT_SUITABILITY =
            () -> SedimentSuitabilityPlacementModifier.CODEC;

    private GeoStrataPlacementModifiers() {
    }

    public static void register() {
        Registry.register(
                Registries.PLACEMENT_MODIFIER_TYPE,
                GeoStrata.id("sediment_suitability"),
                SEDIMENT_SUITABILITY
        );
    }
}
