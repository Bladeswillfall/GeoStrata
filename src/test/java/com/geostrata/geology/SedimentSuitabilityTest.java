package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SedimentSuitabilityTest {
    @Test
    void preferredWetlandStronglyRaisesPeatWithoutBecomingARequirement() {
        TerrainMorphologySample flat = TerrainMorphologySample.fromCardinalHeights(
                64.0, 64.0, 64.0, 64.0, 64.0, 16.0
        );
        SedimentSuitability.Weights peat = new SedimentSuitability.Weights(
                "peat_soil", 0.005, 0.03, 0.04, 0.15, 0.55
        );

        double ordinary = SedimentSuitability.chance(
                SedimentSuitability.evidence(flat, false, false),
                peat
        );
        double preferredWet = SedimentSuitability.chance(
                SedimentSuitability.evidence(flat, true, true),
                peat
        );

        assertEquals(0.035, ordinary, 1.0e-12);
        assertEquals(0.735, preferredWet, 1.0e-12);
        assertTrue(ordinary > 0.0);
    }

    @Test
    void valleyEvidenceFavorsDepositionalGround() {
        TerrainMorphologySample valley = TerrainMorphologySample.fromCardinalHeights(
                60.0, 64.0, 64.0, 64.0, 64.0, 16.0
        );
        TerrainMorphologySample ridge = TerrainMorphologySample.fromCardinalHeights(
                68.0, 64.0, 64.0, 64.0, 64.0, 16.0
        );
        SedimentSuitability.Weights silt = new SedimentSuitability.Weights(
                "silty_loam", 0.025, 0.10, 0.18, 0.25, 0.32
        );

        double valleyChance = SedimentSuitability.chance(
                SedimentSuitability.evidence(valley, false, false),
                silt
        );
        double ridgeChance = SedimentSuitability.chance(
                SedimentSuitability.evidence(ridge, false, false),
                silt
        );

        assertTrue(valleyChance > ridgeChance);
    }

    @Test
    void compactedMudCanPreferExposedGroundOverSubmergedGround() {
        SedimentSuitability.Evidence exposed = new SedimentSuitability.Evidence(1.0, 0.5, false, true);
        SedimentSuitability.Evidence submerged = new SedimentSuitability.Evidence(1.0, 0.5, true, true);
        SedimentSuitability.Weights compacted = new SedimentSuitability.Weights(
                "compacted_mud", 0.02, 0.05, 0.06, -0.12, 0.35
        );

        assertTrue(
                SedimentSuitability.chance(exposed, compacted)
                        > SedimentSuitability.chance(submerged, compacted)
        );
    }

    @Test
    void chanceIsClampedToTheUnitInterval() {
        SedimentSuitability.Evidence ideal = new SedimentSuitability.Evidence(1.0, 1.0, true, true);
        SedimentSuitability.Weights high = new SedimentSuitability.Weights(
                "high", 1.0, 1.0, 1.0, 1.0, 1.0
        );
        SedimentSuitability.Weights low = new SedimentSuitability.Weights(
                "low", 0.0, -1.0, -1.0, -1.0, -1.0
        );

        assertEquals(1.0, SedimentSuitability.chance(ideal, high));
        assertEquals(0.0, SedimentSuitability.chance(ideal, low));
    }
}
