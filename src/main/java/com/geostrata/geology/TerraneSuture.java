package com.geostrata.geology;

/**
 * Bounded dipping contact between the two nearest geological province sites.
 *
 * <p>The X/Z Voronoi boundary remains authoritative at the reference elevation.
 * Only columns close enough to that boundary can switch terrane with Y. Dip
 * variation reuses the two sites' existing tectonic phases; no separate noise
 * or mutable plate state is introduced.</p>
 */
public final class TerraneSuture {
    public static final double MAX_HORIZONTAL_SHIFT_BLOCKS = 96.0;
    private static final double MIN_SHIFT_PER_VERTICAL_BLOCK = 0.18;
    private static final double SHIFT_RANGE_PER_VERTICAL_BLOCK = 0.20;
    private static final double TWO_PI = Math.PI * 2.0;

    private TerraneSuture() {
    }

    public static Contact forColumn(
            GeologyProvinceSampler.Sample sample,
            TectonicStructuralField.Context primaryTectonics,
            TectonicStructuralField.Context neighborTectonics,
            double referenceY
    ) {
        if (sample == null || primaryTectonics == null || neighborTectonics == null) {
            throw new IllegalArgumentException("terrane suture inputs must not be null");
        }
        if (!Double.isFinite(referenceY)) {
            throw new IllegalArgumentException("terrane suture reference Y must be finite");
        }

        Site primary = new Site(sample.province(), sample.siteX(), sample.siteZ());
        Site neighbor = new Site(sample.neighborProvince(), sample.neighborSiteX(), sample.neighborSiteZ());
        boolean primaryIsFirst = primary.compareTo(neighbor) <= 0;
        TectonicStructuralField.Context first = primaryIsFirst ? primaryTectonics : neighborTectonics;
        TectonicStructuralField.Context second = primaryIsFirst ? neighborTectonics : primaryTectonics;

        double phaseAverage = 0.5 * (
                unitPhase(first.foldSecondaryPhase()) + unitPhase(second.foldSecondaryPhase())
        );
        double magnitude = MIN_SHIFT_PER_VERTICAL_BLOCK
                + SHIFT_RANGE_PER_VERTICAL_BLOCK * phaseAverage;
        double direction = Math.sin(first.foldPhase() - second.foldPhase()) < 0.0 ? -1.0 : 1.0;
        double canonicalSurfaceDistance = Math.copySign(sample.distanceToBoundary(), primaryIsFirst ? 1.0 : -1.0);
        return new Contact(primaryIsFirst, canonicalSurfaceDistance, direction * magnitude, referenceY);
    }

    public static boolean canCross(GeologyProvinceSampler.Sample sample) {
        if (sample == null) {
            throw new IllegalArgumentException("province sample must not be null");
        }
        return sample.distanceToBoundary() <= MAX_HORIZONTAL_SHIFT_BLOCKS;
    }

    private static double unitPhase(double phase) {
        double normalized = phase / TWO_PI;
        return normalized - Math.floor(normalized);
    }

    private record Site(GeologyProvince province, int x, int z) implements Comparable<Site> {
        private Site {
            if (province == null) {
                throw new IllegalArgumentException("terrane site province must not be null");
            }
        }

        @Override
        public int compareTo(Site other) {
            int xOrder = Integer.compare(x, other.x);
            if (xOrder != 0) {
                return xOrder;
            }
            int zOrder = Integer.compare(z, other.z);
            if (zOrder != 0) {
                return zOrder;
            }
            return province.id().compareTo(other.province.id());
        }
    }

    public record Contact(
            boolean primaryIsFirst,
            double canonicalSurfaceDistanceBlocks,
            double horizontalShiftPerVerticalBlock,
            double referenceY
    ) {
        public Contact {
            if (!Double.isFinite(canonicalSurfaceDistanceBlocks)
                    || !Double.isFinite(horizontalShiftPerVerticalBlock)
                    || !Double.isFinite(referenceY)) {
                throw new IllegalArgumentException("terrane suture contact must be finite");
            }
        }

        public boolean usesPrimary(double y) {
            if (!Double.isFinite(y)) {
                throw new IllegalArgumentException("terrane suture Y must be finite");
            }
            double shift = clamp(
                    horizontalShiftPerVerticalBlock * (y - referenceY),
                    -MAX_HORIZONTAL_SHIFT_BLOCKS,
                    MAX_HORIZONTAL_SHIFT_BLOCKS
            );
            boolean firstOwns = canonicalSurfaceDistanceBlocks - shift >= 0.0;
            return firstOwns == primaryIsFirst;
        }

        public double dipDegrees() {
            return Math.toDegrees(Math.atan2(1.0, Math.abs(horizontalShiftPerVerticalBlock)));
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
