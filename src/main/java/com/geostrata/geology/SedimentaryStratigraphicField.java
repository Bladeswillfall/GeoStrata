package com.geostrata.geology;

/**
 * Pure spatial transform from world coordinates to a normalized sedimentary
 * succession coordinate. This class owns no Minecraft registries and places no blocks.
 */
public final class SedimentaryStratigraphicField {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final long DIP_ANGLE_SALT = 0xA54FF53A5F1D36F1L;
    private static final long DIP_MAGNITUDE_SALT = 0x510E527FADE682D1L;
    private static final long WARP_ANGLE_SALT = 0x9B05688C2B3E6C1FL;

    private SedimentaryStratigraphicField() {
    }

    public static Field forSite(
            long worldSeed,
            int siteX,
            int siteZ,
            Parameters parameters
    ) {
        if (parameters == null) {
            throw new IllegalArgumentException("stratigraphic field parameters must not be null");
        }

        double dipAngle = TWO_PI * GeologyDeterminism.unitRoll(
                worldSeed,
                siteX,
                0,
                siteZ,
                DIP_ANGLE_SALT
        );
        double dipMagnitude = parameters.maxDip()
                * GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, DIP_MAGNITUDE_SALT);
        double warpAngle = TWO_PI * GeologyDeterminism.unitRoll(
                worldSeed,
                siteX,
                0,
                siteZ,
                WARP_ANGLE_SALT
        );

        return new Field(
                siteX,
                siteZ,
                parameters.cycleThicknessBlocks(),
                dipMagnitude * Math.cos(dipAngle),
                dipMagnitude * Math.sin(dipAngle),
                parameters.warpAmplitudeBlocks(),
                parameters.warpWavelengthBlocks(),
                warpAngle
        );
    }

    public record Parameters(
            double cycleThicknessBlocks,
            double maxDip,
            double warpAmplitudeBlocks,
            double warpWavelengthBlocks
    ) {
        public Parameters {
            if (!Double.isFinite(cycleThicknessBlocks) || cycleThicknessBlocks < 1.0) {
                throw new IllegalArgumentException("cycle thickness must be finite and at least one block");
            }
            if (!Double.isFinite(maxDip) || maxDip < 0.0 || maxDip > 1.0) {
                throw new IllegalArgumentException("max dip must be within 0..1 vertical blocks per horizontal block");
            }
            if (!Double.isFinite(warpAmplitudeBlocks)
                    || warpAmplitudeBlocks < 0.0
                    || warpAmplitudeBlocks > cycleThicknessBlocks) {
                throw new IllegalArgumentException("warp amplitude must be within 0..cycle thickness");
            }
            if (!Double.isFinite(warpWavelengthBlocks) || warpWavelengthBlocks < 1.0) {
                throw new IllegalArgumentException("warp wavelength must be finite and at least one block");
            }
        }
    }

    public record Field(
            int siteX,
            int siteZ,
            double cycleThicknessBlocks,
            double dipX,
            double dipZ,
            double warpAmplitudeBlocks,
            double warpWavelengthBlocks,
            double warpAngle
    ) {
        public Field {
            if (!Double.isFinite(cycleThicknessBlocks) || cycleThicknessBlocks < 1.0) {
                throw new IllegalArgumentException("cycle thickness must be finite and at least one block");
            }
            if (!Double.isFinite(dipX) || !Double.isFinite(dipZ) || Math.hypot(dipX, dipZ) > 1.0) {
                throw new IllegalArgumentException("dip components must be finite and combine to at most one block per block");
            }
            if (!Double.isFinite(warpAmplitudeBlocks)
                    || warpAmplitudeBlocks < 0.0
                    || warpAmplitudeBlocks > cycleThicknessBlocks) {
                throw new IllegalArgumentException("warp amplitude must be within 0..cycle thickness");
            }
            if (!Double.isFinite(warpWavelengthBlocks) || warpWavelengthBlocks < 1.0) {
                throw new IllegalArgumentException("warp wavelength must be finite and at least one block");
            }
            if (!Double.isFinite(warpAngle)) {
                throw new IllegalArgumentException("warp angle must be finite");
            }
        }

        /**
         * Vertical displacement of a constant stratigraphic contact at X/Z.
         * The province site is the zero-offset anchor by construction.
         */
        public double verticalOffset(int x, int z) {
            double dx = (double) x - siteX;
            double dz = (double) z - siteZ;
            double dipOffset = dipX * dx + dipZ * dz;

            double alongWarp = dx * Math.cos(warpAngle) + dz * Math.sin(warpAngle);
            double warpOffset = warpAmplitudeBlocks
                    * Math.sin(TWO_PI * alongWarp / warpWavelengthBlocks);
            return dipOffset + warpOffset;
        }

        /**
         * Samples a normalized repeated succession coordinate. Repetition is
         * explicit here: cycleIndex identifies which motif repeat owns the point,
         * while fraction identifies the lower-to-upper position inside that motif.
         */
        public Sample sample(
                int x,
                double y,
                int z,
                SedimentaryContactPlanner.Plan plan
        ) {
            if (plan == null) {
                throw new IllegalArgumentException("contact plan must not be null");
            }
            if (!Double.isFinite(y)) {
                throw new IllegalArgumentException("sample Y must be finite");
            }

            double offset = verticalOffset(x, z);
            double coordinate = (y - offset) / cycleThicknessBlocks + plan.phase();
            if (!Double.isFinite(coordinate)) {
                throw new IllegalArgumentException("stratigraphic coordinate must be finite");
            }

            double floor = Math.floor(coordinate);
            if (floor < Long.MIN_VALUE || floor > Long.MAX_VALUE) {
                throw new IllegalArgumentException("stratigraphic cycle index is outside long range");
            }
            long cycleIndex = (long) floor;
            double fraction = coordinate - floor;
            if (fraction >= 1.0) {
                fraction = 0.0;
                cycleIndex++;
            }

            SedimentaryContactPlanner.Interval bed = plan.bedAt(fraction);
            return new Sample(coordinate, cycleIndex, fraction, offset, bed);
        }
    }

    public record Sample(
            double stratigraphicCoordinate,
            long cycleIndex,
            double fraction,
            double verticalOffset,
            SedimentaryContactPlanner.Interval bed
    ) {
    }
}
