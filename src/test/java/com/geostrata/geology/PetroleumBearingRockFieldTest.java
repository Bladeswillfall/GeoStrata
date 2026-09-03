package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetroleumBearingRockFieldTest {
    @Test
    void shaleAndSandExpressionsRequireRichReservoirCore() {
        HydrocarbonReservoirField.Reservoir rich = reservoir(0.80, 0.80);
        HydrocarbonReservoirField.Reservoir dilute = reservoir(0.80, 0.30);
        HydrocarbonReservoirField.Reservoir lowPressure = reservoir(0.40, 0.80);

        assertTrue(PetroleumBearingRockField.oilShale(rich));
        assertFalse(PetroleumBearingRockField.oilShale(dilute));
        assertTrue(PetroleumBearingRockField.oilSands(rich));
        assertFalse(PetroleumBearingRockField.oilSands(dilute));
        assertFalse(PetroleumBearingRockField.oilSands(lowPressure));
    }

    @Test
    void unrelatedLithologiesNeverTriggerReservoirSampling() {
        PetroleumBearingRockField.Column column = PetroleumBearingRockField.column(8675309L, 0, 0);

        assertEquals(PetroleumBearingRockField.Expression.NONE, column.expression("limestone"));
        assertEquals(PetroleumBearingRockField.Expression.NONE, column.expression("basalt"));
    }

    private static HydrocarbonReservoirField.Reservoir reservoir(double pressure, double concentration) {
        return new HydrocarbonReservoirField.Reservoir(
                0,
                0,
                0,
                0,
                64,
                48,
                pressure,
                concentration,
                8,
                8
        );
    }
}
