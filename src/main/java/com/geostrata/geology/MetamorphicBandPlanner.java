package com.geostrata.geology;

import java.util.Optional;

/**
 * Pure selector for coherent metamorphic-grade bands.
 *
 * <p>The caller supplies the existing structural field's vertical offset; this
 * class does not invent another dip/fold model. Points sharing the same
 * structure-adjusted band use the same seed-derived roll, while local
 * metamorphic suitability still controls whether slate, schist or gneiss wins.</p>
 */
public final class MetamorphicBandPlanner {
    private static final long BAND_SELECTION_SALT = 0xB7E151628AED2A6BL;

    private MetamorphicBandPlanner() {
    }

    public static Optional<Selection> select(
            long worldSeed,
            int siteX,
            int siteZ,
            double blockY,
            double structuralVerticalOffset,
            double bandThicknessBlocks,
            MetamorphicIntensityField.Suitability suitability
    ) {
        if (!Double.isFinite(blockY) || !Double.isFinite(structuralVerticalOffset)) {
            throw new IllegalArgumentException("metamorphic structural coordinates must be finite");
        }
        if (!Double.isFinite(bandThicknessBlocks) || bandThicknessBlocks < 1.0) {
            throw new IllegalArgumentException("metamorphic band thickness must be finite and at least one block");
        }
        if (suitability == null) {
            throw new IllegalArgumentException("metamorphic suitability must not be null");
        }
        validateWeight(suitability.slate());
        validateWeight(suitability.schist());
        validateWeight(suitability.gneiss());

        double total = suitability.slate() + suitability.schist() + suitability.gneiss();
        if (total <= 0.0) {
            return Optional.empty();
        }

        double structuralCoordinate = (blockY - structuralVerticalOffset) / bandThicknessBlocks;
        if (!Double.isFinite(structuralCoordinate)) {
            throw new IllegalArgumentException("metamorphic structural coordinate must be finite");
        }
        double floor = Math.floor(structuralCoordinate);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("metamorphic band index is outside integer range");
        }
        int bandIndex = (int) floor;
        double roll = GeologyDeterminism.unitRoll(
                worldSeed,
                siteX,
                bandIndex,
                siteZ,
                BAND_SELECTION_SALT
        );
        double choice = roll * total;
        String lithology;
        if (choice < suitability.slate()) {
            lithology = "slate";
        } else if (choice < suitability.slate() + suitability.schist()) {
            lithology = "schist";
        } else {
            lithology = "gneiss";
        }

        return Optional.of(new Selection(lithology, bandIndex, roll, suitability));
    }

    private static void validateWeight(double weight) {
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException("metamorphic suitability weights must be finite and non-negative");
        }
    }

    public record Selection(
            String lithology,
            int bandIndex,
            double roll,
            MetamorphicIntensityField.Suitability suitability
    ) {
    }
}
