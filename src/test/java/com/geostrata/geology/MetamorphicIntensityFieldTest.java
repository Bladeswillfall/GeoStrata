package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetamorphicIntensityFieldTest {
    @Test
    void regionalSignalHasStableRegressionVectors() {
        assertEquals(-0.7141536819356591, MetamorphicIntensityField.regionalSignal(0L, 0, 0), 1.0e-12);
        assertEquals(
                0.11251536011546426,
                MetamorphicIntensityField.regionalSignal(123456789L, 1000, -2000),
                1.0e-12
        );
    }

    @Test
    void regionalSignalIsContinuousAcrossGridBoundaries() {
        double west = MetamorphicIntensityField.regionalSignal(42L, 383, 17);
        double east = MetamorphicIntensityField.regionalSignal(42L, 384, 17);

        assertTrue(Math.abs(east - west) < 0.001);
    }

    @Test
    void provinceBaselinesDescribeBroadMetamorphicHistory() {
        assertTrue(
                MetamorphicIntensityField.baselineFor(GeologyProvince.OROGENIC_BELT)
                        > MetamorphicIntensityField.baselineFor(GeologyProvince.VOLCANIC_ARC)
        );
        assertTrue(
                MetamorphicIntensityField.baselineFor(GeologyProvince.CRATONIC_SHIELD)
                        > MetamorphicIntensityField.baselineFor(GeologyProvince.RIFT_PROVINCE)
        );
        assertTrue(
                MetamorphicIntensityField.baselineFor(GeologyProvince.RIFT_PROVINCE)
                        > MetamorphicIntensityField.baselineFor(GeologyProvince.SEDIMENTARY_BASIN)
        );
    }

    @Test
    void metamorphicGradesOccupyOverlappingWindows() {
        MetamorphicIntensityField.Suitability slate = MetamorphicIntensityField.suitability(0.26);
        MetamorphicIntensityField.Suitability slatePhyllite = MetamorphicIntensityField.suitability(0.34);
        MetamorphicIntensityField.Suitability phyllite = MetamorphicIntensityField.suitability(0.44);
        MetamorphicIntensityField.Suitability phylliteSchist = MetamorphicIntensityField.suitability(0.54);
        MetamorphicIntensityField.Suitability schist = MetamorphicIntensityField.suitability(0.62);
        MetamorphicIntensityField.Suitability highGradeOverlap = MetamorphicIntensityField.suitability(0.72);
        MetamorphicIntensityField.Suitability gneiss = MetamorphicIntensityField.suitability(0.90);

        assertEquals("slate", slate.dominantLithology());
        assertTrue(slatePhyllite.slate() > 0.0 && slatePhyllite.phyllite() > 0.0);
        assertEquals("phyllite", phyllite.dominantLithology());
        assertTrue(phylliteSchist.phyllite() > 0.0 && phylliteSchist.schist() > 0.0);
        assertEquals("schist", schist.dominantLithology());
        assertTrue(highGradeOverlap.schist() > 0.0 && highGradeOverlap.gneiss() > 0.0);
        assertEquals("gneiss", gneiss.dominantLithology());
    }

    @Test
    void terrainCanOnlyNudgeTheRegionalHistory() {
        TerrainMorphologySample strongRelief = new TerrainMorphologySample(100.0, 0.0, 0.0, 96.0, 48.0);

        assertEquals(0.08, MetamorphicIntensityField.terrainAdjustment(strongRelief, 0.08), 1.0e-12);
        assertEquals(0.01, MetamorphicIntensityField.terrainAdjustment(strongRelief, 0.01), 1.0e-12);
    }

    @Test
    void sampledIntensityIsDeterministicAndBounded() {
        for (int x = -2048; x <= 2048; x += 257) {
            for (int z = -2048; z <= 2048; z += 263) {
                MetamorphicIntensityField.Sample first = MetamorphicIntensityField.sample(918273645L, x, z);
                MetamorphicIntensityField.Sample second = MetamorphicIntensityField.sample(918273645L, x, z);

                assertEquals(first, second);
                assertTrue(first.intensity() >= 0.0 && first.intensity() <= 1.0);
                assertTrue(first.suitability().slate() >= 0.0 && first.suitability().slate() <= 1.0);
                assertTrue(first.suitability().phyllite() >= 0.0 && first.suitability().phyllite() <= 1.0);
                assertTrue(first.suitability().schist() >= 0.0 && first.suitability().schist() <= 1.0);
                assertTrue(first.suitability().gneiss() >= 0.0 && first.suitability().gneiss() <= 1.0);
            }
        }
    }
}
