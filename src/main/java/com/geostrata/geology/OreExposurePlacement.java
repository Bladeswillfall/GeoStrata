package com.geostrata.geology;

/** Placement policy for discoverable cave/ravine margins around graded ore bodies. */
public final class OreExposurePlacement {
    private static final int TRACE_PADDING_BLOCKS = 24;

    private OreExposurePlacement() {
    }

    /** Conservative placement bounds including the current non-economic trace envelope. */
    public static OreDepositGeometry.Bounds placementBounds(OreDepositGeometry.Body body) {
        if (body == null) {
            throw new IllegalArgumentException("ore body must not be null");
        }
        OreDepositGeometry.Bounds bounds = body.bounds();
        return new OreDepositGeometry.Bounds(
                bounds.minX() - TRACE_PADDING_BLOCKS,
                bounds.minY() - TRACE_PADDING_BLOCKS,
                bounds.minZ() - TRACE_PADDING_BLOCKS,
                bounds.maxX() + TRACE_PADDING_BLOCKS,
                bounds.maxY() + TRACE_PADDING_BLOCKS,
                bounds.maxZ() + TRACE_PADDING_BLOCKS
        );
    }

    /**
     * Keeps normal economic grading unchanged while allowing only air-exposed trace host
     * to become a poor-grade discovery fringe.
     */
    public static OreGrade placementGrade(OreDepositGeometry.Sample sample, boolean exposedToAir) {
        if (sample == null) {
            throw new IllegalArgumentException("ore sample must not be null");
        }
        if (sample.economic()) {
            return sample.grade();
        }
        return sample.trace() && exposedToAir ? OreGrade.POOR : null;
    }
}
