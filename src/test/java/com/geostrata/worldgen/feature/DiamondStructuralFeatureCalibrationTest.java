package com.geostrata.worldgen.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DiamondStructuralFeatureCalibrationTest {
    @Test
    void bundledStructuralCalibrationStaysWithinMeasuredProfile() {
        assertEquals(0.08, DiamondStructuralFeature.STRUCTURAL_DEPTH_FRACTION, 0.0);
        assertEquals(0.65, DiamondStructuralFeature.LARGE_CLUSTER_RADIUS_CHANCE, 0.0);
    }
}
