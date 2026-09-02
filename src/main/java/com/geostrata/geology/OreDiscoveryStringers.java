package com.geostrata.geology;

import java.util.ArrayList;
import java.util.List;

/** Deterministic poor-grade discovery fractures genetically tied to a parent ore body. */
public final class OreDiscoveryStringers {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final long STRINGER_SALT = 0x6A09E667F3BCC909L;
    private static final long LENGTH_SALT = 0xBB67AE8584CAA73BL;
    private static final long RADIUS_SALT = 0x3C6EF372FE94F82BL;
    private static final long BEND_SALT = 0xA54FF53A5F1D36F1L;
    private static final long NORMAL_SALT = 0x510E527FADE682D1L;

    private OreDiscoveryStringers() {
    }

    /** Uses the loaded ore LUT when available; standalone bodies default to no discovery stringers. */
    public static Field forBody(OreDepositGeometry.Body body) {
        if (body == null) {
            throw new IllegalArgumentException("ore body must not be null");
        }
        OreOccurrenceCatalog.Occurrence occurrence = OreOccurrenceCatalog.current().byId().get(body.material());
        OreGenerationProfile.DiscoveryStringers profile = occurrence == null
                ? OreGenerationProfile.DiscoveryStringers.disabled()
                : occurrence.generation().discoveryStringers();
        return forBody(body, profile);
    }

    public static Field forBody(
            OreDepositGeometry.Body body,
            OreGenerationProfile.DiscoveryStringers profile
    ) {
        if (body == null || profile == null) {
            throw new IllegalArgumentException("ore body and discovery tuning must not be null");
        }
        if (!profile.enabled()) {
            return new Field(body, List.of(), body.bounds(), profile.exposedHaloBlocks());
        }

        List<Segment> segments = new ArrayList<>(profile.count() * 2);
        for (int index = 0; index < profile.count(); index++) {
            addStringer(body, profile, index, segments);
        }
        return new Field(
                body,
                List.copyOf(segments),
                bounds(body, segments, profile.exposedHaloBlocks()),
                profile.exposedHaloBlocks()
        );
    }

    private static void addStringer(
            OreDepositGeometry.Body body,
            OreGenerationProfile.DiscoveryStringers profile,
            int index,
            List<Segment> segments
    ) {
        long salt = STRINGER_SALT + index * 0x9E3779B97F4A7C15L;
        double theta = TWO_PI * roll(body, salt);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double marginScale = 0.78 + 0.18 * roll(body, salt ^ RADIUS_SALT);
        LocalPoint start = new LocalPoint(
                cos * body.lengthRadius() * marginScale,
                sin * body.widthRadius() * marginScale,
                body.thicknessRadius() * (roll(body, salt ^ NORMAL_SALT) - 0.5) * 0.70
        );

        double normalComponent = (roll(body, salt ^ (NORMAL_SALT << 1)) - 0.5) * 1.20;
        double verticalBias = index < profile.downwardBiasedCount() ? profile.downwardBias() : 0.0;
        double directionAlong = cos + verticalBias * Math.sin(body.dipRadians());
        double directionAcross = sin;
        double directionNormal = normalComponent + verticalBias * Math.cos(body.dipRadians());
        double directionLength = Math.sqrt(
                directionAlong * directionAlong
                        + directionAcross * directionAcross
                        + directionNormal * directionNormal
        );
        LocalPoint direction = new LocalPoint(
                directionAlong / directionLength,
                directionAcross / directionLength,
                directionNormal / directionLength
        );
        LocalPoint perpendicular = new LocalPoint(-sin, cos, 0.0);
        double length = profile.minLength()
                + (profile.maxLength() - profile.minLength()) * roll(body, salt ^ LENGTH_SALT);
        double bend = (roll(body, salt ^ BEND_SALT) - 0.5) * length * 0.34;
        double endBend = bend + (roll(body, salt ^ (BEND_SALT << 1)) - 0.5) * length * 0.30;
        double endNormal = (roll(body, salt ^ (NORMAL_SALT << 2)) - 0.5) * length * 0.18;
        LocalPoint middle = add(start, scale(direction, length * 0.46), scale(perpendicular, bend));
        LocalPoint end = add(
                start,
                scale(direction, length),
                scale(perpendicular, endBend),
                new LocalPoint(0.0, 0.0, endNormal)
        );
        double radius = profile.minRadius()
                + (profile.maxRadius() - profile.minRadius()) * roll(body, salt ^ RADIUS_SALT);

        segments.add(new Segment(start, middle, radius));
        segments.add(new Segment(middle, end, radius * 0.82));
    }

    private static OreDepositGeometry.Bounds bounds(
            OreDepositGeometry.Body body,
            List<Segment> segments,
            double exposedHalo
    ) {
        OreDepositGeometry.Bounds bodyBounds = body.bounds();
        BoundsAccumulator bounds = new BoundsAccumulator(
                bodyBounds.minX(),
                bodyBounds.minY(),
                bodyBounds.minZ(),
                bodyBounds.maxX(),
                bodyBounds.maxY(),
                bodyBounds.maxZ()
        );
        for (Segment segment : segments) {
            WorldPoint start = toWorld(body, segment.start());
            WorldPoint end = toWorld(body, segment.end());
            double padding = segment.radius() + exposedHalo + 1.0;
            bounds = bounds.include(start, padding).include(end, padding);
        }
        return bounds.toBounds();
    }

    private static LocalPoint toLocal(OreDepositGeometry.Body body, int x, int y, int z) {
        double dx = (double) x - body.anchorX();
        double dy = (double) y - body.anchorY();
        double dz = (double) z - body.anchorZ();
        double cosAzimuth = Math.cos(body.azimuthRadians());
        double sinAzimuth = Math.sin(body.azimuthRadians());
        double cosDip = Math.cos(body.dipRadians());
        double sinDip = Math.sin(body.dipRadians());
        return new LocalPoint(
                dx * cosAzimuth * cosDip + dy * sinDip + dz * sinAzimuth * cosDip,
                -dx * sinAzimuth + dz * cosAzimuth,
                -dx * cosAzimuth * sinDip + dy * cosDip - dz * sinAzimuth * sinDip
        );
    }

    private static WorldPoint toWorld(OreDepositGeometry.Body body, LocalPoint point) {
        double cosAzimuth = Math.cos(body.azimuthRadians());
        double sinAzimuth = Math.sin(body.azimuthRadians());
        double cosDip = Math.cos(body.dipRadians());
        double sinDip = Math.sin(body.dipRadians());
        return new WorldPoint(
                body.anchorX() + point.along() * cosAzimuth * cosDip
                        - point.across() * sinAzimuth
                        - point.normal() * cosAzimuth * sinDip,
                body.anchorY() + point.along() * sinDip + point.normal() * cosDip,
                body.anchorZ() + point.along() * sinAzimuth * cosDip
                        + point.across() * cosAzimuth
                        - point.normal() * sinAzimuth * sinDip
        );
    }

    private static double roll(OreDepositGeometry.Body body, long salt) {
        long identity = Integer.toUnsignedLong(body.material().hashCode())
                ^ Long.rotateLeft(Integer.toUnsignedLong(body.style().hashCode()), 32);
        return GeologyDeterminism.unitRoll(
                body.worldSeed(),
                body.anchorX(),
                body.anchorY(),
                body.anchorZ(),
                salt ^ identity
        );
    }

    private static double distanceSquaredToSegment(LocalPoint point, Segment segment) {
        double along = segment.end().along() - segment.start().along();
        double across = segment.end().across() - segment.start().across();
        double normal = segment.end().normal() - segment.start().normal();
        double lengthSquared = along * along + across * across + normal * normal;
        double projection = ((point.along() - segment.start().along()) * along
                + (point.across() - segment.start().across()) * across
                + (point.normal() - segment.start().normal()) * normal) / lengthSquared;
        double fraction = Math.min(1.0, Math.max(0.0, projection));
        double deltaAlong = point.along() - (segment.start().along() + along * fraction);
        double deltaAcross = point.across() - (segment.start().across() + across * fraction);
        double deltaNormal = point.normal() - (segment.start().normal() + normal * fraction);
        return deltaAlong * deltaAlong + deltaAcross * deltaAcross + deltaNormal * deltaNormal;
    }

    private static LocalPoint scale(LocalPoint point, double factor) {
        return new LocalPoint(point.along() * factor, point.across() * factor, point.normal() * factor);
    }

    private static LocalPoint add(LocalPoint... points) {
        double along = 0.0;
        double across = 0.0;
        double normal = 0.0;
        for (LocalPoint point : points) {
            along += point.along();
            across += point.across();
            normal += point.normal();
        }
        return new LocalPoint(along, across, normal);
    }

    private record LocalPoint(double along, double across, double normal) {
    }

    private record WorldPoint(double x, double y, double z) {
    }

    private record Segment(LocalPoint start, LocalPoint end, double radius) {
    }

    private record BoundsAccumulator(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        private BoundsAccumulator include(WorldPoint point, double padding) {
            return new BoundsAccumulator(
                    Math.min(minX, point.x() - padding),
                    Math.min(minY, point.y() - padding),
                    Math.min(minZ, point.z() - padding),
                    Math.max(maxX, point.x() + padding),
                    Math.max(maxY, point.y() + padding),
                    Math.max(maxZ, point.z() + padding)
            );
        }

        private OreDepositGeometry.Bounds toBounds() {
            return new OreDepositGeometry.Bounds(
                    (int) Math.floor(minX),
                    (int) Math.floor(minY),
                    (int) Math.floor(minZ),
                    (int) Math.ceil(maxX),
                    (int) Math.ceil(maxY),
                    (int) Math.ceil(maxZ)
            );
        }
    }

    public enum Proximity {
        OUTSIDE,
        NEAR_STRINGER,
        STRINGER
    }

    private static final class SegmentSampler {
        private final double startAlong;
        private final double startAcross;
        private final double startNormal;
        private final double deltaAlong;
        private final double deltaAcross;
        private final double deltaNormal;
        private final double lengthSquared;
        private final double radiusSquared;
        private final double haloRadiusSquared;
        private final double minAlong;
        private final double maxAlong;
        private final double minAcross;
        private final double maxAcross;
        private final double minNormal;
        private final double maxNormal;

        private SegmentSampler(Segment segment, double exposedHalo) {
            startAlong = segment.start().along();
            startAcross = segment.start().across();
            startNormal = segment.start().normal();
            deltaAlong = segment.end().along() - startAlong;
            deltaAcross = segment.end().across() - startAcross;
            deltaNormal = segment.end().normal() - startNormal;
            lengthSquared = deltaAlong * deltaAlong + deltaAcross * deltaAcross + deltaNormal * deltaNormal;
            double radius = segment.radius();
            double haloRadius = radius + exposedHalo;
            radiusSquared = radius * radius;
            haloRadiusSquared = haloRadius * haloRadius;
            minAlong = Math.min(startAlong, segment.end().along()) - haloRadius;
            maxAlong = Math.max(startAlong, segment.end().along()) + haloRadius;
            minAcross = Math.min(startAcross, segment.end().across()) - haloRadius;
            maxAcross = Math.max(startAcross, segment.end().across()) + haloRadius;
            minNormal = Math.min(startNormal, segment.end().normal()) - haloRadius;
            maxNormal = Math.max(startNormal, segment.end().normal()) + haloRadius;
        }

        private boolean canReach(double along, double across, double normal) {
            return along >= minAlong && along <= maxAlong
                    && across >= minAcross && across <= maxAcross
                    && normal >= minNormal && normal <= maxNormal;
        }

        private double distanceSquared(double along, double across, double normal) {
            double projection = ((along - startAlong) * deltaAlong
                    + (across - startAcross) * deltaAcross
                    + (normal - startNormal) * deltaNormal) / lengthSquared;
            double fraction = Math.min(1.0, Math.max(0.0, projection));
            double deltaPointAlong = along - (startAlong + deltaAlong * fraction);
            double deltaPointAcross = across - (startAcross + deltaAcross * fraction);
            double deltaPointNormal = normal - (startNormal + deltaNormal * fraction);
            return deltaPointAlong * deltaPointAlong
                    + deltaPointAcross * deltaPointAcross
                    + deltaPointNormal * deltaPointNormal;
        }
    }

    /** Reuses immutable transforms and culls distant stringer segments before projection math. */
    public static final class Sampler {
        private final Field field;
        private final SegmentSampler[] segments;
        private final double cosAzimuth;
        private final double sinAzimuth;
        private final double cosDip;
        private final double sinDip;

        private Sampler(Field field) {
            this.field = field;
            segments = new SegmentSampler[field.segments().size()];
            for (int index = 0; index < segments.length; index++) {
                segments[index] = new SegmentSampler(
                        field.segments().get(index),
                        field.exposedHaloBlocks()
                );
            }
            cosAzimuth = Math.cos(field.body().azimuthRadians());
            sinAzimuth = Math.sin(field.body().azimuthRadians());
            cosDip = Math.cos(field.body().dipRadians());
            sinDip = Math.sin(field.body().dipRadians());
        }

        public Proximity proximity(int x, int y, int z) {
            if (segments.length == 0 || !field.bounds().contains(x, y, z)) {
                return Proximity.OUTSIDE;
            }

            OreDepositGeometry.Body body = field.body();
            double dx = (double) x - body.anchorX();
            double dy = (double) y - body.anchorY();
            double dz = (double) z - body.anchorZ();
            double along = dx * cosAzimuth * cosDip + dy * sinDip + dz * sinAzimuth * cosDip;
            double across = -dx * sinAzimuth + dz * cosAzimuth;
            double normal = -dx * cosAzimuth * sinDip + dy * cosDip - dz * sinAzimuth * sinDip;
            boolean near = false;
            for (SegmentSampler segment : segments) {
                if (!segment.canReach(along, across, normal)) {
                    continue;
                }
                double distanceSquared = segment.distanceSquared(along, across, normal);
                if (distanceSquared <= segment.radiusSquared) {
                    return Proximity.STRINGER;
                }
                near |= distanceSquared <= segment.haloRadiusSquared;
            }
            return near ? Proximity.NEAR_STRINGER : Proximity.OUTSIDE;
        }
    }

    public record Field(
            OreDepositGeometry.Body body,
            List<Segment> segments,
            OreDepositGeometry.Bounds bounds,
            double exposedHaloBlocks
    ) {
        public Field {
            if (body == null || segments == null || bounds == null
                    || !Double.isFinite(exposedHaloBlocks) || exposedHaloBlocks < 0.0) {
                throw new IllegalArgumentException("ore discovery field inputs must be valid");
            }
            segments = List.copyOf(segments);
        }

        public boolean enabled() {
            return !segments.isEmpty();
        }

        public Sampler sampler() {
            return new Sampler(this);
        }

        public boolean contains(int x, int y, int z) {
            return contains(x, y, z, 0.0);
        }

        /** Broadens only cave-facing discovery; underground stringers retain their thin radius. */
        public boolean nearStringer(int x, int y, int z) {
            return contains(x, y, z, exposedHaloBlocks);
        }

        private boolean contains(int x, int y, int z, double padding) {
            if (segments.isEmpty() || !bounds.contains(x, y, z)) {
                return false;
            }
            LocalPoint point = toLocal(body, x, y, z);
            double paddingSquared;
            for (Segment segment : segments) {
                double radius = segment.radius() + padding;
                paddingSquared = radius * radius;
                if (distanceSquaredToSegment(point, segment) <= paddingSquared) {
                    return true;
                }
            }
            return false;
        }
    }
}
