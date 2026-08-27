package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CorrelatedSedimentaryRuntimeTest {
    @Test
    void resolvesOwnedChunkThroughTheSameSiteModelUsedByWorldgen() {
        Optional<CorrelatedSedimentaryRuntime.Site> resolved = CorrelatedSedimentaryRuntime.resolve(
                0L,
                -2000,
                -1104,
                experiment(),
                profiles(),
                successions(),
                fieldProfiles()
        );

        assertTrue(resolved.isPresent());
        CorrelatedSedimentaryRuntime.Site site = resolved.orElseThrow();
        assertEquals(-1992, site.chunkCenterX());
        assertEquals(-1096, site.chunkCenterZ());
        assertEquals("test_cycle", site.succession().id());
        assertTrue(site.ownership().owned());
    }

    @Test
    void summarizedColumnIsContiguousAndCoalescesEqualNeighbors() {
        CorrelatedSedimentaryRuntime.Site site = CorrelatedSedimentaryRuntime.resolve(
                0L,
                -2000,
                -1104,
                experiment(),
                profiles(),
                successions(),
                fieldProfiles()
        ).orElseThrow();

        List<CorrelatedExperimentDiagnostics.Layer> layers = CorrelatedExperimentDiagnostics.summarize(
                site,
                -2000,
                -1104,
                -32,
                64
        );

        assertFalse(layers.isEmpty());
        assertEquals(-32, layers.get(0).minY());
        assertEquals(64, layers.get(layers.size() - 1).maxY());

        int covered = 0;
        for (int index = 0; index < layers.size(); index++) {
            CorrelatedExperimentDiagnostics.Layer layer = layers.get(index);
            covered += layer.maxY() - layer.minY() + 1;
            if (index > 0) {
                CorrelatedExperimentDiagnostics.Layer previous = layers.get(index - 1);
                assertEquals(previous.maxY() + 1, layer.minY());
                assertNotEquals(previous.lithology(), layer.lithology());
            }
        }
        assertEquals(97, covered);
    }

    private static CorrelatedSedimentaryExperiment.Snapshot experiment() {
        return new CorrelatedSedimentaryExperiment.Snapshot(
                "experimental_runtime",
                true,
                Set.of("test_cycle"),
                Set.of(GeologyProvince.SEDIMENTARY_BASIN),
                Set.of("alpha", "beta"),
                96,
                "geostrata:has_common_rocks",
                "geostrata:worldgen/base_stone_replaceables",
                new CorrelatedSedimentaryExperiment.VerticalWindow(-96, 48)
        );
    }

    private static GeologyProvinceProfiles.Snapshot profiles() {
        EnumMap<GeologyProvince, Map<String, Double>> weights = new EnumMap<>(GeologyProvince.class);
        for (GeologyProvince province : GeologyProvince.values()) {
            weights.put(province, Map.of("alpha", 1.0, "beta", 1.0));
        }
        return new GeologyProvinceProfiles.Snapshot(
                "runtime_bias",
                192,
                Set.of("alpha", "beta"),
                weights
        );
    }

    private static SedimentarySuccessions.Snapshot successions() {
        SedimentarySuccessions.Succession succession = new SedimentarySuccessions.Succession(
                "test_cycle",
                List.of(GeologyProvince.SEDIMENTARY_BASIN),
                "regional",
                List.of(
                        new SedimentarySuccessions.Bed("alpha", 1.0),
                        new SedimentarySuccessions.Bed("beta", 1.0),
                        new SedimentarySuccessions.Bed("alpha", 0.5)
                )
        );
        return new SedimentarySuccessions.Snapshot(
                "metadata_only",
                List.of(succession),
                Map.of(succession.id(), succession)
        );
    }

    private static SedimentaryFieldProfiles.Snapshot fieldProfiles() {
        return new SedimentaryFieldProfiles.Snapshot(
                "metadata_only",
                Map.of(
                        "regional",
                        new SedimentaryStratigraphicField.Parameters(48.0, 0.08, 4.0, 256.0)
                )
        );
    }
}
