package com.geostrata.geology;

/**
 * Shared chunk-normalized ownership adapter for the correlated sedimentary experiment.
 * Both the correlated replacement pass and the superseded baseline features use
 * this class so random placed-feature origins cannot disagree with chunk ownership.
 */
public final class CorrelatedExperimentChunkOwnership {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_CENTER_OFFSET = CHUNK_SIZE / 2;

    private CorrelatedExperimentChunkOwnership() {
    }

    public static int centerCoordinate(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CHUNK_SIZE) * CHUNK_SIZE + CHUNK_CENTER_OFFSET;
    }

    public static CorrelatedSedimentaryExperiment.Ownership ownershipForChunk(
            long worldSeed,
            int blockX,
            int blockZ
    ) {
        return CorrelatedSedimentaryExperiment.ownershipAt(
                worldSeed,
                centerCoordinate(blockX),
                centerCoordinate(blockZ)
        );
    }

    public static boolean suppressionActiveFor(String lithology) {
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        return experiment.loaded()
                && experiment.enabled()
                && experiment.supersededLithologies().contains(lithology);
    }

    public static boolean suppressesBaselineLithology(
            String lithology,
            long worldSeed,
            int blockX,
            int blockZ
    ) {
        return suppressionActiveFor(lithology)
                && ownershipForChunk(worldSeed, blockX, blockZ).owned();
    }
}
