package com.geostrata.geology;

/** Axial amplitude modulation that lets long tectonic folds terminate into broad noses/closures. */
public final class TectonicFoldClosures {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double AXIS_WAVELENGTH_MULTIPLIER = 3.25;
    private static final double MINIMUM_ENVELOPE = 0.08;

    private TectonicFoldClosures() {
    }

    public static double envelope(TectonicStructuralField.Context field, int x, int z) {
        if (field == null) {
            throw new IllegalArgumentException("tectonic field must not be null");
        }
        if (field.foldAmplitudeBlocks() == 0.0) {
            return 1.0;
        }

        double dx = (double) x - field.siteX();
        double dz = (double) z - field.siteZ();
        double alongAxis = -dx * field.foldSin() + dz * field.foldCos();
        double phase = TWO_PI * alongAxis
                / (field.foldWavelengthBlocks() * AXIS_WAVELENGTH_MULTIPLIER)
                + field.foldSecondaryPhase();
        return MINIMUM_ENVELOPE
                + (1.0 - MINIMUM_ENVELOPE) * 0.5 * (1.0 + Math.cos(phase));
    }

    public static double offset(TectonicStructuralField.Context field, int x, int z) {
        if (field == null) {
            throw new IllegalArgumentException("tectonic field must not be null");
        }
        TectonicStructuralField.Column column = field.column(x, z);
        return column.foldOffset() * envelope(field, x, z);
    }
}
