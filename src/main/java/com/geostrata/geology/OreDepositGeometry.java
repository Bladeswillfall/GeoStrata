package com.geostrata.geology;

import java.util.List;

/** Pure geometry and grade sampling for deterministic ore-deposit proposals. */
public final class OreDepositGeometry {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double TRACE_LIMIT = 1.25;
    private static final double TRACE_LIMIT_SQUARED = TRACE_LIMIT * TRACE_LIMIT;

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
    private static final Sample TRACE_SAMPLE = new Sample(0.0, null, true);
    private static final Sample OUTSIDE_SAMPLE = new Sample(0.0, null, false);

    private OreDepositGeometry() {
    }

    public static Body forCandidate(long worldSeed, OreDepositCandidatePlanner.Candidate candidate) {
        return forCandidate(worldSeed, candidate, OreGenerationProfile.defaults());
    }

    public static Body forCandidate(
            long worldSeed,
            OreDepositCandidatePlanner.Candidate candidate,
            OreGenerationProfile generation
    ) {
        if (candidate == null) {
            throw new IllegalArgumentException("ore candidate must not be null");
        }
        return forProposal(worldSeed, candidate.proposal(), generation);
    }

    /** Compatibility overload using neutral material tuning. */
    public static Body forProposal(long worldSeed, OreDepositCandidatePlanner.Proposal proposal) {
        return forProposal(worldSeed, proposal, OreGenerationProfile.defaults());
    }

    /** Builds geometry before local host clipping; the host does not alter deposit shape. */
    public static Body forProposal(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            OreGenerationProfile generation
    ) {
        if (proposal == null || generation == null) {
            throw new IllegalArgumentException("ore proposal and generation tuning must not be null");
        }

        Profile profile = profile(proposal.depositStyle());
        double materialScale = generation.bodyScale();
        double azimuth = TWO_PI * roll(worldSeed, proposal, AZIMUTH_SALT);
        double dip = profile.maximumDipRadians() * (roll(worldSeed, proposal, DIP_SALT) * 2.0 - 1.0);
        double length = varied(profile.lengthRadius() * materialScale, roll(worldSeed, proposal, LENGTH_SALT), 0.20);
        double width = varied(profile.widthRadius() * materialScale, roll(worldSeed, proposal, WIDTH_SALT), 0.20);
        double thickness = varied(profile.thicknessRadius() * materialScale, roll(worldSeed, proposal, THICKNESS_SALT), 0.15);
        double warp = varied(profile.warpAmplitude() * materialScale, roll(worldSeed, proposal, WARP_SALT), 0.25);
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
                branches,
                generation.traceNormalScale(),
                generation.grades()
        );
    }

    private static Profile profile(String style) {
        return switch (style) {
            case "coal_seam" -> new Profile(56.0, 34.0, 2.2, Math.toRadians(12.0), 1.0);
            case "vein" -> new Profile(36.0, 2.8, 2.4, Math.toRadians(75.0), 1.8);
            case "micro_vein" -> new Profile(2.0, 0.85, 0.85, Math.toRadians(70.0), 0.12);
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
                branch(worldSeed, proposal, length, 1, 1.0),
                branch(worldSeed, proposal, length, 2, -1.0),
                branch(worldSeed, proposal, length, 3, 1.0),
                branch(worldSeed, proposal, length, 4, -1.0),
                branch(worldSeed, proposal, length, 5, 1.0)
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
            List<Branch> branches,
            double traceNormalScale,
            OreGenerationProfile.GradeTuning grades
    ) {
        public Body {
            if (material == null || material.isBlank() || style == null || style.isBlank() || grades == null) {
                throw new IllegalArgumentException("ore body material, style and grade tuning must be valid");
            }
            if (!positive(lengthRadius) || !positive(widthRadius) || !positive(thicknessRadius)
                    || !positive(warpWavelength) || !positive(traceNormalScale)
                    || !finite(azimuthRadians) || !finite(dipRadians)
                    || !finite(warpAmplitude) || warpAmplitude < 0.0 || !finite(warpPhase)) {
                throw new IllegalArgumentException("ore body geometry must be finite with positive dimensions");
            }
            branches = List.copyOf(branches);
        }

        /** Compatibility constructor using neutral trace and grade tuning. */
        public Body(
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
            this(
                    worldSeed,
                    material,
                    style,
                    anchorX,
                    anchorY,
                    anchorZ,
                    lengthRadius,
                    widthRadius,
                    thicknessRadius,
                    azimuthRadians,
                    dipRadians,
                    warpAmplitude,
                    warpWavelength,
                    warpPhase,
                    branches,
                    1.0,
                    OreGenerationProfile.GradeTuning.defaults()
            );
        }

        public Sample sample(int x, int y, int z) {
            return sample(x, y, z, localPoint(x, y, z));
        }

        /** Creates one reusable evaluator for the hot voxel loop of this immutable body. */
        public Sampler sampler() {
            return new Sampler(this);
        }

        private Sample sample(int x, int y, int z, LocalPoint point) {
            double normalizedDistanceSquared = normalizedDistanceSquared(point);
            if (normalizedDistanceSquared <= 1.0) {
                return economicSample(x, y, z, Math.sqrt(normalizedDistanceSquared));
            }
            if (traceDistanceSquared(point, normalizedDistanceSquared) <= TRACE_LIMIT_SQUARED) {
                return TRACE_SAMPLE;
            }
            return OUTSIDE_SAMPLE;
        }

        /** Conservative inclusive AABB for every economic voxel in this body. */
        public Bounds bounds() {
            LocalBounds local = localBounds();
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;

            double cosAzimuth = Math.cos(azimuthRadians);
            double sinAzimuth = Math.sin(azimuthRadians);
            double cosDip = Math.cos(dipRadians);
            double sinDip = Math.sin(dipRadians);
            for (int alongSide = 0; alongSide < 2; alongSide++) {
                double along = alongSide == 0 ? local.minAlong() : local.maxAlong();
                for (int acrossSide = 0; acrossSide < 2; acrossSide++) {
                    double across = acrossSide == 0 ? local.minAcross() : local.maxAcross();
                    for (int normalSide = 0; normalSide < 2; normalSide++) {
                        double normal = normalSide == 0 ? local.minNormal() : local.maxNormal();
                        double x = along * cosAzimuth * cosDip
                                - across * sinAzimuth
                                - normal * cosAzimuth * sinDip;
                        double y = along * sinDip + normal * cosDip;
                        double z = along * sinAzimuth * cosDip
                                + across * cosAzimuth
                                - normal * sinAzimuth * sinDip;
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
            return new Bounds(
                    anchorX + (int) Math.floor(minX),
                    anchorY + (int) Math.floor(minY),
                    anchorZ + (int) Math.floor(minZ),
                    anchorX + (int) Math.ceil(maxX),
                    anchorY + (int) Math.ceil(maxY),
                    anchorZ + (int) Math.ceil(maxZ)
            );
        }

        private LocalBounds localBounds() {
            boolean vein = "vein".equals(style);
            double minAlong = -lengthRadius - (vein ? thicknessRadius : 0.0);
            double maxAlong = lengthRadius + (vein ? thicknessRadius : 0.0);
            double minAcross = -widthRadius;
            double maxAcross = widthRadius;
            double minNormal = -thicknessRadius;
            double maxNormal = thicknessRadius;

            if (vein) {
                for (Branch branch : branches) {
                    double normalRadius = thicknessRadius * branch.radiusScale();
                    double acrossRadius = widthRadius * branch.radiusScale();
                    minAlong = Math.min(minAlong, Math.min(branch.startAlong(), branch.endAlong()) - normalRadius);
                    maxAlong = Math.max(maxAlong, Math.max(branch.startAlong(), branch.endAlong()) + normalRadius);
                    minAcross = Math.min(minAcross, Math.min(branch.startAcross(), branch.endAcross()) - acrossRadius);
                    maxAcross = Math.max(maxAcross, Math.max(branch.startAcross(), branch.endAcross()) + acrossRadius);
                    minNormal = Math.min(minNormal, Math.min(branch.startNormal(), branch.endNormal()) - normalRadius);
                    maxNormal = Math.max(maxNormal, Math.max(branch.startNormal(), branch.endNormal()) + normalRadius);
                }
            }

            return new LocalBounds(
                    minAlong,
                    maxAlong,
                    minAcross - warpAmplitude * 2.0,
                    maxAcross + warpAmplitude * 2.0,
                    minNormal - warpAmplitude,
                    maxNormal + warpAmplitude
            );
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
                    * grades.dither();
            return new Sample(concentration, grades.grade(clamp(concentration + dither)), false);
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

        private double normalizedDistanceSquared(LocalPoint point) {
            if ("vein".equals(style)) {
                return veinDistanceSquared(point);
            }
            return square(point.along() / lengthRadius)
                    + square(point.across() / widthRadius)
                    + square(point.normal() / thicknessRadius);
        }

        private double traceDistanceSquared(LocalPoint point, double normalizedDistanceSquared) {
            if (traceNormalScale == 1.0) {
                return normalizedDistanceSquared;
            }
            return square(point.along() / lengthRadius)
                    + square(point.across() / widthRadius)
                    + square(point.normal() / (thicknessRadius * traceNormalScale));
        }

        private double veinDistanceSquared(LocalPoint point) {
            double acrossScale = thicknessRadius / widthRadius;
            LocalPoint scaled = new LocalPoint(point.along(), point.across() * acrossScale, point.normal());
            double bestSquared = distanceSquaredToSegment(
                    scaled,
                    -lengthRadius,
                    0.0,
                    0.0,
                    lengthRadius,
                    0.0,
                    0.0
            ) / square(thicknessRadius);
            for (Branch branch : branches) {
                double radius = thicknessRadius * branch.radiusScale();
                bestSquared = Math.min(
                        bestSquared,
                        distanceSquaredToSegment(scaled, branch, acrossScale) / square(radius)
                );
            }
            return bestSquared;
        }
    }

    /** Reusable body-local transform for high-volume worldgen sampling. */
    public static final class Sampler {
        private final Body body;
        private final Bounds placementBounds;
        private final double cosAzimuth;
        private final double sinAzimuth;
        private final double cosDip;
        private final double sinDip;
        private final double sinWarpPhase;
        private final double cosWarpPhase;

        private Sampler(Body body) {
            this.body = body;
            placementBounds = OreExposurePlacement.placementBounds(body);
            cosAzimuth = Math.cos(body.azimuthRadians());
            sinAzimuth = Math.sin(body.azimuthRadians());
            cosDip = Math.cos(body.dipRadians());
            sinDip = Math.sin(body.dipRadians());
            sinWarpPhase = Math.sin(body.warpPhase());
            cosWarpPhase = Math.cos(body.warpPhase());
        }

        public Sample sample(int x, int y, int z) {
            if (!placementBounds.contains(x, y, z)) {
                return OUTSIDE_SAMPLE;
            }
            double dx = (double) x - body.anchorX();
            double dy = (double) y - body.anchorY();
            double dz = (double) z - body.anchorZ();
            double along = dx * cosAzimuth * cosDip + dy * sinDip + dz * sinAzimuth * cosDip;
            double across = -dx * sinAzimuth + dz * cosAzimuth;
            double normal = -dx * cosAzimuth * sinDip + dy * cosDip - dz * sinAzimuth * sinDip;
            double phase = TWO_PI * along / body.warpWavelength() + body.warpPhase();
            double wave = Math.sin(phase) - sinWarpPhase;
            double crossWave = Math.cos(phase) - cosWarpPhase;
            return body.sample(
                    x,
                    y,
                    z,
                    new LocalPoint(
                            along,
                            across - body.warpAmplitude() * wave,
                            normal - body.warpAmplitude() * 0.5 * crossWave
                    )
            );
        }
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
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

    private record LocalBounds(
            double minAlong,
            double maxAlong,
            double minAcross,
            double maxAcross,
            double minNormal,
            double maxNormal
    ) {
    }

    private static double distanceSquaredToSegment(LocalPoint point, Branch segment, double acrossScale) {
        return distanceSquaredToSegment(
                point,
                segment.startAlong(),
                segment.startAcross() * acrossScale,
                segment.startNormal(),
                segment.endAlong(),
                segment.endAcross() * acrossScale,
                segment.endNormal()
        );
    }

    private static double distanceSquaredToSegment(
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
        return square(deltaAlong) + square(deltaAcross) + square(deltaNormal);
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
