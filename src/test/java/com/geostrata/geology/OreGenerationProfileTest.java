package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreGenerationProfileTest {
    @Test
    void depthBandsCanExpressFlatAndLinearAffinityWithoutMaterialCode() {
        OreGenerationProfile profile = new OreGenerationProfile(
                0.08,
                OreGenerationProfile.defaults().candidateGrid(),
                List.of(
                        new OreGenerationProfile.DepthBand(null, -17, 0.0, 0.0),
                        new OreGenerationProfile.DepthBand(-16, 128, 0.0, 12.5),
                        new OreGenerationProfile.DepthBand(129, null, 12.5, 12.5)
                ),
                Map.of(),
                Map.of(),
                Map.of()
        );

        assertEquals(0.0, profile.depthMultiplier(-17), 0.0);
        assertEquals(0.0, profile.depthMultiplier(-16), 0.0);
        assertEquals(12.5 / 9.0, profile.depthMultiplier(0), 1.0e-12);
        assertEquals(62.5 / 9.0, profile.depthMultiplier(64), 1.0e-12);
        assertEquals(12.5, profile.depthMultiplier(128), 0.0);
        assertEquals(12.5, profile.depthMultiplier(200), 0.0);
    }

    @Test
    void provinceAndBiomeAffinitiesStayIndependentAndBiomeBonusesDoNotStack() {
        OreGenerationProfile profile = new OreGenerationProfile(
                0.5,
                OreGenerationProfile.defaults().candidateGrid(),
                List.of(),
                Map.of(GeologyProvince.OROGENIC_BELT, 1.4),
                Map.of(
                        "geostrata:has_mountain_rocks", 1.25,
                        "geostrata:has_swamp_soils", 1.10
                ),
                Map.of()
        );

        assertEquals(1.4, profile.provinceMultiplier(GeologyProvince.OROGENIC_BELT), 0.0);
        assertEquals(1.0, profile.provinceMultiplier(GeologyProvince.SEDIMENTARY_BASIN), 0.0);
        assertEquals(1.25, profile.biomeMultiplier(tag -> true), 0.0);
        assertEquals(1.10, profile.biomeMultiplier("geostrata:has_swamp_soils"::equals), 0.0);
        assertEquals(1.0, profile.biomeMultiplier(tag -> false), 0.0);
    }

    @Test
    void bodyGradeTraceAndDiscoveryTuningAreGenericData() {
        OreGenerationProfile.GradeTuning grades = new OreGenerationProfile.GradeTuning(0.2, 0.5, 0.9, 0.05);
        OreGenerationProfile.DiscoveryStringers discovery = new OreGenerationProfile.DiscoveryStringers(
                3,
                12.0,
                30.0,
                0.4,
                0.8,
                2.0,
                1,
                -0.75
        );
        OreGenerationProfile profile = new OreGenerationProfile(
                0.4,
                OreGenerationProfile.defaults().candidateGrid(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                1.7,
                2.5,
                grades,
                discovery
        );

        assertEquals(1.7, profile.bodyScale(), 0.0);
        assertEquals(2.5, profile.traceNormalScale(), 0.0);
        assertEquals(OreGrade.POOR, profile.grades().grade(0.19));
        assertEquals(OreGrade.MEDIUM, profile.grades().grade(0.20));
        assertEquals(OreGrade.RICH, profile.grades().grade(0.50));
        assertEquals(OreGrade.MASSIVE, profile.grades().grade(0.90));
        assertTrue(profile.discoveryStringers().enabled());
        assertEquals(1, profile.discoveryStringers().downwardBiasedCount());
        assertEquals(-0.75, profile.discoveryStringers().downwardBias(), 0.0);
    }

    @Test
    void discoveryAndGradeContractsRejectInvalidData() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreGenerationProfile.GradeTuning(0.6, 0.5, 0.9, 0.1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreGenerationProfile.DiscoveryStringers(2, 10, 20, 0.5, 1.0, 1.0, 3, -1.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreGenerationProfile.DiscoveryStringers(2, 10, 20, 0.5, 1.0, 1.0, 1, 0.5)
        );
        assertFalse(OreGenerationProfile.DiscoveryStringers.disabled().enabled());
    }
}
