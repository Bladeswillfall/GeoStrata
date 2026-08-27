package com.geostrata.geology;

/** Pure summary of coarse terrain heights around one X/Z sample. */
public record TerrainMorphologySample(
        double centerHeight,
        double gradientX,
        double gradientZ,
        double relief,
        double prominence
) {
    public TerrainMorphologySample {
        if (!Double.isFinite(centerHeight)
                || !Double.isFinite(gradientX)
                || !Double.isFinite(gradientZ)
                || !Double.isFinite(relief)
                || !Double.isFinite(prominence)) {
            throw new IllegalArgumentException("terrain morphology values must be finite");
        }
        if (relief < 0.0) {
            throw new IllegalArgumentException("terrain relief must not be negative");
        }
    }

    public static TerrainMorphologySample fromCardinalHeights(
            double centerHeight,
            double westHeight,
            double eastHeight,
            double northHeight,
            double southHeight,
            double sampleSpacingBlocks
    ) {
        validateHeights(centerHeight, westHeight, eastHeight, northHeight, southHeight);
        if (!Double.isFinite(sampleSpacingBlocks) || sampleSpacingBlocks <= 0.0) {
            throw new IllegalArgumentException("terrain sample spacing must be finite and positive");
        }

        double minimum = Math.min(
                centerHeight,
                Math.min(Math.min(westHeight, eastHeight), Math.min(northHeight, southHeight))
        );
        double maximum = Math.max(
                centerHeight,
                Math.max(Math.max(westHeight, eastHeight), Math.max(northHeight, southHeight))
        );
        double neighborMean = (westHeight + eastHeight + northHeight + southHeight) / 4.0;
        return new TerrainMorphologySample(
                centerHeight,
                (eastHeight - westHeight) / (2.0 * sampleSpacingBlocks),
                (southHeight - northHeight) / (2.0 * sampleSpacingBlocks),
                maximum - minimum,
                centerHeight - neighborMean
        );
    }

    public double slopeMagnitude() {
        return Math.hypot(gradientX, gradientZ);
    }

    private static void validateHeights(double... heights) {
        for (double height : heights) {
            if (!Double.isFinite(height)) {
                throw new IllegalArgumentException("terrain height observations must be finite");
            }
        }
    }
}
