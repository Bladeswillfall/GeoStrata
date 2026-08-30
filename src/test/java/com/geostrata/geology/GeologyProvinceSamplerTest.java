package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeologyProvinceSamplerTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void regressionVectorsRemainStable() {
        assertSample(
                0L, 0, 0,
                GeologyProvince.CRATONIC_SHIELD, 0, 0, 291, 138,
                GeologyProvince.CRATONIC_SHIELD, -1, 0, -607, 131,
                156.94679076745206
        );
        assertSample(
                1L, 0, 0,
                GeologyProvince.RIFT_PROVINCE, -1, -1, -533, -128,
                GeologyProvince.OROGENIC_BELT, -1, 0, -484, 381,
                77.19127832132233
        );
        assertSample(
                123456789L, 1000, -500,
                GeologyProvince.OROGENIC_BELT, 1, -1, 1165, -602,
                GeologyProvince.OROGENIC_BELT, 0, -1, 572, -532,
                122.73887758761866
        );
        assertSample(
                -42L, -2000, 3000,
                GeologyProvince.RIFT_PROVINCE, -3, 3, -2109, 2461,
                GeologyProvince.CRATONIC_SHIELD, -3, 4, -1785, 3524,
                8.278285795332366
        );
        assertSample(
                987654321L, 100000, 100000,
                GeologyProvince.VOLCANIC_ARC, 130, 130, 100146, 100102,
                GeologyProvince.RIFT_PROVINCE, 130, 129, 100368, 99600,
                240.21261138772743
        );
    }

    @Test
    void repeatedSamplingIsDeterministic() {
        GeologyProvinceSampler.Sample first = GeologyProvinceSampler.sample(8675309L, -12345, 67890);
        GeologyProvinceSampler.Sample second = GeologyProvinceSampler.sample(8675309L, -12345, 67890);
        assertEquals(first, second);
    }

    @Test
    void chunkContextMatchesDirectSampling() {
        assertContextMatchesDirect(123456789L, 992, -512, 1007, -497);
    }

    @Test
    void chunkContextMatchesAcrossProvinceCellBoundary() {
        assertContextMatchesDirect(8675309L, 760, -8, 775, 7);
    }

    @Test
    void blendIsHalfAndHalfAtBoundaryAndFullyInteriorPastWidth() {
        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(-42L, -2000, 3000);
        double width = sample.distanceToBoundary();
        assertEquals(1.0, sample.interiorBlend(width), EPSILON);
        assertEquals(0.5, sample.interiorBlend(width * 2.0), EPSILON);
        assertEquals(1.0, sample.interiorBlend(0.0), EPSILON);
    }

    private static void assertContextMatchesDirect(long seed, int minX, int minZ, int maxX, int maxZ) {
        GeologyProvinceSampler.Context context = GeologyProvinceSampler.context(seed, minX, minZ, maxX, maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                assertEquals(
                        GeologyProvinceSampler.sample(seed, x, z),
                        context.sample(x, z),
                        "province context diverged at " + x + "," + z
                );
            }
        }
    }

    private static void assertSample(
            long seed,
            int x,
            int z,
            GeologyProvince province,
            int cellX,
            int cellZ,
            int siteX,
            int siteZ,
            GeologyProvince neighborProvince,
            int neighborCellX,
            int neighborCellZ,
            int neighborSiteX,
            int neighborSiteZ,
            double boundaryDistance
    ) {
        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(seed, x, z);
        assertEquals(province, sample.province());
        assertEquals(cellX, sample.sourceCellX());
        assertEquals(cellZ, sample.sourceCellZ());
        assertEquals(siteX, sample.siteX());
        assertEquals(siteZ, sample.siteZ());
        assertEquals(neighborProvince, sample.neighborProvince());
        assertEquals(neighborCellX, sample.neighborCellX());
        assertEquals(neighborCellZ, sample.neighborCellZ());
        assertEquals(neighborSiteX, sample.neighborSiteX());
        assertEquals(neighborSiteZ, sample.neighborSiteZ());
        assertEquals(boundaryDistance, sample.distanceToBoundary(), EPSILON);
    }
}
