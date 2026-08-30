package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TectonicFoldPolarityTest {
    @Test
    void onlyOrogenicFoldsCanActivatePolarityReversal() {
        TectonicStructuralField.Context rift = TectonicStructuralField.forSite(
                123456789L,
                GeologyProvince.RIFT_PROVINCE,
                0,
                0,
                48.0
        );

        assertFalse(TectonicFoldPolarity.forField(
                GeologyProvince.RIFT_PROVINCE,
                rift,
                48.0,
                80.0
        ).active());
    }

    @Test
    void activeOrogenicProfileContainsNormalAndOverturnedLimbs() {
        ActiveField active = activeField();
        boolean foundNormal = false;
        boolean foundOverturned = false;
        boolean foundNearVertical = false;

        for (int x = -2048; x <= 2048; x += 32) {
            for (int z = -2048; z <= 2048; z += 32) {
                double scale = active.profile().transform(active.field(), x, z).verticalScale();
                foundNormal |= scale > 0.9;
                foundOverturned |= scale < -0.1;
                foundNearVertical |= Math.abs(scale) < 0.15;
            }
        }

        assertTrue(foundNormal, "expected an ordinary younging-up limb");
        assertTrue(foundNearVertical, "expected a near-vertical hinge transition");
        assertTrue(foundOverturned, "expected a reversed-polarity limb");
    }

    @Test
    void overturnedTransformReversesYoungingDirectionWithHeight() {
        ActiveField active = activeField();
        TectonicFoldPolarity.Transform overturned = null;

        for (int x = -2048; x <= 2048 && overturned == null; x += 32) {
            for (int z = -2048; z <= 2048; z += 32) {
                TectonicFoldPolarity.Transform candidate = active.profile().transform(active.field(), x, z);
                if (candidate.verticalScale() < -0.1) {
                    overturned = candidate;
                    break;
                }
            }
        }

        assertTrue(overturned != null, "expected an overturned test column");
        double lower = overturned.stratigraphicY(20.0, 4.0);
        double upper = overturned.stratigraphicY(21.0, 4.0);
        assertTrue(upper < lower, "stratigraphic coordinate must decrease upward on an overturned limb");
    }

    private static ActiveField activeField() {
        for (long seed = 0; seed < 4096; seed++) {
            TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                    seed,
                    GeologyProvince.OROGENIC_BELT,
                    0,
                    0,
                    48.0
            );
            TectonicFoldPolarity.Profile profile = TectonicFoldPolarity.forField(
                    GeologyProvince.OROGENIC_BELT,
                    field,
                    48.0,
                    80.0
            );
            if (profile.active()) {
                return new ActiveField(field, profile);
            }
        }
        throw new AssertionError("expected at least one active orogenic fold profile");
    }

    private record ActiveField(
            TectonicStructuralField.Context field,
            TectonicFoldPolarity.Profile profile
    ) {
    }
}
