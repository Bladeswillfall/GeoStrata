package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ChunkGeneratorTerrainMorphologySamplerTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void samplesCenterAndCardinalsAtConfiguredSpacing() {
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
    void defaultSpacingIsIntentionallyCoarse() {
        assertEquals(128, ChunkGeneratorTerrainMorphologySampler.DEFAULT_SAMPLE_SPACING_BLOCKS);
    }

    @Test
    void rejectsMissingSourceAndNonPositiveSpacing() {
        assertThrows(IllegalArgumentException.class,
                () -> ChunkGeneratorTerrainMorphologySampler.sample(null, 0, 0, 64));
        assertThrows(IllegalArgumentException.class,
                () -> ChunkGeneratorTerrainMorphologySampler.sample((x, z) -> 64.0, 0, 0, 0));
    }
}
