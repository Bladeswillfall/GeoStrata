package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiamondGeologyPlannerTest {
    @Test
    void pipePlanningIsDeterministicAndCellLocal() {
        long seed = 0x1234ABCD5678EF90L;
        DiamondGeologyPlanner.PipeCandidate first = DiamondGeologyPlanner.pipe(
                seed,
                -3,
                5,
                DiamondGeologyPlanner.PipeKind.KIMBERLITE
        );
        DiamondGeologyPlanner.PipeCandidate repeat = DiamondGeologyPlanner.pipe(
                seed,
                -3,
                5,
                DiamondGeologyPlanner.PipeKind.KIMBERLITE
        );
        DiamondGeologyPlanner.PipeCandidate lamproite = DiamondGeologyPlanner.pipe(
                seed,
                -3,
                5,
                DiamondGeologyPlanner.PipeKind.LAMPROITE
        );

        assertEquals(first, repeat);
        assertNotEquals(first, lamproite);
        int minX = -3 * DiamondGeologyPlanner.PIPE_CELL_SIZE + DiamondGeologyPlanner.PIPE_CELL_SIZE / 4;
        int minZ = 5 * DiamondGeologyPlanner.PIPE_CELL_SIZE + DiamondGeologyPlanner.PIPE_CELL_SIZE / 4;
        int maxOffset = DiamondGeologyPlanner.PIPE_CELL_SIZE / 2;
        assertTrue(first.anchorX() >= minX && first.anchorX() < minX + maxOffset);
        assertTrue(first.anchorZ() >= minZ && first.anchorZ() < minZ + maxOffset);
        assertTrue(Math.abs(first.tiltX()) <= 0.035);
        assertTrue(Math.abs(first.tiltZ()) <= 0.035);
        assertTrue(first.baseRadius() >= 1.8 && first.baseRadius() <= 3.0);
    }

    @Test
    void structuralPlanningStaysSmallAndVertical() {
        DiamondGeologyPlanner.StructuralCandidate candidate = DiamondGeologyPlanner.structural(42L, 7, -4);
        assertEquals(candidate, DiamondGeologyPlanner.structural(42L, 7, -4));
        assertTrue(candidate.clusterCount() >= 2 && candidate.clusterCount() <= 3);
        assertTrue(Math.abs(candidate.tiltX()) <= 0.055);
        assertTrue(Math.abs(candidate.tiltZ()) <= 0.055);
    }
}
