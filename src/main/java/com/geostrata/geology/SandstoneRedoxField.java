package com.geostrata.geology;

import java.util.Optional;

/** Deterministic roll-front districts used to gate sandstone-hosted uranium. */
public final class SandstoneRedoxField {
    public static final String BODY_STYLE = "sandstone_redox_front";

    private static final int CELL_SIZE = 320;
    private static final long ACTIVATION_SALT = 0x7265_646F_785F_6672L;
    private static final long GEOMETRY_SALT = 0x6672_6F6E_745F_6765L;

    private SandstoneRedoxField() {
    }

    public static Optional<String> bodyStyle(
            long worldSeed,
            int x,
            int z,
            String lithology,
            GeologyProvince province
    ) {
        if (!"sandstone".equals(lithology) || activationChance(province) == 0.0) {
            return Optional.empty();
        }
        return contains(worldSeed, x, z, province) ? Optional.of(BODY_STYLE) : Optional.empty();
    }

    static boolean contains(long worldSeed, int x, int z, GeologyProvince province) {
        double activationChance = activationChance(province);
        if (activationChance == 0.0) {
            return false;
        }

        int cellX = Math.floorDiv(x, CELL_SIZE);
        int cellZ = Math.floorDiv(z, CELL_SIZE);
        if (unit(hash(worldSeed, cellX, cellZ, ACTIVATION_SALT)) >= activationChance) {
            return false;
        }

        long geometry = hash(worldSeed, cellX, cellZ, GEOMETRY_SALT);
        double centerX = cellX * (double) CELL_SIZE + CELL_SIZE * 0.5 + (unit(geometry) - 0.5) * 64.0;
        geometry = mix64(geometry);
        double centerZ = cellZ * (double) CELL_SIZE + CELL_SIZE * 0.5 + (unit(geometry) - 0.5) * 64.0;
        geometry = mix64(geometry);
        double angle = unit(geometry) * Math.PI * 2.0;
        geometry = mix64(geometry);
        double halfLength = 112.0 + unit(geometry) * 48.0;
        geometry = mix64(geometry);
        double halfWidth = 32.0 + unit(geometry) * 16.0;
        geometry = mix64(geometry);
        double curveAmplitude = 12.0 + unit(geometry) * 12.0;
        geometry = mix64(geometry);
        double phase = unit(geometry) * Math.PI * 2.0;

        double dx = x - centerX;
        double dz = z - centerZ;
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double along = dx * cos + dz * sin;
        if (Math.abs(along) > halfLength) {
            return false;
        }
        double across = -dx * sin + dz * cos;
        double curve = Math.sin((along / halfLength) * Math.PI * 1.5 + phase) * curveAmplitude;
        return Math.abs(across - curve) <= halfWidth;
    }

    private static double activationChance(GeologyProvince province) {
        if (province == null) {
            return 0.0;
        }
        return switch (province) {
            case SEDIMENTARY_BASIN -> 0.72;
            case RIFT_PROVINCE -> 0.58;
            default -> 0.0;
        };
    }

    private static long hash(long worldSeed, int cellX, int cellZ, long salt) {
        long value = worldSeed ^ salt;
        value ^= cellX * 0x9E3779B97F4A7C15L;
        value ^= cellZ * 0xC2B2AE3D27D4EB4FL;
        return mix64(value);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
