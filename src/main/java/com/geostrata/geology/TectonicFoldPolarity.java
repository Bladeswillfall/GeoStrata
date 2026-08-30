package com.geostrata.geology;

/**
 * Sparse overturned-limb transform derived from the existing tectonic fold family.
 *
 * <p>The transform changes only the vertical component of stratigraphic orientation.
 * A scale of +1 preserves ordinary younging-up beds, zero represents a near-vertical
 * hinge, and a negative scale reverses stratigraphic polarity on an overturned limb.
 * Existing fold/fault phases supply all deterministic variation; no second random
 * structural field is introduced.</p>
 */
public final class TectonicFoldPolarity {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double ACTIVATION_FRACTION = 0.32;

    private TectonicFoldPolarity() {
    }

    public static Profile normal() {
        return Profile.NORMAL;
    }

    public static Profile forField(
            GeologyProvince province,
            TectonicStructuralField.Context field,
            double cycleThicknessBlocks,
            double structuralAnchorY
    ) {
        if (province == null || field == null) {
            throw new IllegalArgumentException("fold polarity requires province and tectonic field");
        }
        if (!Double.isFinite(cycleThicknessBlocks) || cycleThicknessBlocks < 1.0) {
            throw new IllegalArgumentException("cycle thickness must be finite and at least one block");
        }
        if (!Double.isFinite(structuralAnchorY)) {
            throw new IllegalArgumentException("structural anchor Y must be finite");
        }
        if (province != GeologyProvince.OROGENIC_BELT
                || field.foldAmplitudeBlocks() == 0.0
                || unitPhase(field.foldSecondaryPhase()) >= ACTIVATION_FRACTION) {
            return normal();
        }

        double strength = 2.15 + 0.30 * unitPhase(field.foldPhase());
        double limbDirection = Math.sin(field.foldSecondaryPhase()) < 0.0 ? -1.0 : 1.0;
        double phaseFraction = field.faultPhaseBlocks() / field.faultSpacingBlocks();
        double pivotLocalY = structuralAnchorY
                - cycleThicknessBlocks * (0.75 + phaseFraction);
        return new Profile(true, strength, limbDirection, pivotLocalY);
    }

    private static double unitPhase(double phase) {
        double normalized = phase / TWO_PI;
        return normalized - Math.floor(normalized);
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
