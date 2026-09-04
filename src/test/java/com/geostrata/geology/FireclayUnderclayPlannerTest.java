package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FireclayUnderclayPlannerTest {
    @Test
    void underclayTracksCoalSeamAndStaysBelowIt() {
        OreDepositCandidatePlanner.Proposal parentProposal = new OreDepositCandidatePlanner.Proposal(
                "coal", "coal_seam", 2, 1, -3, 120, 40, -180
        );
        OreDepositGeometry.Body parentBody = new OreDepositGeometry.Body(
                42L,
                "coal",
                "coal_seam",
                120,
                40,
                -180,
                48.0,
                28.0,
                2.0,
                Math.toRadians(30.0),
                0.0,
                0.8,
                40.0,
                0.25,
                List.of()
        );

        OreDepositCandidatePlanner.Proposal underclay = FireclayUnderclayPlanner.proposal(parentProposal, parentBody);
        OreDepositGeometry.Body body = FireclayUnderclayPlanner.body(
                underclay,
                parentBody,
                OreGenerationProfile.defaults()
        );

        assertEquals("fireclay", underclay.material());
        assertEquals("stratiform", underclay.depositStyle());
        assertEquals(parentProposal.cellX(), underclay.cellX());
        assertEquals(parentProposal.cellY(), underclay.cellY());
        assertEquals(parentProposal.cellZ(), underclay.cellZ());
        assertTrue(underclay.anchorY() < parentBody.anchorY());
        assertEquals(parentBody.azimuthRadians(), body.azimuthRadians());
        assertEquals(parentBody.dipRadians(), body.dipRadians());
        assertEquals(parentBody.warpWavelength(), body.warpWavelength());
        assertTrue(body.lengthRadius() <= parentBody.lengthRadius());
        assertTrue(body.widthRadius() <= parentBody.widthRadius());
        assertTrue(body.thicknessRadius() < parentBody.thicknessRadius());
        assertTrue(body.branches().isEmpty());
    }

    @Test
    void coalAndLigniteAreTheOnlySupportedParents() {
        assertTrue(FireclayUnderclayPlanner.supportsParent("coal"));
        assertTrue(FireclayUnderclayPlanner.supportsParent("lignite"));

        OreDepositCandidatePlanner.Proposal iron = new OreDepositCandidatePlanner.Proposal(
                "iron", "stratiform", 0, 0, 0, 0, 0, 0
        );
        OreDepositGeometry.Body ironBody = new OreDepositGeometry.Body(
                1L, "iron", "stratiform", 0, 0, 0,
                10.0, 8.0, 2.0, 0.0, 0.0, 0.0, 16.0, 0.0, List.of()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FireclayUnderclayPlanner.proposal(iron, ironBody)
        );
    }
}
