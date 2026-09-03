package com.geostrata.geology;

import java.util.Optional;

/**
 * Deterministic semantic hydrocarbon reservoir field.
 *
 * <p>The field owns no blocks, fluids or extra worldgen pass. It projects sparse
 * reservoir bodies into sedimentary geology so gameplay indicators and optional
 * integrations can query the same reproducible answer.</p>
 */
public final class HydrocarbonReservoirField {
    static final int CELL_SIZE = 384;
    static final int GAS_CELL_SIZE = 192;
    private static final int CELL_MARGIN = 96;
    private static final long ACTIVATION_SALT = 0x3A6F_696C_7365_6570L;
    private static final long GEOMETRY_SALT = 0x5265_7365_7276_6F69L;
    private static final long SEEP_SALT = 0x5365_6570_706F_696EL;
    private static final long GAS_SALT = 0x6669_7265_6461_6D70L;

    private HydrocarbonReservoirField() {
    }

    public static Optional<Reservoir> sample(
            long worldSeed,
            int x,
            int z,
            GeologyResolver.Result geology
    ) {
        if (geology == null) {
            throw new IllegalArgumentException("resolved geology is required");
        }
        LithologyCatalog.Entry lithology = LithologyCatalog.current().byId().get(geology.lithology());
        if (lithology == null) {
            return Optional.empty();
        }
        return sample(worldSeed, x, z, lithology.rockClass(), lithology.genesis());
    }

    /**
     * Returns a deterministic 0..1 gas-proneness signal for underground indicator use.
     * This is recomputable geology, not a simulated gas volume or finite resource state.
     */
    public static double gasPotential(
            long worldSeed,
            int x,
            int y,
            int z,
            GeologyResolver.Result geology
    ) {
        if (geology == null) {
            throw new IllegalArgumentException("resolved geology is required");
        }
        LithologyCatalog.Entry lithology = LithologyCatalog.current().byId().get(geology.lithology());
        if (lithology == null) {
            return 0.0;
        }
        return gasPotential(worldSeed, x, y, z, lithology.rockClass(), lithology.genesis());
    }

    static Optional<Reservoir> sample(
            long worldSeed,
            int x,
            int z,
            String rockClass,
            String genesis
    ) {
        double chance = activationChance(rockClass, genesis);
        if (chance <= 0.0) {
            return Optional.empty();
        }

        int cellX = Math.floorDiv(x, CELL_SIZE);
        int cellZ = Math.floorDiv(z, CELL_SIZE);
        long activation = hash(worldSeed, cellX, cellZ, ACTIVATION_SALT);
        if (unit(activation) >= chance) {
            return Optional.empty();
        }

        long geometry = hash(worldSeed, cellX, cellZ, GEOMETRY_SALT);
        int usable = CELL_SIZE - CELL_MARGIN * 2;
        int centerX = cellX * CELL_SIZE + CELL_MARGIN + (int) Math.floor(unit(geometry) * usable);
        int centerZ = cellZ * CELL_SIZE + CELL_MARGIN + (int) Math.floor(unit(mix64(geometry)) * usable);
        int radiusX = 48 + (int) Math.floor(unit(mix64(geometry ^ 0x41L)) * 49.0);
        int radiusZ = 36 + (int) Math.floor(unit(mix64(geometry ^ 0x82L)) * 45.0);
        double pressure = 0.35 + unit(mix64(geometry ^ 0x1234L)) * 0.65;

        double dx = (x - centerX) / (double) radiusX;
        double dz = (z - centerZ) / (double) radiusZ;
        double normalizedDistance = dx * dx + dz * dz;
        if (normalizedDistance > 1.0) {
            return Optional.empty();
        }

        long seep = hash(worldSeed, cellX, cellZ, SEEP_SALT);
        double angle = unit(seep) * Math.PI * 2.0;
        double offset = 0.25 + unit(mix64(seep)) * 0.45;
        int seepX = centerX + (int) Math.round(Math.cos(angle) * radiusX * offset);
        int seepZ = centerZ + (int) Math.round(Math.sin(angle) * radiusZ * offset);

        return Optional.of(new Reservoir(
                cellX,
                cellZ,
                centerX,
                centerZ,
                radiusX,
                radiusZ,
                pressure,
                1.0 - normalizedDistance,
                seepX,
                seepZ
        ));
    }

    static double gasPotential(
            long worldSeed,
            int x,
            int y,
            int z,
            String rockClass,
            String genesis
    ) {
        double sourceAffinity = gasSourceAffinity(rockClass, genesis);
        if (sourceAffinity <= 0.0) {
            return 0.0;
        }

        double maturity = clamp01((80.0 - y) / 128.0);
        if (maturity <= 0.0) {
            return 0.0;
        }

        int cellX = Math.floorDiv(x, GAS_CELL_SIZE);
        int cellZ = Math.floorDiv(z, GAS_CELL_SIZE);
        long gas = hash(worldSeed, cellX, cellZ, GAS_SALT);
        if (unit(gas) >= 0.16 * sourceAffinity) {
            return 0.0;
        }

        double localRichness = 0.55 + unit(mix64(gas)) * 0.45;
        return clamp01(sourceAffinity * maturity * localRichness);
    }

    static double activationChance(String rockClass, String genesis) {
        if (!"sedimentary".equals(rockClass) || genesis == null) {
            return 0.0;
        }
        return switch (genesis) {
            case "mudrock" -> 0.22;
            case "carbonate" -> 0.14;
            case "quartz_sandstone" -> 0.12;
            case "silt_clastic" -> 0.08;
            case "coarse_clastic" -> 0.05;
            default -> 0.04;
        };
    }

    static double gasSourceAffinity(String rockClass, String genesis) {
        if (!"sedimentary".equals(rockClass) || genesis == null) {
            return 0.0;
        }
        return switch (genesis) {
            case "mudrock" -> 1.0;
            case "silt_clastic" -> 0.75;
            case "carbonate" -> 0.70;
            case "quartz_sandstone" -> 0.60;
            case "coarse_clastic" -> 0.40;
            default -> 0.50;
        };
    }

    private static long hash(long seed, int cellX, int cellZ, long salt) {
        long value = seed ^ salt;
        value ^= (long) cellX * 0x9E37_79B9_7F4A_7C15L;
        value ^= (long) cellZ * 0xC2B2_AE3D_27D4_EB4FL;
        return mix64(value);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Reservoir(
            int cellX,
            int cellZ,
            int centerX,
            int centerZ,
            int radiusX,
            int radiusZ,
            double pressure,
            double concentration,
            int seepX,
            int seepZ
    ) {
        public Reservoir {
            if (radiusX <= 0 || radiusZ <= 0
                    || pressure < 0.0 || pressure > 1.0
                    || concentration < 0.0 || concentration > 1.0) {
                throw new IllegalArgumentException("hydrocarbon reservoir values are outside their valid ranges");
            }
        }
    }
}
