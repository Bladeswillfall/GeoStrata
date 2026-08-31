package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerraneSutureTest {
    private static final long SEED = -42L;
    private static final double CYCLE_THICKNESS = 48.0;
    private static final double REFERENCE_Y = 63.0;

    @Test
    void referenceElevationPreservesVoronoiOwnership() {
        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(SEED, -2000, 3000);
        TerraneSuture.Contact contact = contact(sample);

        assertTrue(TerraneSuture.canCross(sample));
        assertTrue(contact.usesPrimary(REFERENCE_Y));
    }

    @Test
    void nearBoundaryContactDipsAndChangesTerraneWithHeight() {
        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(SEED, -2000, 3000);
        TerraneSuture.Contact contact = contact(sample);
        boolean foundNeighbor = false;

        for (int y = -512; y <= 512; y++) {
            foundNeighbor |= !contact.usesPrimary(y);
        }

        assertTrue(foundNeighbor, "a column this close to the boundary should cross the dipping suture");
        assertTrue(contact.dipDegrees() > 65.0 && contact.dipDegrees() < 82.0);
    }

    @Test
    void farInteriorColumnsCannotCrossTheBoundedSuture() {
        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(123456789L, 1000, -500);

        assertFalse(TerraneSuture.canCross(sample));
    }

    private static TerraneSuture.Contact contact(GeologyProvinceSampler.Sample sample) {
        TectonicStructuralField.Context primary = TectonicStructuralField.forSite(
                SEED,
                sample.province(),
                sample.siteX(),
                sample.siteZ(),
                CYCLE_THICKNESS
        );
        TectonicStructuralField.Context neighbor = TectonicStructuralField.forSite(
                SEED,
                sample.neighborProvince(),
                sample.neighborSiteX(),
                sample.neighborSiteZ(),
                CYCLE_THICKNESS
        );
        return TerraneSuture.forColumn(sample, primary, neighbor, REFERENCE_Y);
    }
}
