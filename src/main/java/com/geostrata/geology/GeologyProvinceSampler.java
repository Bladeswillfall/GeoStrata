package com.geostrata.geology;

/**
 * Stable, chunk-order-independent sampler for broad geological provinces.
 *
 * <p>Province sites are jittered inside a coarse grid and the nearest site
 * owns the queried block. Sampling neighboring cells produces irregular
 * Voronoi-style boundaries without storing per-world state. The algorithm is
 * intentionally pure: the same world seed and block coordinates always return
 * the same result, making it safe for pregeneration, multiplayer and later
 * worldgen features.</p>
 */
public final class GeologyProvinceSampler {
    public static final int CELL_SIZE = 768;

    private static final int SEARCH_RADIUS = 2;
    private static final double SITE_MARGIN = 0.15;
    private static final double SITE_SPAN = 0.70;

    private static final long SITE_X_SALT = 0x3C79AC492BA7B653L;
    private static final long SITE_Z_SALT = 0x1C69B3F74AC4AE35L;
    private static final long PROVINCE_SALT = 0xD1B54A32D192ED03L;

    private GeologyProvinceSampler() {
    }

    public static Sample sample(long worldSeed, int blockX, int blockZ) {
        int baseCellX = Math.floorDiv(blockX, CELL_SIZE);
        int baseCellZ = Math.floorDiv(blockZ, CELL_SIZE);

        long bestDistance = Long.MAX_VALUE;
        int bestCellX = 0;
        int bestCellZ = 0;
        int bestSiteX = 0;
        int bestSiteZ = 0;
        GeologyProvince bestProvince = GeologyProvince.SEDIMENTARY_BASIN;

        for (int offsetX = -SEARCH_RADIUS; offsetX <= SEARCH_RADIUS; offsetX++) {
            for (int offsetZ = -SEARCH_RADIUS; offsetZ <= SEARCH_RADIUS; offsetZ++) {
                int cellX = baseCellX + offsetX;
                int cellZ = baseCellZ + offsetZ;
                int siteX = siteCoordinate(worldSeed, cellX, cellZ, SITE_X_SALT, cellX);
                int siteZ = siteCoordinate(worldSeed, cellX, cellZ, SITE_Z_SALT, cellZ);

                long dx = (long) blockX - siteX;
                long dz = (long) blockZ - siteZ;
                long distance = dx * dx + dz * dz;

                if (distance < bestDistance || (distance == bestDistance && comesBefore(cellX, cellZ, bestCellX, bestCellZ))) {
                    bestDistance = distance;
                    bestCellX = cellX;
                    bestCellZ = cellZ;
                    bestSiteX = siteX;
                    bestSiteZ = siteZ;
                    bestProvince = provinceFor(hash(worldSeed, cellX, cellZ, PROVINCE_SALT));
                }
            }
        }

        return new Sample(bestProvince, bestCellX, bestCellZ, bestSiteX, bestSiteZ, bestDistance);
    }

    private static int siteCoordinate(long seed, int cellX, int cellZ, long salt, int axisCell) {
        double fraction = SITE_MARGIN + unit(hash(seed, cellX, cellZ, salt)) * SITE_SPAN;
        long coordinate = (long) axisCell * CELL_SIZE + (long) Math.floor(fraction * CELL_SIZE);
        return (int) coordinate;
    }

    private static GeologyProvince provinceFor(long hash) {
        int bucket = (int) (unit(hash) * 100.0);
        if (bucket < 28) {
            return GeologyProvince.SEDIMENTARY_BASIN;
        }
        if (bucket < 50) {
            return GeologyProvince.CRATONIC_SHIELD;
        }
        if (bucket < 74) {
            return GeologyProvince.OROGENIC_BELT;
        }
        if (bucket < 88) {
            return GeologyProvince.VOLCANIC_ARC;
        }
        return GeologyProvince.RIFT_PROVINCE;
    }

    private static boolean comesBefore(int cellX, int cellZ, int otherCellX, int otherCellZ) {
        return cellX < otherCellX || (cellX == otherCellX && cellZ < otherCellZ);
    }

    private static long hash(long seed, long cellX, long cellZ, long salt) {
        long value = seed ^ salt;
        value += cellX * 0x9E3779B97F4A7C15L;
        value = Long.rotateLeft(value, 27);
        value ^= cellZ * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;
        return value;
    }

    private static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    public record Sample(
            GeologyProvince province,
            int sourceCellX,
            int sourceCellZ,
            int siteX,
            int siteZ,
            long distanceSquared
    ) {
        public double distanceToSite() {
            return Math.sqrt(distanceSquared);
        }
    }
}
