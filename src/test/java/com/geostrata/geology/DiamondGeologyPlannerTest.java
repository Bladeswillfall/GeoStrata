package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertTrue(Math.abs(first.tiltX()) <= DiamondGeologyPlanner.PIPE_MAX_ABS_TILT_PER_VERTICAL_BLOCK);
        assertTrue(Math.abs(first.tiltZ()) <= DiamondGeologyPlanner.PIPE_MAX_ABS_TILT_PER_VERTICAL_BLOCK);
        assertTrue(first.baseRadius() >= 1.8 && first.baseRadius() <= 3.0);
    }

    @Test
    void pipeSearchPaddingGrowsWithDimensionHeight() {
        assertEquals(34, DiamondGeologyPlanner.pipeSearchPaddingBlocks(384));
        assertEquals(65, DiamondGeologyPlanner.pipeSearchPaddingBlocks(1024));
        assertTrue(
                DiamondGeologyPlanner.pipeSearchPaddingBlocks(1024)
                        > DiamondGeologyPlanner.pipeSearchPaddingBlocks(384)
        );
        assertThrows(IllegalArgumentException.class, () -> DiamondGeologyPlanner.pipeSearchPaddingBlocks(0));
    }

    @Test
    void structuralPlanningUsesShortDenseFaultSegments() {
        DiamondGeologyPlanner.StructuralCandidate candidate = DiamondGeologyPlanner.structural(42L, 7, -4);
        assertEquals(32, DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE);
        assertEquals(candidate, DiamondGeologyPlanner.structural(42L, 7, -4));
        assertTrue(candidate.clusterCount() >= 12 && candidate.clusterCount() <= 16);

        int minX = 7 * DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE + DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE / 5;
        int minZ = -4 * DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE + DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE / 5;
        int span = DiamondGeologyPlanner.STRUCTURAL_CELL_SIZE * 3 / 5;
        assertTrue(candidate.anchorX() >= minX && candidate.anchorX() < minX + span);
        assertTrue(candidate.anchorZ() >= minZ && candidate.anchorZ() < minZ + span);
    }
}
