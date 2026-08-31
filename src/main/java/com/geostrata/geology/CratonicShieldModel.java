package com.geostrata.geology;

/**
 * Deterministic block-scale architecture for the experimental Cratonic Shield province.
 *
 * <p>Old metamorphic basement dominates: broad warped gneiss/schist terranes contain
 * narrower quartzite belts and occasional elongated marble lenses. The shared structural
 * offset shifts those bodies across terrain folds and tectonic faults without introducing
 * another structural simulator.</p>
 */
public final class CratonicShieldModel {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double TERRANE_SPACING = 520.0;
    private static final double SCHIST_HALF_WIDTH = 105.0;
    private static final double QUARTZITE_HALF_WIDTH = 14.0;
    private static final double TERRANE_WARP_AMPLITUDE = 52.0;
    private static final double TERRANE_WARP_WAVELENGTH = 760.0;
    private static final double TERRANE_MAX_DIP = 0.075;

    private static final int MARBLE_CELL_SIZE = 256;
    private static final double MARBLE_CELL_MARGIN = 72.0;
    private static final double MARBLE_CELL_SPAN = MARBLE_CELL_SIZE - MARBLE_CELL_MARGIN * 2.0;
    private static final double MARBLE_ACTIVATION_CHANCE = 0.60;

    private static final long TERRANE_ANGLE_SALT = 0x6A09E667F3BCC909L;
    private static final long TERRANE_PHASE_SALT = 0xBB67AE8584CAA73BL;
    private static final long TERRANE_WARP_PHASE_SALT = 0x3C6EF372FE94F82BL;
    private static final long TERRANE_DIP_SALT = 0xA54FF53A5F1D36F1L;
    private static final long MARBLE_ACTIVE_SALT = 0x510E527FADE682D1L;
    private static final long MARBLE_X_SALT = 0x9B05688C2B3E6C1FL;
    private static final long MARBLE_Y_SALT = 0x1F83D9ABFB41BD6BL;
    private static final long MARBLE_Z_SALT = 0x5BE0CD19137E2179L;
    private static final long MARBLE_ALONG_SALT = 0x243F6A8885A308D3L;
    private static final long MARBLE_ACROSS_SALT = 0x13198A2E03707344L;
    private static final long MARBLE_VERTICAL_SALT = 0xA4093822299F31D0L;

    private CratonicShieldModel() {
    }

    public static Context forSite(long worldSeed, int siteX, int siteZ, double seaLevel) {
        double angle = TWO_PI * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, TERRANE_ANGLE_SALT);
        double dip = TERRANE_MAX_DIP * signed(
                GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, TERRANE_DIP_SALT)
        );
        return new Context(
                worldSeed,
                siteX,
                siteZ,
                Math.sin(angle),
                Math.cos(angle),
                TERRANE_SPACING * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, TERRANE_PHASE_SALT),
                TWO_PI * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, TERRANE_WARP_PHASE_SALT),
                dip,
                seaLevel
        );
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
        return forSite(worldSeed, siteX, siteZ, seaLevel)
                .column(x, z, structuralOffset)
                .sample(y);
    }

    private static double roll(long worldSeed, int cellX, int cellZ, long salt) {
        return GeologyDeterminism.unitRoll(worldSeed, cellX, 0, cellZ, salt);
    }

    private static double periodicDistance(double coordinate, double spacing) {
        return Math.abs(coordinate - Math.rint(coordinate / spacing) * spacing);
    }

    private static double signed(double roll) {
        return roll * 2.0 - 1.0;
    }

    public static final class Context {
        private final long worldSeed;
        private final int siteX;
        private final int siteZ;
        private final double strikeSin;
        private final double strikeCos;
        private final double terranePhase;
        private final double warpPhase;
        private final double dip;
        private final double seaLevel;

        private Context(
                long worldSeed,
                int siteX,
                int siteZ,
                double strikeSin,
                double strikeCos,
                double terranePhase,
                double warpPhase,
                double dip,
                double seaLevel
        ) {
            this.worldSeed = worldSeed;
            this.siteX = siteX;
            this.siteZ = siteZ;
            this.strikeSin = strikeSin;
            this.strikeCos = strikeCos;
            this.terranePhase = terranePhase;
            this.warpPhase = warpPhase;
            this.dip = dip;
            this.seaLevel = seaLevel;
        }

        public Column column(int x, int z, double structuralOffset) {
            double dx = (double) x - siteX;
            double dz = (double) z - siteZ;
            double along = dx * strikeCos + dz * strikeSin;
            double across = -dx * strikeSin + dz * strikeCos;
            double warp = TERRANE_WARP_AMPLITUDE
                    * Math.sin(TWO_PI * along / TERRANE_WARP_WAVELENGTH + warpPhase);
            double terraneCoordinate = across + warp + terranePhase;

            MarbleLens marble = marbleLens(x, z, structuralOffset);
            return new Column(
                    terraneCoordinate,
                    dip,
                    structuralOffset,
                    marble.horizontalRadius,
                    marble.centerY,
                    marble.radiusY,
                    marble.active
            );
        }

        private MarbleLens marbleLens(int x, int z, double structuralOffset) {
            int cellX = Math.floorDiv(x, MARBLE_CELL_SIZE);
            int cellZ = Math.floorDiv(z, MARBLE_CELL_SIZE);
            boolean active = roll(worldSeed, cellX, cellZ, MARBLE_ACTIVE_SALT) < MARBLE_ACTIVATION_CHANCE;
            if (!active) {
                return MarbleLens.INACTIVE;
            }

            int originX = cellX * MARBLE_CELL_SIZE;
            int originZ = cellZ * MARBLE_CELL_SIZE;
            double centerX = originX + MARBLE_CELL_MARGIN
                    + MARBLE_CELL_SPAN * roll(worldSeed, cellX, cellZ, MARBLE_X_SALT);
            double centerZ = originZ + MARBLE_CELL_MARGIN
                    + MARBLE_CELL_SPAN * roll(worldSeed, cellX, cellZ, MARBLE_Z_SALT);
            double centerY = seaLevel - 44.0
                    + 88.0 * roll(worldSeed, cellX, cellZ, MARBLE_Y_SALT)
                    + structuralOffset * 0.35;
            double radiusAlong = 38.0 + 22.0 * roll(worldSeed, cellX, cellZ, MARBLE_ALONG_SALT);
            double radiusAcross = 18.0 + 10.0 * roll(worldSeed, cellX, cellZ, MARBLE_ACROSS_SALT);
            double radiusY = 10.0 + 10.0 * roll(worldSeed, cellX, cellZ, MARBLE_VERTICAL_SALT);

            double bodyDx = x - centerX;
            double bodyDz = z - centerZ;
            double bodyAlong = bodyDx * strikeCos + bodyDz * strikeSin;
            double bodyAcross = -bodyDx * strikeSin + bodyDz * strikeCos;
            double horizontalRadius = square(bodyAlong / radiusAlong) + square(bodyAcross / radiusAcross);
            return new MarbleLens(true, horizontalRadius, centerY, radiusY);
        }
    }

    public record Column(
            double terraneCoordinate,
            double dip,
            double structuralOffset,
            double marbleHorizontalRadius,
            double marbleCenterY,
            double marbleRadiusY,
            boolean marbleActive
    ) {
        public Sample sample(double y) {
            if (marbleActive) {
                double vertical = (y - marbleCenterY) / marbleRadiusY;
                if (marbleHorizontalRadius + vertical * vertical <= 1.0) {
                    return new Sample("marble", "marble_lens");
                }
            }

            double fabricCoordinate = terraneCoordinate + (y - structuralOffset) * dip;
            double distance = periodicDistance(fabricCoordinate, TERRANE_SPACING);
            if (distance <= QUARTZITE_HALF_WIDTH) {
                return new Sample("quartzite", "quartzite_belt");
            }
            if (distance <= SCHIST_HALF_WIDTH) {
                return new Sample("schist", "metamorphic_terrane");
            }
            return new Sample("gneiss", "basement_terrane");
        }
    }

    public record Sample(String lithology, String bodyStyle) {
    }

    private record MarbleLens(boolean active, double horizontalRadius, double centerY, double radiusY) {
        private static final MarbleLens INACTIVE = new MarbleLens(false, Double.POSITIVE_INFINITY, 0.0, 1.0);
    }

    private static double square(double value) {
        return value * value;
    }
}
