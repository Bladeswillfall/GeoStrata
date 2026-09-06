package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SandstoneRedoxFieldTest {
    private static final long SEED = 0x5EED1234L;

    @Test
    void fieldIsSparseDeterministicAndRestrictedToSandstoneBasins() {
        int samples = 0;
        int fronts = 0;
        int firstX = 0;
        int firstZ = 0;
        boolean found = false;
        for (int x = -640; x <= 640; x += 16) {
            for (int z = -640; z <= 640; z += 16) {
                samples++;
                if (SandstoneRedoxField.contains(SEED, x, z, GeologyProvince.SEDIMENTARY_BASIN)) {
                    fronts++;
                    if (!found) {
                        firstX = x;
                        firstZ = z;
                        found = true;
                    }
                }
            }
        }

        assertTrue(found);
        assertTrue(fronts > samples / 20);
        assertTrue(fronts < samples / 3);
        assertTrue(SandstoneRedoxField.contains(SEED, firstX, firstZ, GeologyProvince.SEDIMENTARY_BASIN));
        assertEquals(
                SandstoneRedoxField.BODY_STYLE,
                SandstoneRedoxField.bodyStyle(
                        SEED,
                        firstX,
                        firstZ,
                        "sandstone",
                        GeologyProvince.SEDIMENTARY_BASIN
                ).orElseThrow()
        );
        assertTrue(SandstoneRedoxField.bodyStyle(
                SEED,
                firstX,
                firstZ,
                "granite",
                GeologyProvince.SEDIMENTARY_BASIN
        ).isEmpty());
        assertTrue(SandstoneRedoxField.bodyStyle(
                SEED,
                firstX,
                firstZ,
                "sandstone",
                GeologyProvince.CRATONIC_SHIELD
        ).isEmpty());
        assertFalse(SandstoneRedoxField.contains(SEED, firstX, firstZ, GeologyProvince.CRATONIC_SHIELD));
    }
}
