package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructuralDeformationResponseTest {
    private static final double EPSILON = 1.0e-12;
    private static final ProvinceDeformationProfiles.Normalization NORMALIZATION =
            new ProvinceDeformationProfiles.Normalization(160.0, 0.5, 64.0, 0.5, 0.3, 0.2);

    @Test
    void flatTerrainRetainsProvinceBaselineDeformation() {
        ProvinceDeformationProfiles.Profile orogenic = profile(
                GeologyProvince.OROGENIC_BELT,
                0.55,
                0.40,
                0.95,
                1.0,
                0.75
        );
        TerrainMorphologySample flat = new TerrainMorphologySample(90.0, 0.0, 0.0, 0.0, 0.0);

        StructuralDeformationResponse.Result response = StructuralDeformationResponse.evaluate(
                NORMALIZATION,
                orogenic,
                flat
        );

        assertEquals(0.0, response.terrainSignal(), EPSILON);
        assertEquals(0.55, response.intensity(), EPSILON);
        assertEquals(0.55, response.foldPotential(), EPSILON);
    }

    @Test
    void mountainMorphologyStrengthensButDoesNotDefineOrogenicDeformation() {
        ProvinceDeformationProfiles.Profile orogenic = profile(
                GeologyProvince.OROGENIC_BELT,
                0.55,
                0.40,
                0.95,
                1.0,
                0.75
        );
        TerrainMorphologySample mountain = new TerrainMorphologySample(220.0, 0.4, 0.0, 160.0, 64.0);

        StructuralDeformationResponse.Result response = StructuralDeformationResponse.evaluate(
                NORMALIZATION,
                orogenic,
                mountain
        );

        assertEquals(0.94, response.terrainSignal(), EPSILON);
        assertEquals(0.926, response.intensity(), EPSILON);
        assertTrue(response.foldPotential() > 0.55);
        assertTrue(response.intensity() < 1.0);
    }

    @Test
    void riftStyleFavorsFaultingOverFolding() {
        ProvinceDeformationProfiles.Profile rift = profile(
                GeologyProvince.RIFT_PROVINCE,
                0.35,
                0.35,
                0.70,
                0.15,
                1.0
        );
        TerrainMorphologySample terrain = new TerrainMorphologySample(120.0, 0.2, 0.1, 80.0, 24.0);

        StructuralDeformationResponse.Result response = StructuralDeformationResponse.evaluate(
                NORMALIZATION,
                rift,
                terrain
        );

        assertTrue(response.faultPotential() > response.dipPotential());
        assertTrue(response.dipPotential() > response.foldPotential());
    }

    @Test
    void valleyProminenceDoesNotMasqueradeAsRidgeSignal() {
        ProvinceDeformationProfiles.Profile basin = profile(
                GeologyProvince.SEDIMENTARY_BASIN,
                0.08,
                0.22,
                0.70,
                0.20,
                0.15
        );
        TerrainMorphologySample valley = new TerrainMorphologySample(50.0, 0.0, 0.0, 40.0, -50.0);

        StructuralDeformationResponse.Result response = StructuralDeformationResponse.evaluate(
                NORMALIZATION,
                basin,
                valley
        );

        assertEquals(0.0, response.ridgeSignal(), EPSILON);
        assertTrue(response.terrainSignal() > 0.0);
    }

    private static ProvinceDeformationProfiles.Profile profile(
            GeologyProvince province,
            double baseline,
            double coupling,
            double dip,
            double fold,
            double fault
    ) {
        return new ProvinceDeformationProfiles.Profile(province, baseline, coupling, dip, fold, fault);
    }
}
