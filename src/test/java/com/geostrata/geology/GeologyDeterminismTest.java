package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeologyDeterminismTest {
    private static final long STRATA_SALT = 0x6A09E667F3BCC909L;
    private static final double EPSILON = 1.0e-15;

    @Test
    void runtimeWorldgenRollRegressionVectorsRemainStable() {
        assertEquals(0.35851669857696533, GeologyDeterminism.unitRoll(0L, 0, 64, 0, STRATA_SALT), EPSILON);
        assertEquals(0.020759994184443742, GeologyDeterminism.unitRoll(1L, 0, 64, 0, STRATA_SALT), EPSILON);
        assertEquals(0.8805485957346113, GeologyDeterminism.unitRoll(123456789L, 1000, -24, -500, STRATA_SALT), EPSILON);
        assertEquals(0.6748267704104395, GeologyDeterminism.unitRoll(-42L, -2000, 120, 3000, STRATA_SALT), EPSILON);
        assertEquals(0.23120823782631228, GeologyDeterminism.unitRoll(987654321L, 100000, -48, 100000, STRATA_SALT), EPSILON);
    }

    @Test
    void rollAlwaysStaysWithinUnitInterval() {
        for (int i = -100; i <= 100; i++) {
            double roll = GeologyDeterminism.unitRoll(8675309L, i * 7919, i, i * -104729, STRATA_SALT);
            assertTrue(roll >= 0.0 && roll < 1.0, "roll out of range: " + roll);
        }
    }
}
