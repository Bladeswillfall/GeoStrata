package com.geostrata.worldgen.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrataLensGeometryTest {
    private static final double EPSILON = 1.0E-12;

    @Test
    void shortRadiusUsesConfiguredRatioWithSafeMinimum() {
        assertEquals(2, StrataLensGeometry.shortRadius(4, 0.20));
        assertEquals(6, StrataLensGeometry.shortRadius(9, 0.66));
        assertEquals(6, StrataLensGeometry.shortRadius(8, 0.72));
    }

    @Test
    void rotatedEllipseKeepsMajorAndMinorAxesOnBoundary() {
        double cos = Math.cos(Math.PI / 2.0);
        double sin = Math.sin(Math.PI / 2.0);

        double majorAlong = StrataLensGeometry.along(0, 9, cos, sin);
        double majorAcross = StrataLensGeometry.across(0, 9, cos, sin);
        double majorRadial = StrataLensGeometry.radial(majorAlong, majorAcross, 9, 6);

        double minorAlong = StrataLensGeometry.along(-6, 0, cos, sin);
        double minorAcross = StrataLensGeometry.across(-6, 0, cos, sin);
        double minorRadial = StrataLensGeometry.radial(minorAlong, minorAcross, 9, 6);

        assertEquals(1.0, majorRadial, EPSILON);
        assertEquals(1.0, minorRadial, EPSILON);
        assertTrue(StrataLensGeometry.inside(majorRadial));
        assertTrue(StrataLensGeometry.inside(minorRadial));
        assertFalse(StrataLensGeometry.inside(1.000001));
    }

    @Test
    void thicknessTapersFromCenterToConfiguredEdgeFloor() {
        assertEquals(1.75, StrataLensGeometry.halfThickness(0.0, 1.75, 0.65), EPSILON);
        assertEquals(0.875, StrataLensGeometry.halfThickness(0.75, 1.75, 0.65), EPSILON);
        assertEquals(0.65, StrataLensGeometry.halfThickness(1.0, 1.75, 0.65), EPSILON);
    }

    @Test
    void centerOffsetComposesSlopeAndWarpWithoutHiddenState() {
        assertEquals(
                0.4,
                StrataLensGeometry.centerOffset(4.0, 2.0, 0.1, 0.0, 6.0, 0.0),
                EPSILON
        );
        assertEquals(
                0.5,
                StrataLensGeometry.centerOffset(0.0, 0.0, 0.0, 0.5, 6.0, Math.PI / 2.0),
                EPSILON
        );
        assertEquals(
                -0.5,
                StrataLensGeometry.centerOffset(0.0, 0.0, 0.0, 0.5, 6.0, -Math.PI / 2.0),
                EPSILON
        );
    }
}
