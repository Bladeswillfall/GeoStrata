package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrogenicFaultDipTest {
    @Test
    void orogenicReverseFaultsUseModeratelyLowAnglePlanes() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                246813579L,
                GeologyProvince.OROGENIC_BELT,
                0,
                0,
                48.0
        );

        assertTrue(Math.abs(field.faultDipShiftPerVerticalBlock()) >= 0.90);
        assertTrue(Math.abs(field.faultDipShiftPerVerticalBlock()) <= 1.50);
        assertTrue(field.faultDipDegrees() > 30.0 && field.faultDipDegrees() < 50.0);
        assertEquals(TectonicStructuralField.FaultRegime.REVERSE, field.faultRegime());
    }

    @Test
    void dynamicStructuralOffsetMovesOrogenicFabric() {
        OrogenicBeltModel.Context context = OrogenicBeltModel.forSite(987654321L, 0, 0, 63.0);
        boolean foundDifference = false;
        for (int x = -320; x <= 320 && !foundDifference; x += 16) {
            OrogenicBeltModel.Column column = context.column(x, 0, 0.0);
            for (int y = -32; y <= 128; y += 8) {
                if (!column.sample(y, 0.0).equals(column.sample(y, 48.0))) {
                    foundDifference = true;
                    break;
                }
            }
        }
        assertTrue(foundDifference);
    }
}
