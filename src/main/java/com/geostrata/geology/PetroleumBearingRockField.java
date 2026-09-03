package com.geostrata.geology;

/**
 * Resolves petroleum-bearing sedimentary host expressions from the existing reservoir field.
 *
 * <p>This class owns no blocks and performs no worldgen pass. A column lazily samples only the
 * reservoir flavours needed by shale or sandstone encountered by the existing sedimentary writer.</p>
 */
public final class PetroleumBearingRockField {
    static final double OIL_SHALE_MIN_CONCENTRATION = 0.60;
    static final double OIL_SANDS_MIN_CONCENTRATION = 0.55;
    static final double OIL_SANDS_MIN_PRESSURE = 0.55;

    private PetroleumBearingRockField() {
    }

    public static Column column(long worldSeed, int x, int z) {
        return new Column(worldSeed, x, z);
    }

    public enum Expression {
        NONE,
        OIL_SHALE,
        OIL_SANDS
    }

    /** One lazily evaluated worldgen column; at most one reservoir sample per supported host family. */
    public static final class Column {
        private final long worldSeed;
        private final int x;
        private final int z;
        private boolean shaleResolved;
        private boolean sandsResolved;
        private HydrocarbonReservoirField.Reservoir shaleReservoir;
        private HydrocarbonReservoirField.Reservoir sandsReservoir;

        private Column(long worldSeed, int x, int z) {
            this.worldSeed = worldSeed;
            this.x = x;
            this.z = z;
        }

        public Expression expression(String lithology) {
            return switch (lithology) {
                case "shale" -> oilShale(shaleReservoir()) ? Expression.OIL_SHALE : Expression.NONE;
                case "sandstone" -> oilSands(sandsReservoir()) ? Expression.OIL_SANDS : Expression.NONE;
                default -> Expression.NONE;
            };
        }

        private HydrocarbonReservoirField.Reservoir shaleReservoir() {
            if (!shaleResolved) {
                shaleReservoir = HydrocarbonReservoirField.sample(
                        worldSeed,
                        x,
                        z,
                        "sedimentary",
                        "mudrock"
                ).orElse(null);
                shaleResolved = true;
            }
            return shaleReservoir;
        }

        private HydrocarbonReservoirField.Reservoir sandsReservoir() {
            if (!sandsResolved) {
                sandsReservoir = HydrocarbonReservoirField.sample(
                        worldSeed,
                        x,
                        z,
                        "sedimentary",
                        "quartz_sandstone"
                ).orElse(null);
                sandsResolved = true;
            }
            return sandsReservoir;
        }
    }

    static boolean oilShale(HydrocarbonReservoirField.Reservoir reservoir) {
        return reservoir != null && reservoir.concentration() >= OIL_SHALE_MIN_CONCENTRATION;
    }

    static boolean oilSands(HydrocarbonReservoirField.Reservoir reservoir) {
        return reservoir != null
                && reservoir.concentration() >= OIL_SANDS_MIN_CONCENTRATION
                && reservoir.pressure() >= OIL_SANDS_MIN_PRESSURE;
    }
}
