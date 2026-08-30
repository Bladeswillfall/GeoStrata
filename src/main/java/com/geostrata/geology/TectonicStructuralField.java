package com.geostrata.geology;

/**
 * Deterministic province-scale tectonic deformation layered on top of the base
 * stratigraphic field. Folds are smooth and long-wavelength; faults are discrete
 * block offsets across a seed-derived regional fault family.
 *
 * <p>The model is intentionally small. X/Z work resolves once per column. Rift and
 * sedimentary-basin faults additionally shift their trace with Y, producing dipping
 * fault planes without introducing a second 3-D geology engine.</p>
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
    private static final long FAULT_DIP_MAGNITUDE_SALT = 0x243F6A8885A308D3L;
    private static final long FAULT_DIP_DIRECTION_SALT = 0x13198A2E03707344L;

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
        double faultDipShift = faultDipShift(
                worldSeed,
                province,
                siteX,
                siteZ
        );

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
                faultDipShift,
                settings.faultRegime()
        );
    }

    /** Flat context used by pure terrain-response tests that do not opt into tectonics. */
    public static Context flat() {
        return new Context(
                0,
                0,
                0.0,
                1.0,
                1.0,
                0.0,
                0.0,
                0.0,
                1.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                FaultRegime.NONE
        );
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

    private static double faultDipShift(
            long worldSeed,
            GeologyProvince province,
            int siteX,
            int siteZ
    ) {
        double minimum;
        double range;
        switch (province) {
            case SEDIMENTARY_BASIN -> {
                minimum = 0.35;
                range = 0.20;
            }
            case RIFT_PROVINCE -> {
                minimum = 0.55;
                range = 0.30;
            }
            default -> {
                return 0.0;
            }
        }
        double magnitude = minimum + range * roll(worldSeed, siteX, siteZ, FAULT_DIP_MAGNITUDE_SALT);
        return roll(worldSeed, siteX, siteZ, FAULT_DIP_DIRECTION_SALT) < 0.5 ? -magnitude : magnitude;
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

    /** Nearest point on the deterministic fault trace at the requested Y. */
    public record FaultTrace(
            double x,
            double z,
            double distanceToFault,
            long faultIndex,
            FaultRegime faultRegime
    ) {
        public FaultTrace {
            if (!Double.isFinite(x)
                    || !Double.isFinite(z)
                    || Double.isNaN(distanceToFault)
                    || distanceToFault < 0.0
                    || faultRegime == null) {
                throw new IllegalArgumentException("invalid tectonic fault trace");
            }
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
            double faultDipShiftPerVerticalBlock,
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
                    || !Double.isFinite(faultDipShiftPerVerticalBlock)
                    || faultRegime == null) {
                throw new IllegalArgumentException("invalid tectonic structural context");
            }
        }

        /** Legacy X/Z sample, equivalent to sampling the fault family at Y=0. */
        public Sample sample(int x, int z) {
            return column(x, z).sample(0.0);
        }

        public Sample sample(int x, double y, int z) {
            return column(x, z).sample(y);
        }

        /** Resolves every X/Z-only term once for repeated vertical sampling. */
        public Column column(int x, int z) {
            double dx = (double) x - siteX;
            double dz = (double) z - siteZ;
            double alongFold = dx * foldCos + dz * foldSin;
            double foldAtPoint = wave(
                    TWO_PI * alongFold / foldWavelengthBlocks + foldPhase,
                    foldSecondaryPhase
            );
            double foldAtAnchor = wave(foldPhase, foldSecondaryPhase);
            double foldOffset = 0.5 * foldAmplitudeBlocks * (foldAtPoint - foldAtAnchor);
            double faultCoordinateAtYZero = acrossFaults(x, z) + faultPhaseBlocks;
            long anchorBlock = (long) Math.floor(faultPhaseBlocks / faultSpacingBlocks);
            return new Column(
                    foldOffset,
                    faultCoordinateAtYZero,
                    faultSpacingBlocks,
                    faultThrowBlocks,
                    faultDipShiftPerVerticalBlock,
                    anchorBlock,
                    faultRegime
            );
        }

        /** Legacy X/Z projection, equivalent to projecting onto the Y=0 trace. */
        public FaultTrace nearestFault(int x, int z) {
            return nearestFault(x, 0.0, z);
        }

        /** Projects an X/Z point onto the nearest member of this context's fault family at Y. */
        public FaultTrace nearestFault(int x, double y, int z) {
            if (!Double.isFinite(y)) {
                throw new IllegalArgumentException("fault trace Y must be finite");
            }
            if (faultRegime == FaultRegime.NONE || faultThrowBlocks == 0.0) {
                return new FaultTrace(x, z, Double.POSITIVE_INFINITY, 0L, faultRegime);
            }

            double acrossFaults = acrossFaults(x, z);
            double shiftedAcross = acrossFaults + faultPhaseBlocks + faultDipShiftPerVerticalBlock * y;
            double faultCoordinate = shiftedAcross / faultSpacingBlocks;
            double nearestIndexValue = Math.rint(faultCoordinate);
            long nearestIndex = (long) nearestIndexValue;
            double targetAcross = nearestIndexValue * faultSpacingBlocks
                    - faultPhaseBlocks
                    - faultDipShiftPerVerticalBlock * y;
            double deltaAcross = targetAcross - acrossFaults;
            double normalX = -faultSin;
            double normalZ = faultCos;
            return new FaultTrace(
                    x + normalX * deltaAcross,
                    z + normalZ * deltaAcross,
                    Math.abs(deltaAcross),
                    nearestIndex,
                    faultRegime
            );
        }

        /** Standard geological dip angle measured down from horizontal. */
        public double faultDipDegrees() {
            if (faultDipShiftPerVerticalBlock == 0.0) {
                return 90.0;
            }
            return Math.toDegrees(Math.atan2(1.0, Math.abs(faultDipShiftPerVerticalBlock)));
        }

        private double acrossFaults(int x, int z) {
            double dx = (double) x - siteX;
            double dz = (double) z - siteZ;
            return -dx * faultSin + dz * faultCos;
        }
    }

    /** Piecewise-constant fault state for one X/Z column. */
    public record Column(
            double foldOffset,
            double faultCoordinateAtYZeroBlocks,
            double faultSpacingBlocks,
            double faultThrowBlocks,
            double faultDipShiftPerVerticalBlock,
            long anchorFaultBlock,
            FaultRegime faultRegime
    ) {
        public Column {
            if (!Double.isFinite(foldOffset)
                    || !Double.isFinite(faultCoordinateAtYZeroBlocks)
                    || !Double.isFinite(faultSpacingBlocks) || faultSpacingBlocks < 1.0
                    || !Double.isFinite(faultThrowBlocks) || faultThrowBlocks < 0.0
                    || !Double.isFinite(faultDipShiftPerVerticalBlock)
                    || faultRegime == null) {
                throw new IllegalArgumentException("invalid tectonic structural column");
            }
        }

        public Sample sample(double y) {
            if (!Double.isFinite(y)) {
                throw new IllegalArgumentException("tectonic sample Y must be finite");
            }
            if (faultRegime == FaultRegime.NONE || faultThrowBlocks == 0.0) {
                return new Sample(foldOffset, 0.0, Double.POSITIVE_INFINITY, faultRegime);
            }

            double coordinate = faultCoordinate(y);
            long faultBlock = (long) Math.floor(coordinate);
            double faultOffset = faultThrowBlocks
                    * (faultState(faultBlock, faultRegime) - faultState(anchorFaultBlock, faultRegime));
            double distanceToFault = Math.abs(coordinate - Math.rint(coordinate)) * faultSpacingBlocks;
            return new Sample(foldOffset, faultOffset, distanceToFault, faultRegime);
        }

        public double faultOffset(double y) {
            return sample(y).faultOffset();
        }

        /** Last integer Y that retains the same fault block while scanning upward. */
        public int faultRunEndY(int y) {
            if (faultRegime == FaultRegime.NONE
                    || faultThrowBlocks == 0.0
                    || faultDipShiftPerVerticalBlock == 0.0) {
                return Integer.MAX_VALUE;
            }

            double coordinate = faultCoordinate(y);
            double boundaryCoordinate = faultDipShiftPerVerticalBlock > 0.0
                    ? Math.floor(coordinate) + 1.0
                    : Math.ceil(coordinate) - 1.0;
            double boundaryY = (boundaryCoordinate * faultSpacingBlocks - faultCoordinateAtYZeroBlocks)
                    / faultDipShiftPerVerticalBlock;
            if (!Double.isFinite(boundaryY) || boundaryY >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }

            long firstDifferentY = faultDipShiftPerVerticalBlock > 0.0
                    ? (long) Math.ceil(boundaryY)
                    : (long) Math.floor(boundaryY) + 1L;
            firstDifferentY = Math.max((long) y + 1L, firstDifferentY);
            if (firstDifferentY > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) firstDifferentY - 1;
        }

        private double faultCoordinate(double y) {
            return (faultCoordinateAtYZeroBlocks + faultDipShiftPerVerticalBlock * y) / faultSpacingBlocks;
        }
    }

    private static boolean finiteFraction(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
