package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CorrelatedExperimentChunkOwnershipTest {
    @Test
    void mapsEveryCoordinateInAChunkToTheSameCenter() {
        assertEquals(8, CorrelatedExperimentChunkOwnership.centerCoordinate(0));
        assertEquals(8, CorrelatedExperimentChunkOwnership.centerCoordinate(15));
        assertEquals(24, CorrelatedExperimentChunkOwnership.centerCoordinate(16));
        assertEquals(24, CorrelatedExperimentChunkOwnership.centerCoordinate(31));
    }

    @Test
    void handlesNegativeChunksWithFloorDivision() {
        assertEquals(-8, CorrelatedExperimentChunkOwnership.centerCoordinate(-1));
        assertEquals(-8, CorrelatedExperimentChunkOwnership.centerCoordinate(-16));
        assertEquals(-24, CorrelatedExperimentChunkOwnership.centerCoordinate(-17));
        assertEquals(-24, CorrelatedExperimentChunkOwnership.centerCoordinate(-32));
    }
}
