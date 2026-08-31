package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OreExposurePlacementTest {
    @Test
    void preservesEconomicGradesAndOnlyExposesTraceAsPoor() {
        assertEquals(
                OreGrade.RICH,
                OreExposurePlacement.placementGrade(new OreDepositGeometry.Sample(0.72, OreGrade.RICH, false), true)
        );
        assertNull(OreExposurePlacement.placementGrade(new OreDepositGeometry.Sample(0.0, null, true), false));
        assertEquals(
                OreGrade.POOR,
                OreExposurePlacement.placementGrade(new OreDepositGeometry.Sample(0.0, null, true), true)
        );
        assertNull(OreExposurePlacement.placementGrade(new OreDepositGeometry.Sample(0.0, null, false), true));
    }

    @Test
    void placementBoundsConservativelyExtendEconomicBody() {
        OreDepositCandidatePlanner.Proposal proposal = new OreDepositCandidatePlanner.Proposal(
                "iron",
                "vein",
                0,
                0,
                0,
                32,
                -24,
                48
        );
        OreDepositGeometry.Body body = OreDepositGeometry.forProposal(8675309L, proposal);
        OreDepositGeometry.Bounds economic = body.bounds();
        OreDepositGeometry.Bounds placement = OreExposurePlacement.placementBounds(body);

        assertEquals(economic.minX() - 24, placement.minX());
        assertEquals(economic.minY() - 24, placement.minY());
        assertEquals(economic.minZ() - 24, placement.minZ());
        assertEquals(economic.maxX() + 24, placement.maxX());
        assertEquals(economic.maxY() + 24, placement.maxY());
        assertEquals(economic.maxZ() + 24, placement.maxZ());
        assertThrows(IllegalArgumentException.class, () -> OreExposurePlacement.placementBounds(null));
        assertThrows(IllegalArgumentException.class, () -> OreExposurePlacement.placementGrade(null, true));
    }
}
