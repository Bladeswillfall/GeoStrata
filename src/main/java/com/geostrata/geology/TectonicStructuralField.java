package com.geostrata.geology;

/**
 * Deterministic province-scale tectonic deformation layered on top of the base
 * stratigraphic field. Folds are smooth and long-wavelength; faults are discrete
 * block offsets across a seed-derived regional fault family.
 *
 * <p>The model is intentionally small. X/Z work resolves once per column. Fault
 * traces gain a restrained long-wavelength meander derived from existing structural
 * phase and throw. Basin, rift and orogenic fault families additionally shift their
 * trace with Y; rift faults add one curvature term so they flatten with depth.</p>
 */
public final class TectonicStructuralField {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double SECOND_HARMONIC_WEIGHT = 0.22;
    private static final double ROOT_EPSILON = 1.0e-9;
    private static final double MAX_FAULT_MEANDER_BLOCKS = 18.0;
    private static final double FAULT_MEANDER_THROW_MULTIPLIER = 0.60;
    private static final double FAULT_MEANDER_WAVELENGTH_MULTIPLIER = 2.0;
    private static final double MIN_FAULT_MEANDER_WAVELENGTH_BLOCKS = 256.0;

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
    private static final long FAULT_CURVATURE_SALT = 0xA4093822299F31D0L;

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
        double faultDipShift = faultDipShift(worldSeed, province, siteX, siteZ);
        double faultCurvature = faultCurvature(worldSeed, province, siteX, siteZ, faultDipShift);

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
                faultCurvature,
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
            case OROGENIC_BELT -> {
                minimum = 0.90;
                range = 0.60;
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

    private static double faultCurvature(
            long worldSeed,
            GeologyProvince province,
            int siteX,
            int siteZ,
            double faultDipShift
    ) {
        if (province != GeologyProvince.RIFT_PROVINCE || faultDipShift == 0.0) {
            return 0.0;
        }
        double magnitude = 0.00012 + 0.00006 * roll(worldSeed, siteX, siteZ, FAULT_CURVATURE_SALT);
        return -Math.copySign(magnitude, faultDipShift);
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
            double faultCurvaturePerVerticalBlockSquared,
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
                    || !Double.isFinite(faultCurvaturePerVerticalBlockSquared)
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
                    faultCurvaturePerVerticalBlockSquared,
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
            double shiftedAcross = acrossFaults + faultPhaseBlocks + faultTraceShift(y);
            double faultCoordinate = shiftedAcross / faultSpacingBlocks;
            double nearestIndexValue = Math.rint(faultCoordinate);
            long nearestIndex = (long) nearestIndexValue;
            double targetAcross = nearestIndexValue * faultSpacingBlocks - faultPhaseBlocks - faultTraceShift(y);
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

        /** Standard geological dip angle measured down from horizontal at Y=0. */
        public double faultDipDegrees() {
            return faultDipDegrees(0.0);
        }

        /** Local dip of a curved or planar fault at the requested elevation. */
        public double faultDipDegrees(double y) {
            if (!Double.isFinite(y)) {
                throw new IllegalArgumentException("fault dip Y must be finite");
            }
            double shiftPerVerticalBlock = faultDipShiftPerVerticalBlock
                    + 2.0 * faultCurvaturePerVerticalBlockSquared * y;
            if (shiftPerVerticalBlock == 0.0) {
                return 90.0;
            }
            return Math.toDegrees(Math.atan2(1.0, Math.abs(shiftPerVerticalBlock)));
        }

        private double faultTraceShift(double y) {
            return faultDipShiftPerVerticalBlock * y + faultCurvaturePerVerticalBlockSquared * y * y;
        }

        private double acrossFaults(int x, int z) {
            double dx = (double) x - siteX;
            double dz = (double) z - siteZ;
            double alongFault = dx * faultCos + dz * faultSin;
            double across = -dx * faultSin + dz * faultCos;
            return across + faultTraceMeander(alongFault);
        }

        private double faultTraceMeander(double alongFault) {
            if (faultThrowBlocks == 0.0) {
                return 0.0;
            }
            double amplitude = Math.min(
                    MAX_FAULT_MEANDER_BLOCKS,
                    faultThrowBlocks * FAULT_MEANDER_THROW_MULTIPLIER
            );
            double wavelength = Math.max(
                    MIN_FAULT_MEANDER_WAVELENGTH_BLOCKS,
                    faultSpacingBlocks * FAULT_MEANDER_WAVELENGTH_MULTIPLIER
            );
            double phase = TWO_PI * alongFault / wavelength + foldPhase;
            return amplitude * (Math.sin(phase) - Math.sin(foldPhase));
        }
    }

    /** Piecewise-constant fault state for one X/Z column. */
    public record Column(
            double foldOffset,
            double faultCoordinateAtYZeroBlocks,
            double faultSpacingBlocks,
            double faultThrowBlocks,
            double faultDipShiftPerVerticalBlock,
            double faultCurvaturePerVerticalBlockSquared,
            long anchorFaultBlock,
            FaultRegime faultRegime
    ) {
        public Column {
            if (!Double.isFinite(foldOffset)
                    || !Double.isFinite(faultCoordinateAtYZeroBlocks)
                    || !Double.isFinite(faultSpacingBlocks) || faultSpacingBlocks < 1.0
                    || !Double.isFinite(faultThrowBlocks) || faultThrowBlocks < 0.0
                    || !Double.isFinite(faultDipShiftPerVerticalBlock)
                    || !Double.isFinite(faultCurvaturePerVerticalBlockSquared)
                    || faultRegime == null) {
                throw new IllegalArgumentException("invalid tectonic structural column");
            }
        }

        public Sample sample(double y) {
            return new Sample(foldOffset, faultOffset(y), distanceToFault(y), faultRegime);
        }

        public double faultOffset(double y) {
            validateY(y);
            if (faultRegime == FaultRegime.NONE || faultThrowBlocks == 0.0) {
                return 0.0;
            }
            long faultBlock = (long) Math.floor(faultCoordinate(y));
            return faultThrowBlocks
                    * (faultState(faultBlock, faultRegime) - faultState(anchorFaultBlock, faultRegime));
        }

        public double distanceToFault(double y) {
            validateY(y);
            if (faultRegime == FaultRegime.NONE || faultThrowBlocks == 0.0) {
                return Double.POSITIVE_INFINITY;
            }
            double coordinate = faultCoordinate(y);
            return Math.abs(coordinate - Math.rint(coordinate)) * faultSpacingBlocks;
        }

        /** Last integer Y that retains the same fault block while scanning upward. */
        public int faultRunEndY(int y) {
            if (faultRegime == FaultRegime.NONE
                    || faultThrowBlocks == 0.0
                    || (faultDipShiftPerVerticalBlock == 0.0 && faultCurvaturePerVerticalBlockSquared == 0.0)) {
                return Integer.MAX_VALUE;
            }
            double currentOffset = faultOffset(y);
            if (faultOffset((double) y + 1.0) != currentOffset) {
                return y;
            }

            double coordinate = faultCoordinate(y);
            double lowerBoundary = Math.floor(coordinate);
            double upperBoundary = lowerBoundary + 1.0;
            double nextBoundaryY = Math.min(
                    nextRootAfter(y, lowerBoundary),
                    nextRootAfter(y, upperBoundary)
            );
            if (!Double.isFinite(nextBoundaryY) || nextBoundaryY >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }

            long firstCandidate = Math.max((long) y + 1L, (long) Math.floor(nextBoundaryY) - 1L);
            long lastCandidate = Math.min((long) Integer.MAX_VALUE, (long) Math.ceil(nextBoundaryY) + 2L);
            for (long candidate = firstCandidate; candidate <= lastCandidate; candidate++) {
                if (faultOffset(candidate) != currentOffset) {
                    return (int) candidate - 1;
                }
            }
            return Integer.MAX_VALUE;
        }

        private double nextRootAfter(int y, double targetCoordinate) {
            double targetBlocks = targetCoordinate * faultSpacingBlocks;
            double constant = faultCoordinateAtYZeroBlocks - targetBlocks;
            if (faultCurvaturePerVerticalBlockSquared == 0.0) {
                if (faultDipShiftPerVerticalBlock == 0.0) {
                    return Double.POSITIVE_INFINITY;
                }
                double root = -constant / faultDipShiftPerVerticalBlock;
                return root > y + ROOT_EPSILON ? root : Double.POSITIVE_INFINITY;
            }

            double discriminant = faultDipShiftPerVerticalBlock * faultDipShiftPerVerticalBlock
                    - 4.0 * faultCurvaturePerVerticalBlockSquared * constant;
            if (discriminant < 0.0) {
                return Double.POSITIVE_INFINITY;
            }
            double squareRoot = Math.sqrt(discriminant);
            double denominator = 2.0 * faultCurvaturePerVerticalBlockSquared;
            double first = (-faultDipShiftPerVerticalBlock - squareRoot) / denominator;
            double second = (-faultDipShiftPerVerticalBlock + squareRoot) / denominator;
            double result = Double.POSITIVE_INFINITY;
            if (first > y + ROOT_EPSILON) {
                result = first;
            }
            if (second > y + ROOT_EPSILON) {
                result = Math.min(result, second);
            }
            return result;
        }

        private double faultCoordinate(double y) {
            return (faultCoordinateAtYZeroBlocks
                    + faultDipShiftPerVerticalBlock * y
                    + faultCurvaturePerVerticalBlockSquared * y * y)
                    / faultSpacingBlocks;
        }

        private static void validateY(double y) {
            if (!Double.isFinite(y)) {
                throw new IllegalArgumentException("tectonic sample Y must be finite");
            }
        }
    }

    private static boolean finiteFraction(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
