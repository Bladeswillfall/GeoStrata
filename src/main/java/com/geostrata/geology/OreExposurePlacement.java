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

    /** Conservative placement bounds for the parent trace envelope plus any linked discovery stringers. */
    public static OreDepositGeometry.Bounds placementBounds(
            OreDepositGeometry.Body body,
            OreDiscoveryStringers.Field discovery
    ) {
        OreDepositGeometry.Bounds trace = placementBounds(body);
        if (discovery == null || !discovery.enabled()) {
            return trace;
        }
        OreDepositGeometry.Bounds stringers = discovery.bounds();
        return new OreDepositGeometry.Bounds(
                Math.min(trace.minX(), stringers.minX()),
                Math.min(trace.minY(), stringers.minY()),
                Math.min(trace.minZ(), stringers.minZ()),
                Math.max(trace.maxX(), stringers.maxX()),
                Math.max(trace.maxY(), stringers.maxY()),
                Math.max(trace.maxZ(), stringers.maxZ())
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

    /** Parent-body grade wins; otherwise a linked discovery fracture is always poor grade. */
    public static OreGrade placementGrade(
            OreDepositGeometry.Sample sample,
            boolean exposedToAir,
            boolean discoveryStringer
    ) {
        OreGrade parentGrade = placementGrade(sample, exposedToAir);
        return parentGrade != null ? parentGrade : discoveryStringer ? OreGrade.POOR : null;
    }
}
