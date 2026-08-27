package com.geostrata.geology;

import java.util.List;

/** Pure, non-mutating geometry and grade preview for geology-qualified ore candidates. */
public final class OreDepositGeometry {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double TRACE_LIMIT = 1.25;
    private static final double GRADE_DITHER = 0.12;

    private static final long AZIMUTH_SALT = 0x243F6A8885A308D3L;
    private static final long DIP_SALT = 0x13198A2E03707344L;
    private static final long LENGTH_SALT = 0xA4093822299F31D0L;
    private static final long WIDTH_SALT = 0x082EFA98EC4E6C89L;
    private static final long THICKNESS_SALT = 0x452821E638D01377L;
    private static final long WARP_SALT = 0xBE5466CF34E90C6CL;
    private static final long PHASE_SALT = 0xC0AC29B7C97C50DDL;
    private static final long BRANCH_SALT = 0x3F84D5B5B5470917L;
    private static final long FILL_SALT = 0x9216D5D98979FB1BL;
    private static final long GRADE_SALT = 0xD1310BA698DFB5ACL;

    private OreDepositGeometry() {
    }

    public static Body forCandidate(long worldSeed, OreDepositCandidatePlanner.Candidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("ore candidate must not be null");
        }

        OreDepositCandidatePlanner.Proposal proposal = candidate.proposal();
        Profile profile = profile(proposal.depositStyle());
        double azimuth = TWO_PI * roll(worldSeed, proposal, AZIMUTH_SALT);
        double dip = profile.maximumDipRadians() * (roll(worldSeed, proposal, DIP_SALT) * 2.0 - 1.0);
        double length = varied(profile.lengthRadius(), roll(worldSeed, proposal, LENGTH_SALT), 0.20);
        double width = varied(profile.widthRadius(), roll(worldSeed, proposal, WIDTH_SALT), 0.20);
        double thickness = varied(profile.thicknessRadius(), roll(worldSeed, proposal, THICKNESS_SALT), 0.15);
        double warp = varied(profile.warpAmplitude(), roll(worldSeed, proposal, WARP_SALT), 0.25);
        double phase = TWO_PI * roll(worldSeed, proposal, PHASE_SALT);
        List<Branch> branches = "vein".equals(proposal.depositStyle())
                ? veinBranches(worldSeed, proposal, length)
                : List.of();

        return new Body(
                worldSeed,
                proposal.material(),
                proposal.depositStyle(),
                proposal.anchorX(),
                proposal.anchorY(),
                proposal.anchorZ(),
                length,
                width,
                thickness,
                azimuth,
                dip,
                warp,
                Math.max(8.0, length * 0.85),
                phase,
                branches
        );
    }

    private static Profile profile(String style) {
        return switch (style) {
            case "coal_seam" -> new Profile(56.0, 34.0, 2.2, Math.toRadians(12.0), 1.0);
            case "vein" -> new Profile(36.0, 2.8, 2.4, Math.toRadians(75.0), 1.8);
            case "stratiform" -> new Profile(46.0, 30.0, 4.5, Math.toRadians(18.0), 1.6);
            case "disseminated" -> new Profile(26.0, 20.0, 13.0, Math.toRadians(35.0), 2.4);
            case "massive_lens_or_pocket" -> new Profile(20.0, 15.0, 9.0, Math.toRadians(45.0), 1.8);
            default -> throw new IllegalArgumentException("unsupported ore deposit style: " + style);
        };
    }

    private static List<Branch> veinBranches(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            double length
    ) {
        return List.of(
                branch(worldSeed, proposal, length, 0, -1.0),
                branch(worldSeed, proposal, length, 1, 1.0)
        );
    }

    private static Branch branch(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            double length,
            int index,
            double side
    ) {
        long salt = BRANCH_SALT + index * 0x9E3779B97F4A7C15L;
        double startAlong = length * (-0.30 + 0.60 * roll(worldSeed, proposal, salt));
        double endAlong = startAlong + length * (0.25 + 0.20 * roll(worldSeed, proposal, salt ^ LENGTH_SALT));
        double endAcross = side * length * (0.24 + 0.18 * roll(worldSeed, proposal, salt ^ WIDTH_SALT));
        double endNormal = length * (roll(worldSeed, proposal, salt ^ DIP_SALT) - 0.5) * 0.30;
        double radiusScale = 0.52 + 0.16 * roll(worldSeed, proposal, salt ^ THICKNESS_SALT);
        return new Branch(startAlong, 0.0, 0.0, endAlong, endAcross, endNormal, radiusScale);
    }

    private static double varied(double base, double roll, double fraction) {
        return base * (1.0 - fraction + roll * fraction * 2.0);
    }

    private static double roll(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            long salt
    ) {
        long identity = Integer.toUnsignedLong(proposal.material().hashCode())
                ^ Long.rotateLeft(Integer.toUnsignedLong(proposal.depositStyle().hashCode()), 32);
        return GeologyDeterminism.unitRoll(
                worldSeed,
                proposal.anchorX(),
                proposal.anchorY(),
                proposal.anchorZ(),
                salt ^ identity
        );
    }

    private record Profile(
            double lengthRadius,
            double widthRadius,
            double thicknessRadius,
            double maximumDipRadians,
            double warpAmplitude
    ) {
    }

    public record Branch(
            double startAlong,
            double startAcross,
            double startNormal,
            double endAlong,
            double endAcross,
            double endNormal,
            double radiusScale
    ) {
        public Branch {
            if (!positive(radiusScale)) {
                throw new IllegalArgumentException("ore branch radius scale must be finite and positive");
            }
        }
    }

    public record Body(
            long worldSeed,
            String material,
            String style,
            int anchorX,
            int anchorY,
            int anchorZ,
            double lengthRadius,
            double widthRadius,
            double thicknessRadius,
            double azimuthRadians,
            double dipRadians,
            double warpAmplitude,
            double warpWavelength,
            double warpPhase,
            List<Branch> branches
    ) {
        public Body {
            if (material == null || material.isBlank() || style == null || style.isBlank()) {
                throw new IllegalArgumentException("ore body material and style must not be blank");
            }
            if (!positive(lengthRadius) || !positive(widthRadius) || !positive(thicknessRadius)
                    || !positive(warpWavelength) || !finite(azimuthRadians) || !finite(dipRadians)
                    || !finite(warpAmplitude) || warpAmplitude < 0.0 || !finite(warpPhase)) {
                throw new IllegalArgumentException("ore body geometry must be finite with positive dimensions");
            }
            branches = List.copyOf(branches);
        }

        public Sample sample(int x, int y, int z) {
            LocalPoint point = localPoint(x, y, z);
            double normalizedDistance = normalizedDistance(point);
            if (normalizedDistance <= 1.0) {
                return economicSample(x, y, z, normalizedDistance);
            }
            if (normalizedDistance <= TRACE_LIMIT) {
                return new Sample(0.0, null, true);
            }
            return new Sample(0.0, null, false);
        }

        private Sample economicSample(int x, int y, int z, double normalizedDistance) {
            double concentration = clamp(1.0 - normalizedDistance);
            if ("disseminated".equals(style)) {
                double fillChance = 0.18 + 0.82 * concentration;
                double fillRoll = GeologyDeterminism.unitRoll(worldSeed, x, y, z, FILL_SALT);
                if (!GeologyDeterminism.passesChance(fillChance, fillRoll)) {
                    return new Sample(concentration, null, true);
                }
            }

            double dither = (GeologyDeterminism.unitRoll(worldSeed, x, y, z, GRADE_SALT) - 0.5)
                    * GRADE_DITHER;
            return new Sample(concentration, grade(clamp(concentration + dither)), false);
        }

        private LocalPoint localPoint(int x, int y, int z) {
            double dx = (double) x - anchorX;
            double dy = (double) y - anchorY;
            double dz = (double) z - anchorZ;
            double cosAzimuth = Math.cos(azimuthRadians);
            double sinAzimuth = Math.sin(azimuthRadians);
            double cosDip = Math.cos(dipRadians);
            double sinDip = Math.sin(dipRadians);

            double along = dx * cosAzimuth * cosDip + dy * sinDip + dz * sinAzimuth * cosDip;
            double across = -dx * sinAzimuth + dz * cosAzimuth;
            double normal = -dx * cosAzimuth * sinDip + dy * cosDip - dz * sinAzimuth * sinDip;
            double wave = Math.sin(TWO_PI * along / warpWavelength + warpPhase) - Math.sin(warpPhase);
            double crossWave = Math.cos(TWO_PI * along / warpWavelength + warpPhase) - Math.cos(warpPhase);
            return new LocalPoint(
                    along,
                    across - warpAmplitude * wave,
                    normal - warpAmplitude * 0.5 * crossWave
            );
        }

        private double normalizedDistance(LocalPoint point) {
            if ("vein".equals(style)) {
                return veinDistance(point);
            }
            return Math.sqrt(
                    square(point.along() / lengthRadius)
                            + square(point.across() / widthRadius)
                            + square(point.normal() / thicknessRadius)
            );
        }

        private double veinDistance(LocalPoint point) {
            double acrossScale = thicknessRadius / widthRadius;
            LocalPoint scaled = new LocalPoint(point.along(), point.across() * acrossScale, point.normal());
            double best = distanceToSegment(
                    scaled,
                    -lengthRadius,
                    0.0,
                    0.0,
                    lengthRadius,
                    0.0,
                    0.0
            ) / thicknessRadius;
            for (Branch branch : branches) {
                best = Math.min(
                        best,
                        distanceToSegment(scaled, branch, acrossScale)
                                / (thicknessRadius * branch.radiusScale())
                );
            }
            return best;
        }
    }

    public record Sample(double concentration, OreGrade grade, boolean trace) {
        public boolean economic() {
            return grade != null;
        }

        public String zone() {
            if (grade != null) {
                return grade.id();
            }
            return trace ? "trace" : "outside";
        }
    }

    private record LocalPoint(double along, double across, double normal) {
    }

    private static double distanceToSegment(LocalPoint point, Branch segment, double acrossScale) {
        return distanceToSegment(
                point,
                segment.startAlong(),
                segment.startAcross() * acrossScale,
                segment.startNormal(),
                segment.endAlong(),
                segment.endAcross() * acrossScale,
                segment.endNormal()
        );
    }

    private static double distanceToSegment(
            LocalPoint point,
            double startAlong,
            double startAcross,
            double startNormal,
            double endAlong,
            double endAcross,
            double endNormal
    ) {
        double along = endAlong - startAlong;
        double across = endAcross - startAcross;
        double normal = endNormal - startNormal;
        double lengthSquared = square(along) + square(across) + square(normal);
        double projection = ((point.along() - startAlong) * along
                + (point.across() - startAcross) * across
                + (point.normal() - startNormal) * normal) / lengthSquared;
        double fraction = Math.min(1.0, Math.max(0.0, projection));
        double deltaAlong = point.along() - (startAlong + along * fraction);
        double deltaAcross = point.across() - (startAcross + across * fraction);
        double deltaNormal = point.normal() - (startNormal + normal * fraction);
        return Math.sqrt(square(deltaAlong) + square(deltaAcross) + square(deltaNormal));
    }

    private static OreGrade grade(double concentration) {
        if (concentration < 0.35) {
            return OreGrade.POOR;
        }
        if (concentration < 0.60) {
            return OreGrade.MEDIUM;
        }
        if (concentration < 0.82) {
            return OreGrade.RICH;
        }
        return OreGrade.MASSIVE;
    }

    private static boolean positive(double value) {
        return finite(value) && value > 0.0;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static double square(double value) {
        return value * value;
    }
}
