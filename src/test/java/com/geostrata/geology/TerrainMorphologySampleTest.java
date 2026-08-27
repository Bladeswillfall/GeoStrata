package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainMorphologySampleTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void flatTerrainProducesNoSlopeReliefOrProminence() {
        TerrainMorphologySample sample = TerrainMorphologySample.fromCardinalHeights(
                80.0,
                80.0,
                80.0,
                80.0,
                80.0,
                64.0
        );

        assertEquals(0.0, sample.gradientX(), EPSILON);
        assertEquals(0.0, sample.gradientZ(), EPSILON);
        assertEquals(0.0, sample.relief(), EPSILON);
        assertEquals(0.0, sample.prominence(), EPSILON);
        assertEquals(0.0, sample.slopeMagnitude(), EPSILON);
    }

    @Test
    void centeredGradientReportsDirectionAndRelief() {
        TerrainMorphologySample sample = TerrainMorphologySample.fromCardinalHeights(
                100.0,
                80.0,
                120.0,
                100.0,
                100.0,
                20.0
        );

        assertEquals(1.0, sample.gradientX(), EPSILON);
        assertEquals(0.0, sample.gradientZ(), EPSILON);
        assertEquals(40.0, sample.relief(), EPSILON);
        assertEquals(0.0, sample.prominence(), EPSILON);
        assertEquals(1.0, sample.slopeMagnitude(), EPSILON);
    }

    @Test
    void prominenceDistinguishesRidgeFromValleyWithoutChangingSlope() {
        TerrainMorphologySample ridge = TerrainMorphologySample.fromCardinalHeights(
                140.0,
                100.0,
                100.0,
                100.0,
                100.0,
                64.0
        );
        TerrainMorphologySample valley = TerrainMorphologySample.fromCardinalHeights(
                60.0,
                100.0,
                100.0,
                100.0,
                100.0,
                64.0
        );

        assertTrue(ridge.prominence() > 0.0);
        assertTrue(valley.prominence() < 0.0);
        assertEquals(0.0, ridge.slopeMagnitude(), EPSILON);
        assertEquals(0.0, valley.slopeMagnitude(), EPSILON);
        assertEquals(40.0, ridge.relief(), EPSILON);
        assertEquals(40.0, valley.relief(), EPSILON);
    }

    @Test
    void rejectsInvalidMeasurements() {
        assertThrows(IllegalArgumentException.class,
                () -> TerrainMorphologySample.fromCardinalHeights(
                        Double.NaN,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        64.0
                ));
        assertThrows(IllegalArgumentException.class,
                () -> TerrainMorphologySample.fromCardinalHeights(
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainMorphologySample(0.0, 0.0, 0.0, -1.0, 0.0));
    }
}
