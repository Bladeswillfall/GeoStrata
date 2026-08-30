package com.geostrata.worldgen.feature;

import com.geostrata.geology.GeologyProvince;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProvinceBackgroundFeatureTest {
    @Test
    void mapsEveryProvinceToAConservativeBackgroundRock() {
        assertEquals("shale", ProvinceBackgroundFeature.backgroundLithology(GeologyProvince.SEDIMENTARY_BASIN));
        assertEquals("gneiss", ProvinceBackgroundFeature.backgroundLithology(GeologyProvince.CRATONIC_SHIELD));
        assertEquals("gneiss", ProvinceBackgroundFeature.backgroundLithology(GeologyProvince.OROGENIC_BELT));
        assertEquals("basalt", ProvinceBackgroundFeature.backgroundLithology(GeologyProvince.VOLCANIC_ARC));
        assertEquals("basalt", ProvinceBackgroundFeature.backgroundLithology(GeologyProvince.RIFT_PROVINCE));
    }
}
