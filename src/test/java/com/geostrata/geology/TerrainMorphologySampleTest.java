package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainMorphologySampleTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void flatTerrainHasNoStructuralEvidence() {
        TerrainMorphologySample sample = TerrainMorphologySample.fromCardinalHeights(
                80.0,
                80.0,
                80.0,
                80.0,
                80.0,
                64.0
        );

        assertEquals(0.0, sample.slopeMagnitude(), EPSILON);
        assertEquals(0.0, sample.relief(), EPSILON);
        assertEquals(0.0, sample.prominence(), EPSILON);
    }

    @Test
    void activeGeneratorAdapterSamplesCenterAndCardinals() {
        TerrainMorphologySample sample = ChunkGeneratorTerrainMorphologySampler.sample(
                (x, z) -> 100.0 + 0.5 * x - 0.25 * z,
                40,
                -20,
                16
        );

        assertEquals(125.0, sample.centerHeight(), EPSILON);
        assertEquals(0.5, sample.gradientX(), EPSILON);
        assertEquals(-0.25, sample.gradientZ(), EPSILON);
        assertEquals(16.0, sample.relief(), EPSILON);
        assertEquals(0.0, sample.prominence(), EPSILON);
    }

    @Test
    void prominenceDistinguishesRidgeFromValley() {
        TerrainMorphologySample ridge = TerrainMorphologySample.fromCardinalHeights(
                140.0, 100.0, 100.0, 100.0, 100.0, 64.0
        );
        TerrainMorphologySample valley = TerrainMorphologySample.fromCardinalHeights(
                60.0, 100.0, 100.0, 100.0, 100.0, 64.0
        );

        assertTrue(ridge.prominence() > 0.0);
        assertTrue(valley.prominence() < 0.0);
        assertEquals(40.0, ridge.relief(), EPSILON);
        assertEquals(40.0, valley.relief(), EPSILON);
    }

    @Test
    void rejectsInvalidEvidence() {
        assertThrows(IllegalArgumentException.class,
                () -> TerrainMorphologySample.fromCardinalHeights(
                        Double.NaN, 0.0, 0.0, 0.0, 0.0, 64.0
                ));
        assertThrows(IllegalArgumentException.class,
                () -> ChunkGeneratorTerrainMorphologySampler.sample((x, z) -> 64.0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainMorphologySample(0.0, 0.0, 0.0, -1.0, 0.0));
    }
}
