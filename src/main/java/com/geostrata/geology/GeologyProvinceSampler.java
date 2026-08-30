package com.geostrata.geology;

import java.util.ArrayList;
import java.util.List;

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

        Candidate best = null;
        Candidate second = null;

        for (int offsetX = -SEARCH_RADIUS; offsetX <= SEARCH_RADIUS; offsetX++) {
            for (int offsetZ = -SEARCH_RADIUS; offsetZ <= SEARCH_RADIUS; offsetZ++) {
                int cellX = baseCellX + offsetX;
                int cellZ = baseCellZ + offsetZ;
                Candidate candidate = candidate(worldSeed, blockX, blockZ, cellX, cellZ);

                if (best == null || candidate.precedes(best)) {
                    second = best;
                    best = candidate;
                } else if (second == null || candidate.precedes(second)) {
                    second = candidate;
                }
            }
        }

        if (best == null || second == null) {
            throw new IllegalStateException("GeoStrata province sampler did not produce two candidate sites");
        }

        return sample(best, second);
    }

    /**
     * Precomputes the seed-derived candidate sites required by every point in a
     * rectangular region. Chunk worldgen can reuse this immutable context for all
     * 256 columns instead of re-hashing the same coarse Voronoi sites per column.
     */
    public static Context context(
            long worldSeed,
            int minBlockX,
            int minBlockZ,
            int maxBlockX,
            int maxBlockZ
    ) {
        if (minBlockX > maxBlockX || minBlockZ > maxBlockZ) {
            throw new IllegalArgumentException("province context bounds must be ordered");
        }
        int minCellX = Math.floorDiv(minBlockX, CELL_SIZE) - SEARCH_RADIUS;
        int maxCellX = Math.floorDiv(maxBlockX, CELL_SIZE) + SEARCH_RADIUS;
        int minCellZ = Math.floorDiv(minBlockZ, CELL_SIZE) - SEARCH_RADIUS;
        int maxCellZ = Math.floorDiv(maxBlockZ, CELL_SIZE) + SEARCH_RADIUS;
        List<SiteCandidate> sites = new ArrayList<>((maxCellX - minCellX + 1) * (maxCellZ - minCellZ + 1));
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                sites.add(siteCandidate(worldSeed, cellX, cellZ));
            }
        }
        return new Context(sites.toArray(SiteCandidate[]::new));
    }

    private static Candidate candidate(long seed, int blockX, int blockZ, int cellX, int cellZ) {
        int siteX = siteCoordinate(seed, cellX, cellZ, SITE_X_SALT, cellX);
        int siteZ = siteCoordinate(seed, cellX, cellZ, SITE_Z_SALT, cellZ);
        long dx = (long) blockX - siteX;
        long dz = (long) blockZ - siteZ;
        long distanceSquared = dx * dx + dz * dz;
        GeologyProvince province = provinceFor(hash(seed, cellX, cellZ, PROVINCE_SALT));
        return new Candidate(province, cellX, cellZ, siteX, siteZ, distanceSquared);
    }

    private static SiteCandidate siteCandidate(long seed, int cellX, int cellZ) {
        return new SiteCandidate(
                provinceFor(hash(seed, cellX, cellZ, PROVINCE_SALT)),
                cellX,
                cellZ,
                siteCoordinate(seed, cellX, cellZ, SITE_X_SALT, cellX),
                siteCoordinate(seed, cellX, cellZ, SITE_Z_SALT, cellZ)
        );
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

    private static Sample sample(Candidate best, Candidate second) {
        return new Sample(
                best.province,
                best.cellX,
                best.cellZ,
                best.siteX,
                best.siteZ,
                best.distanceSquared,
                second.province,
                second.cellX,
                second.cellZ,
                second.siteX,
                second.siteZ,
                second.distanceSquared
        );
    }

    private static Sample sample(
            SiteCandidate best,
            long bestDistanceSquared,
            SiteCandidate second,
            long secondDistanceSquared
    ) {
        return new Sample(
                best.province,
                best.cellX,
                best.cellZ,
                best.siteX,
                best.siteZ,
                bestDistanceSquared,
                second.province,
                second.cellX,
                second.cellZ,
                second.siteX,
                second.siteZ,
                secondDistanceSquared
        );
    }

    private static long distanceSquared(int blockX, int blockZ, SiteCandidate site) {
        long dx = (long) blockX - site.siteX;
        long dz = (long) blockZ - site.siteZ;
        return dx * dx + dz * dz;
    }

    private static boolean precedes(
            SiteCandidate candidate,
            long candidateDistance,
            SiteCandidate other,
            long otherDistance
    ) {
        if (other == null || candidateDistance != otherDistance) {
            return other == null || candidateDistance < otherDistance;
        }
        if (candidate.cellX != other.cellX) {
            return candidate.cellX < other.cellX;
        }
        return candidate.cellZ < other.cellZ;
    }

    private record Candidate(
            GeologyProvince province,
            int cellX,
            int cellZ,
            int siteX,
            int siteZ,
            long distanceSquared
    ) {
        private boolean precedes(Candidate other) {
            if (distanceSquared != other.distanceSquared) {
                return distanceSquared < other.distanceSquared;
            }
            if (cellX != other.cellX) {
                return cellX < other.cellX;
            }
            return cellZ < other.cellZ;
        }
    }

    private record SiteCandidate(
            GeologyProvince province,
            int cellX,
            int cellZ,
            int siteX,
            int siteZ
    ) {
    }

    public static final class Context {
        private final SiteCandidate[] sites;

        private Context(SiteCandidate[] sites) {
            this.sites = sites.clone();
        }

        public Sample sample(int blockX, int blockZ) {
            SiteCandidate best = null;
            SiteCandidate second = null;
            long bestDistance = Long.MAX_VALUE;
            long secondDistance = Long.MAX_VALUE;
            for (SiteCandidate candidate : sites) {
                long distance = distanceSquared(blockX, blockZ, candidate);
                if (precedes(candidate, distance, best, bestDistance)) {
                    second = best;
                    secondDistance = bestDistance;
                    best = candidate;
                    bestDistance = distance;
                } else if (precedes(candidate, distance, second, secondDistance)) {
                    second = candidate;
                    secondDistance = distance;
                }
            }
            if (best == null || second == null) {
                throw new IllegalStateException("GeoStrata province context did not produce two candidate sites");
            }
            return GeologyProvinceSampler.sample(best, bestDistance, second, secondDistance);
        }
    }

    public record Sample(
            GeologyProvince province,
            int sourceCellX,
            int sourceCellZ,
            int siteX,
            int siteZ,
            long distanceSquared,
            GeologyProvince neighborProvince,
            int neighborCellX,
            int neighborCellZ,
            int neighborSiteX,
            int neighborSiteZ,
            long neighborDistanceSquared
    ) {
        public double distanceToSite() {
            return Math.sqrt(distanceSquared);
        }

        /**
         * Exact distance to the bisector between the nearest and second-nearest
         * province sites. This is the local Voronoi boundary relevant to
         * blending those two province profiles.
         */
        public double distanceToBoundary() {
            double siteDistance = Math.hypot((double) neighborSiteX - siteX, (double) neighborSiteZ - siteZ);
            if (siteDistance == 0.0) {
                return 0.0;
            }
            return (neighborDistanceSquared - distanceSquared) / (2.0 * siteDistance);
        }

        /**
         * Returns 0 at the nearest province boundary and 1 once the requested
         * interior blend width has been traversed.
         */
        public double interiorBlend(double blendWidthBlocks) {
            if (blendWidthBlocks <= 0.0) {
                return 1.0;
            }
            return Math.min(1.0, Math.max(0.0, distanceToBoundary() / blendWidthBlocks));
        }
    }
}
