package com.geostrata.geology;

/**
 * Deterministic province-scale tectonic deformation layered on top of the base
 * stratigraphic field. Folds are smooth and long-wavelength; faults are discrete
 * block offsets across a seed-derived regional fault family.
 *
 * <p>The model is intentionally small. It adds the structural primitives needed
 * for readable anticline/syncline-style folding and faulted contacts without
 * creating a second terrain generator or mutable plate simulation.</p>
 */
public final class TectonicStructuralField {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double SECOND_HARMONIC_WEIGHT = 0.22;

    private static final long FOLD_ANGLE_SALT = 0x6A09E667F3BCC909L;
    private static final long FOLD_PHASE_SALT = 0xBB67AE8584CAA73BL;
    private static final long FOLD_SECONDARY_PHASE_SALT = 0x3C6EF372FE94F82BL;
    private static final long FOLD_SCALE_SALT = 0xA54FF53A5F1D36F1L;
    private static final long FAULT_ANGLE_SALT = 0x510E527FADE682D1L;
    private static final long FAULT_PHASE_SALT = 0x9B05688C2B3E6C1FL;
    private static final long FAULT_SCALE_SALT = 0x1F83D9ABFB41BD6BL;
    private static final long FAULT_THROW_SALT = 0x5BE0CD19137E2179L;

    private TectonicStructuralField() {
    }

    public static Context forSite(
            long worldSeed,
            GeologyProvince province,
            int siteX,
            int siteZ,
            double cycleThicknessBlocks
    ) {
        if (province == null) {
            throw new IllegalArgumentException("geological province must not be null");
        }
        if (!Double.isFinite(cycleThicknessBlocks) || cycleThicknessBlocks < 1.0) {
            throw new IllegalArgumentException("cycle thickness must be finite and at least one block");
        }

        Settings settings = settingsFor(province);
        double foldAngle = TWO_PI * roll(worldSeed, siteX, siteZ, FOLD_ANGLE_SALT);
        double faultAngle = TWO_PI * roll(worldSeed, siteX, siteZ, FAULT_ANGLE_SALT);
        double foldScale = 0.80 + 0.40 * roll(worldSeed, siteX, siteZ, FOLD_SCALE_SALT);
        double faultScale = 0.85 + 0.30 * roll(worldSeed, siteX, siteZ, FAULT_SCALE_SALT);
        double foldWavelength = settings.foldWavelengthBlocks() * foldScale;
        double faultSpacing = settings.faultSpacingBlocks() * faultScale;
        double faultThrow = cycleThicknessBlocks
                * settings.faultThrowCycleFraction()
                * (0.70 + 0.30 * roll(worldSeed, siteX, siteZ, FAULT_THROW_SALT));

        return new Context(
                siteX,
                siteZ,
                cycleThicknessBlocks * settings.foldAmplitudeCycleFraction(),
                foldWavelength,
                Math.cos(foldAngle),
                Math.sin(foldAngle),
                TWO_PI * roll(worldSeed, siteX, siteZ, FOLD_PHASE_SALT),
                TWO_PI * roll(worldSeed, siteX, siteZ, FOLD_SECONDARY_PHASE_SALT),
                faultSpacing,
                Math.cos(faultAngle),
                Math.sin(faultAngle),
                faultSpacing * roll(worldSeed, siteX, siteZ, FAULT_PHASE_SALT),
                faultThrow,
                settings.faultRegime()
        );
    }

    /** Flat context used by pure terrain-response tests that do not opt into tectonics. */
    public static Context flat() {
        return new Context(0, 0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, FaultRegime.NONE);
    }

    public static Settings settingsFor(GeologyProvince province) {
        if (province == null) {
            throw new IllegalArgumentException("geological province must not be null");
        }
        return switch (province) {
            case SEDIMENTARY_BASIN -> new Settings(0.06, 720.0, 960.0, 0.18, FaultRegime.NORMAL);
            case CRATONIC_SHIELD -> new Settings(0.03, 900.0, 1200.0, 0.12, FaultRegime.ANCIENT);
            case OROGENIC_BELT -> new Settings(0.32, 420.0, 520.0, 0.60, FaultRegime.REVERSE);
            case VOLCANIC_ARC -> new Settings(0.14, 540.0, 640.0, 0.35, FaultRegime.MIXED);
            case RIFT_PROVINCE -> new Settings(0.10, 680.0, 360.0, 0.55, FaultRegime.NORMAL);
        };
    }

    private static double roll(long worldSeed, int siteX, int siteZ, long salt) {
        return GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, salt);
    }

    private static double wave(double phase, double secondaryPhase) {
        return (Math.sin(phase) + SECOND_HARMONIC_WEIGHT * Math.sin(phase * 2.0 + secondaryPhase))
                / (1.0 + SECOND_HARMONIC_WEIGHT);
    }

    private static double faultState(long index, FaultRegime regime) {
        int phase = (int) Math.floorMod(index, 4L);
        return switch (regime) {
            case NONE -> 0.0;
            case NORMAL -> switch (phase) {
                case 0 -> 0.0;
                case 1 -> -1.0;
                case 2 -> -2.0;
                default -> -1.0;
            };
            case REVERSE -> switch (phase) {
                case 0 -> 0.0;
                case 1 -> 1.0;
                case 2 -> 2.0;
                default -> 1.0;
            };
            case MIXED -> switch (phase) {
                case 0, 2 -> 0.0;
                case 1 -> 1.0;
                default -> -1.0;
            };
            case ANCIENT -> switch (phase) {
                case 0, 2 -> 0.0;
                case 1 -> 0.5;
                default -> -0.5;
            };
        };
    }

    public enum FaultRegime {
        NONE,
        NORMAL,
        REVERSE,
        MIXED,
        ANCIENT
    }

    public record Settings(
            double foldAmplitudeCycleFraction,
            double foldWavelengthBlocks,
            double faultSpacingBlocks,
            double faultThrowCycleFraction,
            FaultRegime faultRegime
    ) {
        public Settings {
            if (!finiteFraction(foldAmplitudeCycleFraction)
                    || !Double.isFinite(foldWavelengthBlocks) || foldWavelengthBlocks < 1.0
                    || !Double.isFinite(faultSpacingBlocks) || faultSpacingBlocks < 1.0
                    || !finiteFraction(faultThrowCycleFraction)
                    || faultRegime == null) {
                throw new IllegalArgumentException("invalid tectonic structural settings");
            }
        }
    }

    public record Sample(
            double foldOffset,
            double faultOffset,
            double distanceToFault,
            FaultRegime faultRegime
    ) {
        public double totalOffset() {
            return foldOffset + faultOffset;
        }
    }

    public record Context(
            int siteX,
            int siteZ,
            double foldAmplitudeBlocks,
            double foldWavelengthBlocks,
            double foldCos,
            double foldSin,
            double foldPhase,
            double foldSecondaryPhase,
            double faultSpacingBlocks,
            double faultCos,
            double faultSin,
            double faultPhaseBlocks,
            double faultThrowBlocks,
            FaultRegime faultRegime
    ) {
        public Context {
            if (!Double.isFinite(foldAmplitudeBlocks) || foldAmplitudeBlocks < 0.0
                    || !Double.isFinite(foldWavelengthBlocks) || foldWavelengthBlocks < 1.0
                    || !Double.isFinite(foldCos) || !Double.isFinite(foldSin)
                    || !Double.isFinite(foldPhase) || !Double.isFinite(foldSecondaryPhase)
                    || !Double.isFinite(faultSpacingBlocks) || faultSpacingBlocks < 1.0
                    || !Double.isFinite(faultCos) || !Double.isFinite(faultSin)
                    || !Double.isFinite(faultPhaseBlocks)
                    || !Double.isFinite(faultThrowBlocks) || faultThrowBlocks < 0.0
                    || faultRegime == null) {
                throw new IllegalArgumentException("invalid tectonic structural context");
            }
        }

        public Sample sample(int x, int z) {
            if (faultRegime == FaultRegime.NONE && foldAmplitudeBlocks == 0.0) {
                return new Sample(0.0, 0.0, Double.POSITIVE_INFINITY, FaultRegime.NONE);
            }

            double dx = (double) x - siteX;
            double dz = (double) z - siteZ;
            double alongFold = dx * foldCos + dz * foldSin;
            double foldAtPoint = wave(
                    TWO_PI * alongFold / foldWavelengthBlocks + foldPhase,
                    foldSecondaryPhase
            );
            double foldAtAnchor = wave(foldPhase, foldSecondaryPhase);
            double foldOffset = 0.5 * foldAmplitudeBlocks * (foldAtPoint - foldAtAnchor);

            if (faultRegime == FaultRegime.NONE || faultThrowBlocks == 0.0) {
                return new Sample(foldOffset, 0.0, Double.POSITIVE_INFINITY, faultRegime);
            }

            double acrossFaults = -dx * faultSin + dz * faultCos;
            double faultCoordinate = (acrossFaults + faultPhaseBlocks) / faultSpacingBlocks;
            long faultBlock = (long) Math.floor(faultCoordinate);
            long anchorBlock = (long) Math.floor(faultPhaseBlocks / faultSpacingBlocks);
            double faultOffset = faultThrowBlocks
                    * (faultState(faultBlock, faultRegime) - faultState(anchorBlock, faultRegime));
            double distanceToFault = Math.abs(faultCoordinate - Math.rint(faultCoordinate)) * faultSpacingBlocks;
            return new Sample(foldOffset, faultOffset, distanceToFault, faultRegime);
        }
    }

    private static boolean finiteFraction(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
