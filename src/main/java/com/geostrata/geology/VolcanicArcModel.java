package com.geostrata.geology;

/**
 * Deterministic block-scale architecture for the experimental Volcanic Arc province.
 *
 * <p>This is deliberately province-specific rather than a generic architecture engine.
 * It supplies a varied metamorphic basement, mafic dikes/sills, zoned volcanic complexes
 * and their breccia/contact aureoles while reusing the shared structural offset supplied
 * by GeoStrata's terrain-aware field.</p>
 */
public final class VolcanicArcModel {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double DIKE_SPACING = 144.0;
    private static final double DIKE_HALF_THICKNESS = 1.35;
    private static final double DIKE_BRECCIA_HALF_THICKNESS = 3.25;
    private static final double SILL_HALF_THICKNESS = 2.25;
    private static final double SILL_LONG_RADIUS_SCALE = 1.30;
    private static final double SILL_SHORT_RADIUS_SCALE = 0.85;
    private static final double METAMORPHIC_BELT_SPACING = 224.0;
    private static final double SCHIST_HALF_WIDTH = 32.0;
    private static final double QUARTZITE_HALF_WIDTH = 7.0;
    private static final int COMPLEX_CELL_SIZE = 256;
    private static final double COMPLEX_CELL_MARGIN = 64.0;
    private static final double COMPLEX_CELL_SPAN = COMPLEX_CELL_SIZE - COMPLEX_CELL_MARGIN * 2.0;
    private static final double COMPLEX_HALO_SCALE = 1.18;
    private static final double PLUTON_TOP_NORMALIZED_Y = -0.20;
    private static final double GRANITE_CORE_RADIUS = 0.58;
    private static final double PEGMATITE_FERTILE_INNER_RADIUS = 0.42;
    private static final double RHYOLITE_CORE_RADIUS = 0.52;

    private static final long DIKE_ANGLE_SALT = 0x8EBC6AF09C88C6E3L;
    private static final long DIKE_PHASE_SALT = 0x589965CC75374CC3L;
    private static final long METAMORPHIC_ANGLE_SALT = 0xA54FF53A5F1D36F1L;
    private static final long METAMORPHIC_PHASE_SALT = 0x510E527FADE682D1L;
    private static final long COMPLEX_X_SALT = 0xEB44ACCAB455D165L;
    private static final long COMPLEX_Y_SALT = 0xA4093822299F31D0L;
    private static final long COMPLEX_Z_SALT = 0x13198A2E03707344L;
    private static final long COMPLEX_RADIUS_XZ_SALT = 0x243F6A8885A308D3L;
    private static final long COMPLEX_RADIUS_Y_SALT = 0x452821E638D01377L;

    private VolcanicArcModel() {
    }

    public static Context forSite(long worldSeed, int siteX, int siteZ, double seaLevel) {
        double dikeAngle = TWO_PI * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, DIKE_ANGLE_SALT);
        double metamorphicAngle = TWO_PI
                * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, METAMORPHIC_ANGLE_SALT);
        return new Context(
                worldSeed,
                siteX,
                siteZ,
                Math.sin(dikeAngle),
                Math.cos(dikeAngle),
                DIKE_SPACING * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, DIKE_PHASE_SALT),
                Math.sin(metamorphicAngle),
                Math.cos(metamorphicAngle),
                METAMORPHIC_BELT_SPACING
                        * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, METAMORPHIC_PHASE_SALT),
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

    public static final class Context {
        private final long worldSeed;
        private final int siteX;
        private final int siteZ;
        private final double dikeSin;
        private final double dikeCos;
        private final double dikePhase;
        private final double metamorphicSin;
        private final double metamorphicCos;
        private final double metamorphicPhase;
        private final double seaLevel;

        private Context(
                long worldSeed,
                int siteX,
                int siteZ,
                double dikeSin,
                double dikeCos,
                double dikePhase,
                double metamorphicSin,
                double metamorphicCos,
                double metamorphicPhase,
                double seaLevel
        ) {
            this.worldSeed = worldSeed;
            this.siteX = siteX;
            this.siteZ = siteZ;
            this.dikeSin = dikeSin;
            this.dikeCos = dikeCos;
            this.dikePhase = dikePhase;
            this.metamorphicSin = metamorphicSin;
            this.metamorphicCos = metamorphicCos;
            this.metamorphicPhase = metamorphicPhase;
            this.seaLevel = seaLevel;
        }

        public Column column(int x, int z, double structuralOffset) {
            double dx = (double) x - siteX;
            double dz = (double) z - siteZ;
            double dikeAcross = -dx * dikeSin + dz * dikeCos;
            double dikeDistance = periodicDistance(dikeAcross + dikePhase, DIKE_SPACING);
            double metamorphicAcross = -dx * metamorphicSin + dz * metamorphicCos;
            double metamorphicDistance = periodicDistance(
                    metamorphicAcross + metamorphicPhase,
                    METAMORPHIC_BELT_SPACING
            );

            int cellX = Math.floorDiv(x, COMPLEX_CELL_SIZE);
            int cellZ = Math.floorDiv(z, COMPLEX_CELL_SIZE);
            int originX = cellX * COMPLEX_CELL_SIZE;
            int originZ = cellZ * COMPLEX_CELL_SIZE;
            double centerX = originX + COMPLEX_CELL_MARGIN + COMPLEX_CELL_SPAN
                    * roll(worldSeed, cellX, cellZ, COMPLEX_X_SALT);
            double centerZ = originZ + COMPLEX_CELL_MARGIN + COMPLEX_CELL_SPAN
                    * roll(worldSeed, cellX, cellZ, COMPLEX_Z_SALT);
            double centerY = seaLevel - 48.0
                    + 96.0 * roll(worldSeed, cellX, cellZ, COMPLEX_Y_SALT)
                    + structuralOffset * 0.25;
            double radiusXZ = 28.0 + 20.0 * roll(worldSeed, cellX, cellZ, COMPLEX_RADIUS_XZ_SALT);
            double radiusY = 14.0 + 16.0 * roll(worldSeed, cellX, cellZ, COMPLEX_RADIUS_Y_SALT);
            double centerDx = x - centerX;
            double centerDz = z - centerZ;
            double horizontalX = centerDx / radiusXZ;
            double horizontalZ = centerDz / radiusXZ;
            double horizontalRadius = horizontalX * horizontalX + horizontalZ * horizontalZ;
            double sillAlong = centerDx * dikeCos + centerDz * dikeSin;
            double sillAcross = -centerDx * dikeSin + centerDz * dikeCos;
            double sillFootprint = square(sillAlong / (radiusXZ * SILL_LONG_RADIUS_SCALE))
                    + square(sillAcross / (radiusXZ * SILL_SHORT_RADIUS_SCALE));
            double sillCenterY = centerY + radiusY * 0.65 + structuralOffset * 0.75;

            return new Column(
                    dikeDistance,
                    metamorphicDistance,
                    horizontalRadius,
                    centerY,
                    radiusY,
                    sillFootprint,
                    sillCenterY
            );
        }
    }

    private static double square(double value) {
        return value * value;
    }

    public record Column(
            double dikeDistance,
            double metamorphicDistance,
            double complexHorizontalRadius,
            double complexCenterY,
            double complexRadiusY,
            double sillFootprint,
            double sillCenterY
    ) {
        public Sample sample(double y) {
            if (dikeDistance <= DIKE_HALF_THICKNESS) {
                return new Sample("basalt", "dike");
            }
            if (dikeDistance <= DIKE_BRECCIA_HALF_THICKNESS) {
                return new Sample("breccia", "dike_breccia");
            }

            double vertical = (y - complexCenterY) / complexRadiusY;
            double complexRadius = complexHorizontalRadius + vertical * vertical;
            if (complexRadius <= 1.0) {
                return complexRock(vertical, complexRadius);
            }
            if (vertical > PLUTON_TOP_NORMALIZED_Y && complexRadius <= COMPLEX_HALO_SCALE) {
                return new Sample("breccia", "volcanic_breccia");
            }
            if (sillFootprint <= 1.0 && Math.abs(y - sillCenterY) <= SILL_HALF_THICKNESS) {
                return new Sample("basalt", "sill");
            }

            Sample countryRock = countryRock();
            if (vertical <= PLUTON_TOP_NORMALIZED_Y && complexRadius <= COMPLEX_HALO_SCALE) {
                return new Sample(countryRock.lithology(), "contact_aureole");
            }
            return countryRock;
        }

        private Sample complexRock(double vertical, double complexRadius) {
            if (vertical <= PLUTON_TOP_NORMALIZED_Y) {
                if (complexRadius <= GRANITE_CORE_RADIUS) {
                    return new Sample(
                            "granite",
                            complexRadius >= PEGMATITE_FERTILE_INNER_RADIUS
                                    ? "pegmatite_fertile_margin"
                                    : "plutonic_core"
                    );
                }
                return new Sample("diorite", "plutonic_margin");
            }
            return complexRadius <= RHYOLITE_CORE_RADIUS
                    ? new Sample("rhyolite", "rhyolite_body")
                    : new Sample("andesite", "andesite_body");
        }

        private Sample countryRock() {
            if (metamorphicDistance <= QUARTZITE_HALF_WIDTH) {
                return new Sample("quartzite", "metamorphic_belt_core");
            }
            if (metamorphicDistance <= SCHIST_HALF_WIDTH) {
                return new Sample("schist", "metamorphic_belt");
            }
            return new Sample("gneiss", "basement");
        }
    }

    public record Sample(String lithology, String bodyStyle) {
    }
}
