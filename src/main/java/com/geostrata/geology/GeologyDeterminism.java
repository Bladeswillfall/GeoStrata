package com.geostrata.geology;

/** Stable coordinate hashing for geology decisions that must not consume feature RNG state. */
public final class GeologyDeterminism {
    private GeologyDeterminism() {
    }

    /**
     * Returns a stable value in [0, 1) from world seed, block coordinates and a
     * caller-specific salt. Keep regression tests for any salt used by runtime
     * worldgen: changing this mapping changes unexplored chunks in existing worlds.
     */
    public static double unitRoll(long worldSeed, int x, int y, int z, long salt) {
        long value = worldSeed ^ salt;
        value += (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value = Long.rotateLeft(value, 31);
        value += (long) y * 0x165667B19E3779F9L;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        value ^= value >>> 33;
        return (value >>> 11) * 0x1.0p-53;
    }
}
