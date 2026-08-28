package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainPatchMorphologyTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void morphologyInterpolationIsContinuousAcrossGridBoundaries() {
        TerrainAwareStructuralField.HeightSource heights =
                (x, z) -> 80.0 + x * x / 1024.0 - 0.125 * z;
        TerrainAwareStructuralField.TerrainPatch west = TerrainAwareStructuralField.TerrainPatch.sample(
                heights, 64, 64, 128
        );
        TerrainAwareStructuralField.TerrainPatch east = TerrainAwareStructuralField.TerrainPatch.sample(
                heights, 192, 64, 128
        );

        TerrainMorphologySample westSample = west.morphologyAt(128, 64);
        TerrainMorphologySample eastSample = east.morphologyAt(128, 64);

        assertEquals(westSample.centerHeight(), eastSample.centerHeight(), EPSILON);
        assertEquals(westSample.gradientX(), eastSample.gradientX(), EPSILON);
        assertEquals(westSample.gradientZ(), eastSample.gradientZ(), EPSILON);
        assertEquals(westSample.relief(), eastSample.relief(), EPSILON);
        assertEquals(westSample.prominence(), eastSample.prominence(), EPSILON);
    }
}
