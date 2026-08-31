package com.geostrata.geology;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic poor-grade fracture/stringer geometry genetically tied to a parent ore body.
 *
 * <p>Stringers are not independent ore candidates. They reuse the parent body's seed, anchor
 * and orientation, start near its economic margin, then extend outward as thin kinked fractures.
 * The worldgen consumer still applies normal host and structure protection before placing ore.</p>
 */
public final class OreDiscoveryStringers {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double COPPER_EXPOSED_HALO_BLOCKS = 3.0;
    private static final long STRINGER_SALT = 0x6A09E667F3BCC909L;
    private static final long LENGTH_SALT = 0xBB67AE8584CAA73BL;
    private static final long RADIUS_SALT = 0x3C6EF372FE94F82BL;
    private static final long BEND_SALT = 0xA54FF53A5F1D36F1L;
    private static final long NORMAL_SALT = 0x510E527FADE682D1L;

    private OreDiscoveryStringers() {
    }

    public static Field forBody(OreDepositGeometry.Body body) {
        if (body == null) {
            throw new IllegalArgumentException("ore body must not be null");
        }
        Profile profile = profile(body.material());
        if (profile == null) {
            return new Field(body, List.of(), body.bounds());
        }

        List<Segment> segments = new ArrayList<>(profile.count() * 2);
        for (int index = 0; index < profile.count(); index++) {
            addStringer(body, profile, index, segments);
        }
        return new Field(body, List.copyOf(segments), bounds(body, segments));
    }

    private static Profile profile(String material) {
        return switch (material) {
            case "iron" -> new Profile(14, 52.0, 96.0, 0.62, 0.88);
            case "copper" -> new Profile(12, 48.0, 88.0, 0.58, 0.84);
            default -> null;
        };
    }

    private static void addStringer(
            OreDepositGeometry.Body body,
            Profile profile,
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
        double verticalBias = "copper".equals(body.material()) && index < 4 ? -1.0 : 0.0;
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

    private static double exposedHaloBlocks(OreDepositGeometry.Body body) {
        return "copper".equals(body.material()) ? COPPER_EXPOSED_HALO_BLOCKS : 0.0;
    }

    private static BoundsAccumulator worldBounds(
            OreDepositGeometry.Body body,
            Segment segment,
            BoundsAccumulator bounds
    ) {
        WorldPoint start = toWorld(body, segment.start());
        WorldPoint end = toWorld(body, segment.end());
        double radius = segment.radius() + exposedHaloBlocks(body) + 1.0;
        return bounds.include(start, radius).include(end, radius);
    }

    private static OreDepositGeometry.Bounds bounds(
            OreDepositGeometry.Body body,
            List<Segment> segments
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
            bounds = worldBounds(body, segment, bounds);
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

    private static double distanceToSegment(LocalPoint point, Segment segment) {
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
        return Math.sqrt(deltaAlong * deltaAlong + deltaAcross * deltaAcross + deltaNormal * deltaNormal);
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

    private record Profile(int count, double minLength, double maxLength, double minRadius, double maxRadius) {
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

    public record Field(
            OreDepositGeometry.Body body,
            List<Segment> segments,
            OreDepositGeometry.Bounds bounds
    ) {
        public Field {
            if (body == null || segments == null || bounds == null) {
                throw new IllegalArgumentException("ore discovery field inputs must not be null");
            }
            segments = List.copyOf(segments);
        }

        public boolean enabled() {
            return !segments.isEmpty();
        }

        public boolean contains(int x, int y, int z) {
            return contains(x, y, z, 0.0);
        }

        /** Broadens only cave-facing discovery; underground stringers remain their original thin radius. */
        public boolean nearStringer(int x, int y, int z) {
            return contains(x, y, z, exposedHaloBlocks(body));
        }

        private boolean contains(int x, int y, int z, double padding) {
            if (segments.isEmpty() || !bounds.contains(x, y, z)) {
                return false;
            }
            LocalPoint point = toLocal(body, x, y, z);
            for (Segment segment : segments) {
                if (distanceToSegment(point, segment) <= segment.radius() + padding) {
                    return true;
                }
            }
            return false;
        }
    }
}
