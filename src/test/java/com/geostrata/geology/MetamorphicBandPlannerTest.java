package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MetamorphicBandPlannerTest {
    private static final double EPSILON = 1.0e-15;

    @Test
    void noMetamorphicRockIsForcedWhenSuitabilityIsZero() {
        Optional<MetamorphicBandPlanner.Selection> selection = MetamorphicBandPlanner.select(
                42L,
                100,
                -200,
                64,
                0.0,
                12.0,
                new MetamorphicIntensityField.Suitability(0.0, 0.0, 0.0)
        );

        assertTrue(selection.isEmpty());
    }

    @Test
    void structuralOffsetMovesBandsWithoutChangingTheirOwnership() {
        MetamorphicIntensityField.Suitability suitability =
                new MetamorphicIntensityField.Suitability(0.3, 0.7, 0.2);

        MetamorphicBandPlanner.Selection base = MetamorphicBandPlanner.select(
                42L,
                100,
                -200,
                30,
                0.0,
                12.0,
                suitability
        ).orElseThrow();
        MetamorphicBandPlanner.Selection displaced = MetamorphicBandPlanner.select(
                42L,
                100,
                -200,
                38,
                8.0,
                12.0,
                suitability
        ).orElseThrow();

        assertEquals(base.bandIndex(), displaced.bandIndex());
        assertEquals(base.roll(), displaced.roll(), EPSILON);
        assertEquals(base.lithology(), displaced.lithology());
    }

    @Test
    void bandRollHasStableRegressionVectors() {
        MetamorphicIntensityField.Suitability suitability =
                new MetamorphicIntensityField.Suitability(1.0, 1.0, 1.0);

        assertEquals(
                0.6914742562727322,
                MetamorphicBandPlanner.select(0L, 0, 0, 0, 0.0, 12.0, suitability)
                        .orElseThrow()
                        .roll(),
                EPSILON
        );
        assertEquals(
                0.13327742471659565,
                MetamorphicBandPlanner.select(42L, 100, -200, -24, 0.0, 12.0, suitability)
                        .orElseThrow()
                        .roll(),
                EPSILON
        );
        assertEquals(
                0.3822126995533245,
                MetamorphicBandPlanner.select(123456789L, 384, -768, 60, 0.0, 12.0, suitability)
                        .orElseThrow()
                        .roll(),
                EPSILON
        );
    }

    @Test
    void suitabilityWeightsControlBandLithology() {
        assertEquals(
                "slate",
                MetamorphicBandPlanner.select(
                        42L,
                        100,
                        -200,
                        -24,
                        0.0,
                        12.0,
                        new MetamorphicIntensityField.Suitability(0.9, 0.1, 0.0)
                ).orElseThrow().lithology()
        );
        assertEquals(
                "gneiss",
                MetamorphicBandPlanner.select(
                        0L,
                        0,
                        0,
                        0,
                        0.0,
                        12.0,
                        new MetamorphicIntensityField.Suitability(0.0, 0.1, 0.9)
                ).orElseThrow().lithology()
        );
    }
}
