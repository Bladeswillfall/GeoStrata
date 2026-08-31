package com.geostrata.geology;

/**
 * Deterministic background architecture for experimental Orogenic Belt provinces.
 *
 * <p>The belt is a strongly deformed metamorphic gradient rather than a repeating
 * palette: a high-grade gneiss core grades outward through schist and phyllite into
 * slate, with quartzite ribbons and common elongated marble lenses. Correlated
 * parent-aware strata remain higher authority wherever they already own the host stone.</p>
 */
public final class OrogenicBeltModel {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double GNEISS_CORE_HALF_WIDTH = 92.0;
    private static final double SCHIST_ZONE_HALF_WIDTH = 225.0;
    private static final double PHYLLITE_ZONE_HALF_WIDTH = 340.0;
    private static final double QUARTZITE_RIBBON_OFFSET = 155.0;
    private static final double QUARTZITE_RIBBON_HALF_WIDTH = 11.0;
    private static final double BELT_WARP_AMPLITUDE = 64.0;
    private static final double BELT_WARP_WAVELENGTH = 520.0;
    private static final double BELT_MAX_DIP = 0.18;

    private static final int MARBLE_CELL_SIZE = 224;
    private static final double MARBLE_CELL_MARGIN = 62.0;
    private static final double MARBLE_CELL_SPAN = MARBLE_CELL_SIZE - MARBLE_CELL_MARGIN * 2.0;
    private static final double MARBLE_ACTIVATION_CHANCE = 0.78;

    private static final long BELT_ANGLE_SALT = 0xCBBB9D5DC1059ED8L;
    private static final long BELT_WARP_PHASE_SALT = 0x629A292A367CD507L;
    private static final long BELT_DIP_SALT = 0x9159015A3070DD17L;
    private static final long MARBLE_ACTIVE_SALT = 0x152FECD8F70E5939L;
    private static final long MARBLE_X_SALT = 0x67332667FFC00B31L;
    private static final long MARBLE_Y_SALT = 0x8EB44A8768581511L;
    private static final long MARBLE_Z_SALT = 0xDB0C2E0D64F98FA7L;
    private static final long MARBLE_ALONG_SALT = 0x47B5481DBEFA4FA4L;
    private static final long MARBLE_ACROSS_SALT = 0x0FC19DC68B8CD5B5L;
    private static final long MARBLE_VERTICAL_SALT = 0x240CA1CC77AC9C65L;

    private OrogenicBeltModel() {
    }

    public static Context forSite(long worldSeed, int siteX, int siteZ, double seaLevel) {
        double angle = TWO_PI * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, BELT_ANGLE_SALT);
        double dip = BELT_MAX_DIP * signed(
                GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, BELT_DIP_SALT)
        );
        return new Context(
                worldSeed,
                siteX,
                siteZ,
                Math.sin(angle),
                Math.cos(angle),
                TWO_PI * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, BELT_WARP_PHASE_SALT),
                dip,
                seaLevel
        );
    }

    private static double roll(long worldSeed, int cellX, int cellZ, long salt) {
        return GeologyDeterminism.unitRoll(worldSeed, cellX, 0, cellZ, salt);
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
        private final double warpPhase;
        private final double dip;
        private final double seaLevel;

        private Context(
                long worldSeed,
                int siteX,
                int siteZ,
                double strikeSin,
                double strikeCos,
                double warpPhase,
                double dip,
                double seaLevel
        ) {
            this.worldSeed = worldSeed;
            this.siteX = siteX;
            this.siteZ = siteZ;
            this.strikeSin = strikeSin;
            this.strikeCos = strikeCos;
            this.warpPhase = warpPhase;
            this.dip = dip;
            this.seaLevel = seaLevel;
        }

        public Column column(int x, int z, double structuralOffset) {
            double dx = (double) x - siteX;
            double dz = (double) z - siteZ;
            double along = dx * strikeCos + dz * strikeSin;
            double across = -dx * strikeSin + dz * strikeCos;
            double warp = BELT_WARP_AMPLITUDE
                    * (Math.sin(TWO_PI * along / BELT_WARP_WAVELENGTH + warpPhase) - Math.sin(warpPhase));
            MarbleLens marble = marbleLens(x, z);
            return new Column(
                    across + warp,
                    dip,
                    structuralOffset,
                    marble.horizontalRadius,
                    marble.baseCenterY,
                    marble.radiusY,
                    marble.active
            );
        }

        private MarbleLens marbleLens(int x, int z) {
            int cellX = Math.floorDiv(x, MARBLE_CELL_SIZE);
            int cellZ = Math.floorDiv(z, MARBLE_CELL_SIZE);
            if (roll(worldSeed, cellX, cellZ, MARBLE_ACTIVE_SALT) >= MARBLE_ACTIVATION_CHANCE) {
                return MarbleLens.INACTIVE;
            }

            int originX = cellX * MARBLE_CELL_SIZE;
            int originZ = cellZ * MARBLE_CELL_SIZE;
            double centerX = originX + MARBLE_CELL_MARGIN
                    + MARBLE_CELL_SPAN * roll(worldSeed, cellX, cellZ, MARBLE_X_SALT);
            double centerZ = originZ + MARBLE_CELL_MARGIN
                    + MARBLE_CELL_SPAN * roll(worldSeed, cellX, cellZ, MARBLE_Z_SALT);
            double baseCenterY = seaLevel - 38.0
                    + 92.0 * roll(worldSeed, cellX, cellZ, MARBLE_Y_SALT);
            double radiusAlong = 36.0 + 20.0 * roll(worldSeed, cellX, cellZ, MARBLE_ALONG_SALT);
            double radiusAcross = 16.0 + 10.0 * roll(worldSeed, cellX, cellZ, MARBLE_ACROSS_SALT);
            double radiusY = 12.0 + 12.0 * roll(worldSeed, cellX, cellZ, MARBLE_VERTICAL_SALT);

            double bodyDx = x - centerX;
            double bodyDz = z - centerZ;
            double bodyAlong = bodyDx * strikeCos + bodyDz * strikeSin;
            double bodyAcross = -bodyDx * strikeSin + bodyDz * strikeCos;
            double horizontalRadius = square(bodyAlong / radiusAlong) + square(bodyAcross / radiusAcross);
            return new MarbleLens(true, horizontalRadius, baseCenterY, radiusY);
        }
    }

    public record Column(
            double beltCoordinate,
            double dip,
            double structuralOffset,
            double marbleHorizontalRadius,
            double marbleBaseCenterY,
            double marbleRadiusY,
            boolean marbleActive
    ) {
        public Sample sample(double y) {
            return sample(y, structuralOffset);
        }

        /** Samples the same X/Z architecture with a Y-specific shared structural offset. */
        public Sample sample(double y, double dynamicStructuralOffset) {
            if (marbleActive) {
                double marbleCenterY = marbleBaseCenterY + dynamicStructuralOffset * 0.45;
                double vertical = (y - marbleCenterY) / marbleRadiusY;
                if (marbleHorizontalRadius + vertical * vertical <= 1.0) {
                    return new Sample("marble", "marble_lens");
                }
            }

            double fabric = beltCoordinate + (y - dynamicStructuralOffset) * dip;
            double distance = Math.abs(fabric);
            if (Math.abs(distance - QUARTZITE_RIBBON_OFFSET) <= QUARTZITE_RIBBON_HALF_WIDTH) {
                return new Sample("quartzite", "quartzite_ribbon");
            }
            if (distance <= GNEISS_CORE_HALF_WIDTH) {
                return new Sample("gneiss", "high_grade_core");
            }
            if (distance <= SCHIST_ZONE_HALF_WIDTH) {
                return new Sample("schist", "schist_zone");
            }
            if (distance <= PHYLLITE_ZONE_HALF_WIDTH) {
                return new Sample("phyllite", "phyllite_zone");
            }
            return new Sample("slate", "outer_metamorphic_zone");
        }
    }

    public record Sample(String lithology, String bodyStyle) {
    }

    private record MarbleLens(boolean active, double horizontalRadius, double baseCenterY, double radiusY) {
        private static final MarbleLens INACTIVE = new MarbleLens(false, Double.POSITIVE_INFINITY, 0.0, 1.0);
    }

    private static double square(double value) {
        return value * value;
    }
}
