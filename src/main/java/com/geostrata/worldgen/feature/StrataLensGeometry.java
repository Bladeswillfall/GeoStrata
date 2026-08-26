package com.geostrata.worldgen.feature;

/** Pure, allocation-free geometry helpers shared by strata-lens worldgen and regression tests. */
final class StrataLensGeometry {
    private StrataLensGeometry() {
    }

    static int shortRadius(int longRadius, double ratio) {
        return Math.max(2, (int) Math.round(longRadius * ratio));
    }

    static double along(int dx, int dz, double cos, double sin) {
        return dx * cos + dz * sin;
    }

    static double across(int dx, int dz, double cos, double sin) {
        return -dx * sin + dz * cos;
    }

    static double radial(double along, double across, int longRadius, int shortRadius) {
        return square(along / longRadius) + square(across / shortRadius);
    }

    static boolean inside(double radial) {
        return radial <= 1.0;
    }

    static double centerOffset(
            double along,
            double across,
            double slope,
            double warpAmplitude,
            double warpWavelength,
            double warpPhase
    ) {
        return slope * along
                + warpAmplitude * Math.sin((along + across * 0.35) / warpWavelength + warpPhase);
    }

    static double halfThickness(double radial, double centerHalfThickness, double edgeHalfThickness) {
        double taper = Math.sqrt(Math.max(0.0, 1.0 - radial));
        return Math.max(edgeHalfThickness, centerHalfThickness * taper);
    }

    private static double square(double value) {
        return value * value;
    }
}
