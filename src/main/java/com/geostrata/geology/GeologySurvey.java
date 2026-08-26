package com.geostrata.geology;

/**
 * Pure diagnostic search over GeoStrata's deterministic regional suitability model.
 *
 * <p>This does not inspect generated chunks or locate actual rock bodies. It samples
 * the province/profile model only, so it is safe to use before chunks exist and does
 * not introduce chunk-order-dependent behavior.</p>
 */
public final class GeologySurvey {
    private static final double WEIGHT_EPSILON = 1.0E-12;

    private GeologySurvey() {
    }

    public static Result findBest(
            long worldSeed,
            int originX,
            int originZ,
            String lithology,
            GeologyProvinceProfiles.Snapshot profiles,
            int radius,
            int step
    ) {
        if (!profiles.loaded()) {
            throw new IllegalStateException("GeoStrata province profiles have not been loaded yet");
        }
        if (!profiles.lithologyIds().contains(lithology)) {
            throw new IllegalArgumentException("unknown lithology: " + lithology);
        }
        if (radius < 0) {
            throw new IllegalArgumentException("survey radius must not be negative");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("survey step must be positive");
        }

        Result best = null;
        for (int offsetX = -radius; offsetX <= radius; offsetX += step) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ += step) {
                long candidateX = (long) originX + offsetX;
                long candidateZ = (long) originZ + offsetZ;
                if (candidateX < Integer.MIN_VALUE || candidateX > Integer.MAX_VALUE
                        || candidateZ < Integer.MIN_VALUE || candidateZ > Integer.MAX_VALUE) {
                    continue;
                }

                int x = (int) candidateX;
                int z = (int) candidateZ;
                GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(worldSeed, x, z);
                double weight = profiles.effectiveWeight(sample, lithology);
                long dx = (long) x - originX;
                long dz = (long) z - originZ;
                long distanceSquared = dx * dx + dz * dz;
                Result candidate = new Result(x, z, weight, distanceSquared, sample.province());

                if (isBetter(candidate, best)) {
                    best = candidate;
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException("survey produced no valid sample points");
        }
        return best;
    }

    private static boolean isBetter(Result candidate, Result best) {
        if (best == null) {
            return true;
        }
        if (candidate.weight() > best.weight() + WEIGHT_EPSILON) {
            return true;
        }
        if (Math.abs(candidate.weight() - best.weight()) > WEIGHT_EPSILON) {
            return false;
        }
        if (candidate.distanceSquared() != best.distanceSquared()) {
            return candidate.distanceSquared() < best.distanceSquared();
        }
        if (candidate.x() != best.x()) {
            return candidate.x() < best.x();
        }
        return candidate.z() < best.z();
    }

    public record Result(
            int x,
            int z,
            double weight,
            long distanceSquared,
            GeologyProvince province
    ) {
        public double distance() {
            return Math.sqrt(distanceSquared);
        }
    }
}
