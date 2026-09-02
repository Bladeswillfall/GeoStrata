package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FaultControlledOrePlannerTest {
    private static final double CYCLE_THICKNESS = 48.0;

    @Test
    void nonVeinGeometryRemainsUntouched() {
        OreDepositCandidatePlanner.Proposal proposal = new OreDepositCandidatePlanner.Proposal(
                "iron",
                "stratiform",
                0,
                0,
                0,
                64,
                24,
                64
        );
        FaultControlledOrePlanner.Binding binding = FaultControlledOrePlanner.bind(
                123456789L,
                proposal,
                CYCLE_THICKNESS
        );

        assertFalse(binding.faultAligned());
        assertEquals(proposal, binding.proposal());
        assertEquals(proposal, binding.bodyProposal());
        assertEquals(
                OreDepositGeometry.forProposal(123456789L, proposal),
                binding.body(123456789L)
        );
    }

    @Test
    void capturedVeinKeepsCandidateIdentityButMovesBodyToTheSharedFault() {
        BoundCase found = boundCase();
        FaultControlledOrePlanner.Binding binding = found.binding();
        OreDepositGeometry.Body body = binding.body(found.seed());
        GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(
                found.seed(),
                body.anchorX(),
                body.anchorZ()
        );
        TectonicStructuralField.Context tectonics = TectonicStructuralField.forSite(
                found.seed(),
                province.province(),
                province.siteX(),
                province.siteZ(),
                CYCLE_THICKNESS
        );

        assertTrue(binding.faultAligned());
        assertEquals(found.original(), binding.proposal());
        assertNotEquals(found.original(), binding.bodyProposal());
        assertEquals(binding.bodyProposal().anchorX(), body.anchorX());
        assertEquals(binding.bodyProposal().anchorY(), body.anchorY());
        assertEquals(binding.bodyProposal().anchorZ(), body.anchorZ());
        assertEquals(0.0, body.dipRadians(), 0.0);
        assertTrue(tectonics.nearestFault(body.anchorX(), body.anchorY(), body.anchorZ()).distanceToFault() <= 1.5);

        double sampleDistance = Math.min(24.0, body.lengthRadius() * 0.6);
        for (double direction : new double[]{-1.0, 1.0}) {
            int x = (int) Math.round(body.anchorX()
                    + direction * sampleDistance * Math.cos(body.azimuthRadians()));
            int z = (int) Math.round(body.anchorZ()
                    + direction * sampleDistance * Math.sin(body.azimuthRadians()));
            assertTrue(
                    tectonics.nearestFault(x, body.anchorY(), z).distanceToFault() <= 2.5,
                    "fault-controlled vein axis should follow the local meandering trace"
            );
        }
    }

    @Test
    void bindingIsDeterministic() {
        BoundCase found = boundCase();
        assertEquals(
                found.binding(),
                FaultControlledOrePlanner.bind(found.seed(), found.original(), CYCLE_THICKNESS)
        );
    }

    @Test
    void contextBackedBindingMatchesDirectSampling() {
        BoundCase found = boundCase();
        OreDepositCandidatePlanner.Proposal proposal = found.original();
        GeologyProvinceSampler.Context context = GeologyProvinceSampler.context(
                found.seed(),
                proposal.anchorX() - 128,
                proposal.anchorZ() - 128,
                proposal.anchorX() + 128,
                proposal.anchorZ() + 128
        );

        assertEquals(
                found.binding(),
                FaultControlledOrePlanner.bind(found.seed(), proposal, CYCLE_THICKNESS, context)
        );
    }

    private static BoundCase boundCase() {
        long seed = 246813579L;
        int y = 24;
        for (int x = -2048; x <= 2048; x += 64) {
            for (int z = -2048; z <= 2048; z += 64) {
                GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(seed, x, z);
                if (province.distanceToBoundary() < 160.0) {
                    continue;
                }
                TectonicStructuralField.Context tectonics = TectonicStructuralField.forSite(
                        seed,
                        province.province(),
                        province.siteX(),
                        province.siteZ(),
                        CYCLE_THICKNESS
                );
                if (tectonics.nearestFault(x, y, z).distanceToFault()
                        > FaultControlledOrePlanner.CAPTURE_DISTANCE_BLOCKS * 0.75) {
                    continue;
                }
                OreDepositCandidatePlanner.Proposal proposal = new OreDepositCandidatePlanner.Proposal(
                        "gold",
                        "vein",
                        Math.floorDiv(x, OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE),
                        Math.floorDiv(y, OreDepositCandidatePlanner.VERTICAL_CELL_SIZE),
                        Math.floorDiv(z, OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE),
                        x,
                        y,
                        z
                );
                FaultControlledOrePlanner.Binding binding = FaultControlledOrePlanner.bind(
                        seed,
                        proposal,
                        CYCLE_THICKNESS
                );
                if (binding.faultAligned()) {
                    return new BoundCase(seed, proposal, binding);
                }
            }
        }
        throw new AssertionError("expected to find an interior fault-controlled vein test case");
    }

    private record BoundCase(
            long seed,
            OreDepositCandidatePlanner.Proposal original,
            FaultControlledOrePlanner.Binding binding
    ) {
    }
}
