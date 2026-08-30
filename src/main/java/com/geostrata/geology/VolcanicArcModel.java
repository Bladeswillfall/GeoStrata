package com.geostrata.geology;

/**
 * Deterministic block-scale architecture for the experimental Volcanic Arc province.
 *
 * <p>This is deliberately province-specific rather than a generic architecture engine.
 * It supplies a metamorphic basement, mafic dikes/sills, local rhyolitic bodies and
 * breccia halos while reusing the shared structural offset supplied by GeoStrata's
 * terrain-aware field.</p>
 */
public final class VolcanicArcModel {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double DIKE_SPACING = 144.0;
    private static final double DIKE_HALF_THICKNESS = 1.35;
    private static final double DIKE_BRECCIA_HALF_THICKNESS = 3.25;
    private static final double SILL_SPACING = 160.0;
    private static final double SILL_HALF_THICKNESS = 2.25;
    private static final int RHYOLITE_CELL_SIZE = 256;
    private static final double RHYOLITE_CELL_MARGIN = 64.0;
    private static final double RHYOLITE_CELL_SPAN = RHYOLITE_CELL_SIZE - RHYOLITE_CELL_MARGIN * 2.0;
    private static final double RHYOLITE_HALO_SCALE = 1.18;

    private static final long DIKE_ANGLE_SALT = 0x8EBC6AF09C88C6E3L;
    private static final long DIKE_PHASE_SALT = 0x589965CC75374CC3L;
    private static final long SILL_PHASE_SALT = 0x1D8E4E27C47D124FL;
    private static final long RHYOLITE_X_SALT = 0xEB44ACCAB455D165L;
    private static final long RHYOLITE_Y_SALT = 0xA4093822299F31D0L;
    private static final long RHYOLITE_Z_SALT = 0x13198A2E03707344L;
    private static final long RHYOLITE_RADIUS_XZ_SALT = 0x243F6A8885A308D3L;
    private static final long RHYOLITE_RADIUS_Y_SALT = 0x452821E638D01377L;

    private VolcanicArcModel() {
    }

    public static Sample sample(
            long worldSeed,
            int x,
            double y,
            int z,
            int siteX,
            int siteZ,
            double structuralOffset,
            double seaLevel
    ) {
        double angle = TWO_PI * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, DIKE_ANGLE_SALT);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double dx = (double) x - siteX;
        double dz = (double) z - siteZ;
        double across = -dx * sin + dz * cos;
        double dikePhase = DIKE_SPACING
                * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, DIKE_PHASE_SALT);
        double dikeDistance = periodicDistance(across + dikePhase, DIKE_SPACING);
        if (dikeDistance <= DIKE_HALF_THICKNESS) {
            return new Sample("basalt", "dike");
        }
        if (dikeDistance <= DIKE_BRECCIA_HALF_THICKNESS) {
            return new Sample("breccia", "dike_breccia");
        }

        RhyoliteBody rhyolite = rhyoliteBody(worldSeed, x, z, structuralOffset, seaLevel);
        double rhyoliteRadius = rhyolite.normalizedRadius(x, y, z);
        if (rhyoliteRadius <= 1.0) {
            return new Sample("rhyolite", "rhyolite_body");
        }
        if (rhyoliteRadius <= RHYOLITE_HALO_SCALE) {
            return new Sample("breccia", "rhyolite_breccia");
        }

        double sillPhase = SILL_SPACING
                * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, SILL_PHASE_SALT);
        double sillDistance = periodicDistance(y - structuralOffset + sillPhase, SILL_SPACING);
        if (sillDistance <= SILL_HALF_THICKNESS) {
            return new Sample("basalt", "sill");
        }

        return new Sample("gneiss", "basement");
    }

    private static RhyoliteBody rhyoliteBody(
            long worldSeed,
            int x,
            int z,
            double structuralOffset,
            double seaLevel
    ) {
        int cellX = Math.floorDiv(x, RHYOLITE_CELL_SIZE);
        int cellZ = Math.floorDiv(z, RHYOLITE_CELL_SIZE);
        int originX = cellX * RHYOLITE_CELL_SIZE;
        int originZ = cellZ * RHYOLITE_CELL_SIZE;
        double centerX = originX + RHYOLITE_CELL_MARGIN + RHYOLITE_CELL_SPAN
                * roll(worldSeed, cellX, cellZ, RHYOLITE_X_SALT);
        double centerZ = originZ + RHYOLITE_CELL_MARGIN + RHYOLITE_CELL_SPAN
                * roll(worldSeed, cellX, cellZ, RHYOLITE_Z_SALT);
        double centerY = seaLevel - 48.0
                + 96.0 * roll(worldSeed, cellX, cellZ, RHYOLITE_Y_SALT)
                + structuralOffset * 0.25;
        double radiusXZ = 28.0 + 20.0 * roll(worldSeed, cellX, cellZ, RHYOLITE_RADIUS_XZ_SALT);
        double radiusY = 14.0 + 16.0 * roll(worldSeed, cellX, cellZ, RHYOLITE_RADIUS_Y_SALT);
        return new RhyoliteBody(centerX, centerY, centerZ, radiusXZ, radiusY);
    }

    private static double roll(long worldSeed, int cellX, int cellZ, long salt) {
        return GeologyDeterminism.unitRoll(worldSeed, cellX, 0, cellZ, salt);
    }

    private static double periodicDistance(double coordinate, double spacing) {
        return Math.abs(coordinate - Math.rint(coordinate / spacing) * spacing);
    }

    public record Sample(String lithology, String bodyStyle) {
    }

    private record RhyoliteBody(
            double centerX,
            double centerY,
            double centerZ,
            double radiusXZ,
            double radiusY
    ) {
        private double normalizedRadius(double x, double y, double z) {
            double horizontalX = (x - centerX) / radiusXZ;
            double horizontalZ = (z - centerZ) / radiusXZ;
            double vertical = (y - centerY) / radiusY;
            return horizontalX * horizontalX + horizontalZ * horizontalZ + vertical * vertical;
        }
    }
}
