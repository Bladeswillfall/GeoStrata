package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TectonicFoldClosuresTest {
    @Test
    void closuresPreserveTheProvinceSiteAnchor() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                987654321L,
                GeologyProvince.OROGENIC_BELT,
                128,
                -256,
                48.0
        );

        assertEquals(0.0, TectonicFoldClosures.offset(field, 128, -256), 1.0e-9);
    }

    @Test
    void axialEnvelopeCreatesBroadFoldNosesWithoutIncreasingAmplitude() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                123456789L,
                GeologyProvince.OROGENIC_BELT,
                0,
                0,
                48.0
        );
        boolean foundStrongClosure = false;

        for (int x = -2048; x <= 2048; x += 64) {
            for (int z = -2048; z <= 2048; z += 64) {
                double raw = field.column(x, z).foldOffset();
                double closed = TectonicFoldClosures.offset(field, x, z);
                assertTrue(Math.abs(closed) <= Math.abs(raw) + 1.0e-9);
                if (Math.abs(raw) > 1.0 && Math.abs(closed) < Math.abs(raw) * 0.25) {
                    foundStrongClosure = true;
                }
            }
        }

        assertTrue(foundStrongClosure, "expected the axial envelope to taper at least one fold limb");
    }
}
