package com.geostrata.geology;

/** Small shared policy for visually fractured host rock around active fault planes. */
public final class FaultDamageZone {
    private static final double RIFT_HALF_WIDTH_BLOCKS = 1.35;
    private static final double OROGENIC_HALF_WIDTH_BLOCKS = 1.10;

    private FaultDamageZone() {
    }

    public static boolean contains(
            GeologyProvince province,
            TectonicStructuralField.Column faultColumn,
            double y
    ) {
        if (province == null || faultColumn == null) {
            throw new IllegalArgumentException("fault damage-zone context must not be null");
        }
        double halfWidth = switch (province) {
            case RIFT_PROVINCE -> RIFT_HALF_WIDTH_BLOCKS;
            case OROGENIC_BELT -> OROGENIC_HALF_WIDTH_BLOCKS;
            default -> 0.0;
        };
        return halfWidth > 0.0 && faultColumn.distanceToFault(y) <= halfWidth;
    }
}
