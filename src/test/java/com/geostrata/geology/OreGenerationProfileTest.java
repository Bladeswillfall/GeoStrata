package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
