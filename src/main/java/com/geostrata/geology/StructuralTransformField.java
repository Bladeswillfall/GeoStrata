package com.geostrata.geology;

/**
 * Pure diagnostic structural transform built from deterministic site orientation plus
 * reviewed physical scales. It produces vertical coordinate offsets only and therefore
 * models steep dip, open folds and a representative fault plane, but not overturned or
 * recumbent structures that require a fuller XYZ transform.
 */
public final class StructuralTransformField {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final long STRUCTURAL_ANGLE_SALT = 0x6A09E667F3BCC909L;
    private static final long FOLD_PHASE_SALT = 0xBB67AE8584CAA73BL;
    private static final long FAULT_OFFSET_SALT = 0x3C6EF372FE94F82BL;
    private static final long FAULT_SIGN_SALT = 0xA54FF53A5F1D36F1L;

    private StructuralTransformField() {
    }

    public static Field forSite(
            long worldSeed,
            int siteX,
            int siteZ,
            StructuralTransformProfiles.Profile profile,
            StructuralDeformationResponse.Result response
    ) {
        if (profile == null || response == null) {
            throw new IllegalArgumentException("structural transform inputs must not be null");
        }

        double normalAngle = TWO_PI * GeologyDeterminism.unitRoll(
                worldSeed,
                siteX,
                0,
                siteZ,
                STRUCTURAL_ANGLE_SALT
        );
        double foldPhase = TWO_PI * GeologyDeterminism.unitRoll(
                worldSeed,
                siteX,
                0,
                siteZ,
                FOLD_PHASE_SALT
        );
        double faultOffsetUnit = GeologyDeterminism.unitRoll(
                worldSeed,
                siteX,
                0,
                siteZ,
                FAULT_OFFSET_SALT
        ) * 2.0 - 1.0;
        double faultSign = GeologyDeterminism.unitRoll(
                worldSeed,
                siteX,
                0,
                siteZ,
                FAULT_SIGN_SALT
        ) < 0.5 ? -1.0 : 1.0;

        double dipDegrees = profile.maxDipDegrees() * response.dipPotential();
        double dipGradient = Math.tan(Math.toRadians(dipDegrees));
        double foldAmplitude = profile.maxFoldAmplitudeBlocks() * response.foldPotential();
        double faultDisplacement = profile.maxFaultDisplacementBlocks() * response.faultPotential() * faultSign;

        return new Field(
                siteX,
                siteZ,
                Math.cos(normalAngle),
                Math.sin(normalAngle),
                dipDegrees,
                dipGradient,
                foldAmplitude,
                profile.foldWavelengthBlocks(),
                foldPhase,
                faultOffsetUnit * profile.faultPlaneOffsetRangeBlocks(),
                faultDisplacement
        );
    }

    public static Sample blend(Sample primary, Sample neighbor, double interiorBlend) {
        if (primary == null || neighbor == null || !Double.isFinite(interiorBlend)) {
            throw new IllegalArgumentException("structural transform blend inputs must be finite and non-null");
        }
        double clamped = Math.max(0.0, Math.min(1.0, interiorBlend));
        double primaryShare = 0.5 + 0.5 * clamped;
        return new Sample(
                mix(primary.dipOffset(), neighbor.dipOffset(), primaryShare),
                mix(primary.foldOffset(), neighbor.foldOffset(), primaryShare),
                mix(primary.faultOffset(), neighbor.faultOffset(), primaryShare)
        );
    }

    private static double mix(double primary, double neighbor, double primaryShare) {
        return primary * primaryShare + neighbor * (1.0 - primaryShare);
    }

    public record Field(
            int siteX,
            int siteZ,
            double normalX,
            double normalZ,
            double dipDegrees,
            double dipGradient,
            double foldAmplitudeBlocks,
            double foldWavelengthBlocks,
            double foldPhase,
            double faultPlaneOffsetBlocks,
            double faultDisplacementBlocks
    ) {
        public Field {
            if (!Double.isFinite(normalX) || !Double.isFinite(normalZ)) {
                throw new IllegalArgumentException("structural normal must be finite");
            }
            double normalLength = Math.hypot(normalX, normalZ);
            if (Math.abs(normalLength - 1.0) > 1.0e-9) {
                throw new IllegalArgumentException("structural normal must be unit length");
            }
            requireFinite(dipDegrees, "dip degrees");
            requireFinite(dipGradient, "dip gradient");
            requireNonNegative(foldAmplitudeBlocks, "fold amplitude");
            if (!Double.isFinite(foldWavelengthBlocks) || foldWavelengthBlocks <= 0.0) {
                throw new IllegalArgumentException("fold wavelength must be finite and positive");
            }
            requireFinite(foldPhase, "fold phase");
            requireFinite(faultPlaneOffsetBlocks, "fault-plane offset");
            requireFinite(faultDisplacementBlocks, "fault displacement");
        }

        public Sample sample(double x, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("structural sample coordinates must be finite");
            }
            double alongNormal = alongNormal(x, z);
            return new Sample(
                    dipGradient * alongNormal,
                    foldOffset(alongNormal),
                    faultOffset(alongNormal)
            );
        }

        private double alongNormal(double x, double z) {
            return (x - siteX) * normalX + (z - siteZ) * normalZ;
        }

        private double foldOffset(double alongNormal) {
            if (foldAmplitudeBlocks == 0.0) {
                return 0.0;
            }
            double phaseAtSample = TWO_PI * alongNormal / foldWavelengthBlocks + foldPhase;
            return foldAmplitudeBlocks * (Math.sin(phaseAtSample) - Math.sin(foldPhase));
        }

        private double faultOffset(double alongNormal) {
            if (faultDisplacementBlocks == 0.0) {
                return 0.0;
            }
            double sampleSide = alongNormal >= faultPlaneOffsetBlocks ? 1.0 : 0.0;
            double siteSide = 0.0 >= faultPlaneOffsetBlocks ? 1.0 : 0.0;
            return (sampleSide - siteSide) * faultDisplacementBlocks;
        }

        private static void requireFinite(double value, String name) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }

        private static void requireNonNegative(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(name + " must be finite and non-negative");
            }
        }
    }

    public record Sample(double dipOffset, double foldOffset, double faultOffset) {
        public double totalOffset() {
            return dipOffset + foldOffset + faultOffset;
        }

        public double transformY(double worldY) {
            if (!Double.isFinite(worldY)) {
                throw new IllegalArgumentException("world Y must be finite");
            }
            return worldY - totalOffset();
        }
    }
}
