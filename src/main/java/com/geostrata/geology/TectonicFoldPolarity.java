package com.geostrata.geology;

/**
 * Sparse overturned-limb transform derived from the existing tectonic fold family.
 *
 * <p>The transform changes only the vertical component of stratigraphic orientation.
 * A scale of +1 preserves ordinary younging-up beds, zero represents a near-vertical
 * hinge, and a negative scale reverses stratigraphic polarity on an overturned limb.
 * The existing fold axis, wavelength, phase and closure envelope own the geometry.</p>
 */
public final class TectonicFoldPolarity {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double ACTIVATION_CHANCE = 0.32;

    private static final long ACTIVE_SALT = 0x082EFA98EC4E6C89L;
    private static final long STRENGTH_SALT = 0x452821E638D01377L;
    private static final long LIMB_SALT = 0xBE5466CF34E90C6CL;
    private static final long PIVOT_SALT = 0xC0AC29B7C97C50DDL;

    private TectonicFoldPolarity() {
    }

    public static Profile normal() {
        return Profile.NORMAL;
    }

    public static Profile forSite(
            long worldSeed,
            GeologyProvince province,
            int siteX,
            int siteZ,
            double cycleThicknessBlocks,
            double structuralAnchorY
    ) {
        if (province == null) {
            throw new IllegalArgumentException("geological province must not be null");
        }
        if (!Double.isFinite(cycleThicknessBlocks) || cycleThicknessBlocks < 1.0) {
            throw new IllegalArgumentException("cycle thickness must be finite and at least one block");
        }
        if (!Double.isFinite(structuralAnchorY)) {
            throw new IllegalArgumentException("structural anchor Y must be finite");
        }
        if (province != GeologyProvince.OROGENIC_BELT
                || roll(worldSeed, siteX, siteZ, ACTIVE_SALT) >= ACTIVATION_CHANCE) {
            return normal();
        }

        double strength = 2.15 + 0.30 * roll(worldSeed, siteX, siteZ, STRENGTH_SALT);
        double limbDirection = roll(worldSeed, siteX, siteZ, LIMB_SALT) < 0.5 ? -1.0 : 1.0;
        double pivotLocalY = structuralAnchorY
                - cycleThicknessBlocks * (0.75 + roll(worldSeed, siteX, siteZ, PIVOT_SALT));
        return new Profile(true, strength, limbDirection, pivotLocalY);
    }

    private static double roll(long worldSeed, int siteX, int siteZ, long salt) {
        return GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, salt);
    }

    public record Profile(
            boolean active,
            double overturnStrength,
            double limbDirection,
            double pivotLocalY
    ) {
        private static final Profile NORMAL = new Profile(false, 0.0, 1.0, 0.0);

        public Profile {
            if (!Double.isFinite(overturnStrength) || overturnStrength < 0.0
                    || !Double.isFinite(limbDirection) || Math.abs(limbDirection) != 1.0
                    || !Double.isFinite(pivotLocalY)) {
                throw new IllegalArgumentException("invalid tectonic fold polarity profile");
            }
            if (!active && overturnStrength != 0.0) {
                throw new IllegalArgumentException("inactive fold polarity must have zero strength");
            }
        }

        public Transform transform(TectonicStructuralField.Context field, int x, int z) {
            if (field == null) {
                throw new IllegalArgumentException("tectonic field must not be null");
            }
            if (!active || field.foldAmplitudeBlocks() == 0.0) {
                return Transform.NORMAL;
            }

            double dx = (double) x - field.siteX();
            double dz = (double) z - field.siteZ();
            double alongFold = dx * field.foldCos() + dz * field.foldSin();
            double phase = TWO_PI * alongFold / field.foldWavelengthBlocks() + field.foldPhase();
            double selectedLimb = Math.max(0.0, limbDirection * Math.cos(phase));
            double compression = TectonicFoldClosures.envelope(field, x, z)
                    * selectedLimb * selectedLimb;
            return new Transform(1.0 - overturnStrength * compression, pivotLocalY);
        }
    }

    public record Transform(double verticalScale, double pivotLocalY) {
        private static final Transform NORMAL = new Transform(1.0, 0.0);

        public Transform {
            if (!Double.isFinite(verticalScale) || !Double.isFinite(pivotLocalY)) {
                throw new IllegalArgumentException("invalid tectonic fold polarity transform");
            }
        }

        public boolean preservesVerticalScale() {
            return Double.compare(verticalScale, 1.0) == 0;
        }

        public boolean overturned() {
            return verticalScale < 0.0;
        }

        /**
         * Maps world Y to the equivalent Y consumed by the existing stratigraphic
         * field while preserving the shared structural vertical offset.
         */
        public double stratigraphicY(double worldY, double structuralVerticalOffset) {
            if (!Double.isFinite(worldY) || !Double.isFinite(structuralVerticalOffset)) {
                throw new IllegalArgumentException("stratigraphic transform inputs must be finite");
            }
            double localY = worldY - structuralVerticalOffset;
            double transformedLocalY = pivotLocalY + verticalScale * (localY - pivotLocalY);
            return structuralVerticalOffset + transformedLocalY;
        }
    }
}
