package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetroleumChunkFieldTest {
    @Test
    void richerReservoirsSeedLargerProviderOilChunks() {
        int edge = PetroleumChunkField.oilAmount(reservoir(0.35, 0.0));
        int medium = PetroleumChunkField.oilAmount(reservoir(0.65, 0.55));
        int rich = PetroleumChunkField.oilAmount(reservoir(1.0, 1.0));

        assertEquals(PetroleumChunkField.MIN_OIL_MB, edge);
        assertTrue(medium > edge);
        assertEquals(PetroleumChunkField.MAX_OIL_MB, rich);
        assertEquals(0, PetroleumChunkField.oilAmount(null));
    }

    @Test
    void onlyVeryHighPressureReservoirsExposeFreeCrude() {
        assertFalse(PetroleumChunkField.exposesFreeCrude(reservoir(0.89, 1.0)));
        assertTrue(PetroleumChunkField.exposesFreeCrude(reservoir(0.90, 0.2)));
    }

    private static HydrocarbonReservoirField.Reservoir reservoir(double pressure, double concentration) {
        return new HydrocarbonReservoirField.Reservoir(0, 0, 0, 0, 64, 48, pressure, concentration, 8, 8);
    }
}
