package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeologyProvinceSamplerTest {
    @Test
    void regressionVectorsRemainStable() {
        assertSample(0L, 0, 0, GeologyProvince.CRATONIC_SHIELD, 0, 0, 291, 138);
        assertSample(1L, 0, 0, GeologyProvince.RIFT_PROVINCE, -1, -1, -533, -128);
        assertSample(123456789L, 1000, -500, GeologyProvince.OROGENIC_BELT, 1, -1, 1165, -602);
        assertSample(-42L, -2000, 3000, GeologyProvince.RIFT_PROVINCE, -3, 3, -2109, 2461);
        assertSample(987654321L, 100000, 100000, GeologyProvince.VOLCANIC_ARC, 130, 130, 100146, 100102);
    }

    @Test
    void repeatedSamplingIsDeterministic() {
        GeologyProvinceSampler.Sample first = GeologyProvinceSampler.sample(8675309L, -12345, 67890);
        GeologyProvinceSampler.Sample second = GeologyProvinceSampler.sample(8675309L, -12345, 67890);
        assertEquals(first, second);
    }

    private static void assertSample(
            long seed,
            int x,
            int z,
            GeologyProvince province,
            int cellX,
            int cellZ,
            int siteX,
            int siteZ
    ) {
        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(seed, x, z);
        assertEquals(province, sample.province());
        assertEquals(cellX, sample.sourceCellX());
        assertEquals(cellZ, sample.sourceCellZ());
        assertEquals(siteX, sample.siteX());
        assertEquals(siteZ, sample.siteZ());
    }
}
