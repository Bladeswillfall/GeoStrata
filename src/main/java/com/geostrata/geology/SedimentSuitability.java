package com.geostrata.geology;

/** Pure scoring model for shallow sediment placement from terrain and environmental evidence. */
public final class SedimentSuitability {
    private static final double FLAT_RELIEF_BLOCKS = 12.0;
    private static final double FLAT_SLOPE = 0.18;
    private static final double VALLEY_PROMINENCE_BLOCKS = 8.0;

    private SedimentSuitability() {
    }

    public static Evidence evidence(
            TerrainMorphologySample terrain,
            boolean submerged,
            boolean preferredBiome
    ) {
        if (terrain == null) {
            throw new IllegalArgumentException("terrain sample must not be null");
        }
        double reliefFlatness = 1.0 - clamp01(terrain.relief() / FLAT_RELIEF_BLOCKS);
        double slopeFlatness = 1.0 - clamp01(terrain.slopeMagnitude() / FLAT_SLOPE);
        double flatness = (reliefFlatness + slopeFlatness) * 0.5;
        double valley = clamp01(-terrain.prominence() / VALLEY_PROMINENCE_BLOCKS);
        return new Evidence(flatness, valley, submerged, preferredBiome);
    }

    public static double chance(Evidence evidence, Weights weights) {
        if (evidence == null || weights == null) {
            throw new IllegalArgumentException("sediment evidence and weights must not be null");
        }
        double chance = weights.baseChance()
                + weights.flatnessWeight() * evidence.flatness()
                + weights.valleyWeight() * evidence.valley()
                + weights.submergedWeight() * (evidence.submerged() ? 1.0 : 0.0)
                + weights.preferredBiomeBonus() * (evidence.preferredBiome() ? 1.0 : 0.0);
        return clamp01(chance);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Evidence(double flatness, double valley, boolean submerged, boolean preferredBiome) {
        public Evidence {
            requireUnit(flatness, "flatness");
            requireUnit(valley, "valley");
        }
    }

    public record Weights(
            String id,
            double baseChance,
            double flatnessWeight,
            double valleyWeight,
            double submergedWeight,
            double preferredBiomeBonus
    ) {
        public Weights {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("sediment profile id must not be blank");
            }
            requireUnit(baseChance, "base chance");
            requireSignedUnit(flatnessWeight, "flatness weight");
            requireSignedUnit(valleyWeight, "valley weight");
            requireSignedUnit(submergedWeight, "submerged weight");
            requireSignedUnit(preferredBiomeBonus, "preferred biome bonus");
        }
    }

    private static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
        }
    }

    private static void requireSignedUnit(double value, String name) {
        if (!Double.isFinite(value) || value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between -1 and 1");
        }
    }
}
