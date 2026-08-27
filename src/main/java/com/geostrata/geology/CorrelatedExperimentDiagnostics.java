package com.geostrata.geology;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only inspection of the exact correlated field that runtime worldgen would consume. */
public final class CorrelatedExperimentDiagnostics {
    private CorrelatedExperimentDiagnostics() {
    }

    public static Report inspect(
            long worldSeed,
            int x,
            double y,
            int z,
            int seaLevel,
            int bottomY,
            int topYExclusive
    ) {
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        CorrelatedSedimentaryExperiment.Ownership ownership =
                CorrelatedExperimentChunkOwnership.ownershipForChunk(worldSeed, x, z);
        Optional<CorrelatedSedimentaryRuntime.Site> resolved = CorrelatedSedimentaryRuntime.resolve(worldSeed, x, z);
        if (resolved.isEmpty()) {
            return Report.unresolved(experiment.enabled(), ownership);
        }

        CorrelatedSedimentaryRuntime.Site site = resolved.get();
        SedimentaryStratigraphicField.Sample sample = site.sample(x, y, z);
        int minY = Math.max(bottomY, seaLevel + experiment.verticalWindow().minOffsetBlocks());
        int maxY = Math.min(topYExclusive - 1, seaLevel + experiment.verticalWindow().maxOffsetBlocks());
        List<Layer> layers = minY <= maxY ? summarize(site, x, z, minY, maxY) : List.of();

        return new Report(
                true,
                ownership,
                site.chunkCenterX(),
                site.chunkCenterZ(),
                site.succession().id(),
                sample.bed().lithology(),
                sample.cycleIndex(),
                sample.fraction(),
                sample.verticalOffset(),
                minY,
                maxY,
                layers
        );
    }

    static List<Layer> summarize(
            CorrelatedSedimentaryRuntime.Site site,
            int x,
            int z,
            int minY,
            int maxY
    ) {
        if (minY > maxY) {
            return List.of();
        }

        List<Layer> layers = new ArrayList<>();
        String current = site.sample(x, minY, z).bed().lithology();
        int startY = minY;
        for (int y = minY + 1; y <= maxY; y++) {
            String lithology = site.sample(x, y, z).bed().lithology();
            if (!lithology.equals(current)) {
                layers.add(new Layer(startY, y - 1, current));
                startY = y;
                current = lithology;
            }
        }
        layers.add(new Layer(startY, maxY, current));
        return List.copyOf(layers);
    }

    public record Layer(int minY, int maxY, String lithology) {
    }

    public record Report(
            boolean resolved,
            CorrelatedSedimentaryExperiment.Ownership ownership,
            int chunkCenterX,
            int chunkCenterZ,
            String successionId,
            String lithology,
            long cycleIndex,
            double fraction,
            double verticalOffset,
            int minY,
            int maxY,
            List<Layer> layers
    ) {
        private static Report unresolved(
                boolean enabled,
                CorrelatedSedimentaryExperiment.Ownership ownership
        ) {
            String reason = enabled ? ownership.reason() : "disabled";
            CorrelatedSedimentaryExperiment.Ownership normalized = ownership.reason().equals(reason)
                    ? ownership
                    : new CorrelatedSedimentaryExperiment.Ownership(
                            false,
                            reason,
                            ownership.province(),
                            ownership.boundaryDistanceBlocks(),
                            ownership.successionId()
                    );
            return new Report(false, normalized, 0, 0, null, null, 0L, 0.0, 0.0, 0, -1, List.of());
        }
    }
}
