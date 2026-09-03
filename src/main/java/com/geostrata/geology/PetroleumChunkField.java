package com.geostrata.geology;

/** Maps GeoStrata reservoir quality onto provider-owned chunk petroleum reserves. */
public final class PetroleumChunkField {
    static final int MIN_OIL_MB = 8_000;
    static final int MAX_OIL_MB = 400_000;
    static final double FREE_CRUDE_PRESSURE = 0.90;

    private PetroleumChunkField() {
    }

    /**
     * Uses Create: Diesel Generators' 1.20.1 rich-chunk range without owning its fluid or pump mechanics.
     */
    public static int oilAmount(HydrocarbonReservoirField.Reservoir reservoir) {
        if (reservoir == null) {
            return 0;
        }
        double richness = reservoir.pressure() * reservoir.concentration();
        double scaled = richness * richness;
        return MIN_OIL_MB + (int) Math.round((MAX_OIL_MB - MIN_OIL_MB) * scaled);
    }

    /** Very high-pressure reservoirs may expose rare bucketable free crude at their seep. */
    public static boolean exposesFreeCrude(HydrocarbonReservoirField.Reservoir reservoir) {
        return reservoir != null && reservoir.pressure() >= FREE_CRUDE_PRESSURE;
    }
}
