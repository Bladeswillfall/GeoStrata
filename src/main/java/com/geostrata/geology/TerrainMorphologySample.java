package com.geostrata.geology;

/**
 * Pure, generator-agnostic summary of broad terrain morphology around one X/Z sample.
 *
 * <p>The caller owns how heights are obtained. Core geology can therefore consume vanilla
 * ChunkGenerator observations or optional terrain-mod adapters without importing those APIs
 * into the mathematical model. Structural geology should normally build these samples from
 * coarse spacing rather than reacting to individual surface blocks.</p>
 */
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

    /**
     * Builds a centered morphology sample from cardinal height observations at equal spacing.
     * Positive prominence means the center stands above its cardinal surroundings; negative
     * prominence means it lies below them.
     */
    public static TerrainMorphologySample fromCardinalHeights(
            double centerHeight,
            double westHeight,
            double eastHeight,
            double northHeight,
            double southHeight,
            double sampleSpacingBlocks
    ) {
        validateInputs(
                centerHeight,
                westHeight,
                eastHeight,
                northHeight,
                southHeight,
                sampleSpacingBlocks
        );

        double gradientX = (eastHeight - westHeight) / (2.0 * sampleSpacingBlocks);
        double gradientZ = (southHeight - northHeight) / (2.0 * sampleSpacingBlocks);
        double minimumHeight = Math.min(
                centerHeight,
                Math.min(Math.min(westHeight, eastHeight), Math.min(northHeight, southHeight))
        );
        double maximumHeight = Math.max(
                centerHeight,
                Math.max(Math.max(westHeight, eastHeight), Math.max(northHeight, southHeight))
        );
        double neighborMean = (westHeight + eastHeight + northHeight + southHeight) / 4.0;

        return new TerrainMorphologySample(
                centerHeight,
                gradientX,
                gradientZ,
                maximumHeight - minimumHeight,
                centerHeight - neighborMean
        );
    }

    /** Horizontal rise per block represented by the centered gradient. */
    public double slopeMagnitude() {
        return Math.hypot(gradientX, gradientZ);
    }

    private static void validateInputs(
            double centerHeight,
            double westHeight,
            double eastHeight,
            double northHeight,
            double southHeight,
            double sampleSpacingBlocks
    ) {
        if (!Double.isFinite(centerHeight)
                || !Double.isFinite(westHeight)
                || !Double.isFinite(eastHeight)
                || !Double.isFinite(northHeight)
                || !Double.isFinite(southHeight)) {
            throw new IllegalArgumentException("terrain height observations must be finite");
        }
        if (!Double.isFinite(sampleSpacingBlocks) || sampleSpacingBlocks <= 0.0) {
            throw new IllegalArgumentException("terrain sample spacing must be finite and positive");
        }
    }
}
