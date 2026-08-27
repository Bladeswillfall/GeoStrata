package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CorrelatedExperimentActivationTest {
    @Test
    void validMarkerPromotesLoadedDisabledSnapshot() {
        CorrelatedSedimentaryExperiment.Snapshot base = snapshot("metadata_only", false);

        CorrelatedSedimentaryExperiment.Snapshot active = CorrelatedExperimentActivation.apply(base, marker());

        assertTrue(active.enabled());
        assertEquals("experimental_runtime", active.runtimeStatus());
        assertSame(base.targetSuccessionIds(), active.targetSuccessionIds());
        assertSame(base.allowedProvinces(), active.allowedProvinces());
        assertSame(base.supersededLithologies(), active.supersededLithologies());
        assertSame(base.verticalWindow(), active.verticalWindow());
    }

    @Test
    void rejectsMarkerForAnotherExperiment() {
        JsonObject wrong = marker();
        wrong.addProperty("experiment", "geostrata:other_experiment");

        assertThrows(
                IllegalArgumentException.class,
                () -> CorrelatedExperimentActivation.apply(snapshot("metadata_only", false), wrong)
        );
    }

    @Test
    void alreadyActiveSnapshotRemainsStable() {
        CorrelatedSedimentaryExperiment.Snapshot active = snapshot("experimental_runtime", true);
        assertSame(active, CorrelatedExperimentActivation.apply(active, marker()));
    }

    private static CorrelatedSedimentaryExperiment.Snapshot snapshot(String runtimeStatus, boolean enabled) {
        return new CorrelatedSedimentaryExperiment.Snapshot(
                runtimeStatus,
                enabled,
                Set.of("basin_mudrock_carbonate_cycle"),
                Set.of(GeologyProvince.SEDIMENTARY_BASIN),
                Set.of("limestone", "shale"),
                96,
                "geostrata:has_common_rocks",
                "geostrata:worldgen/base_stone_replaceables",
                new CorrelatedSedimentaryExperiment.VerticalWindow(-96, 48)
        );
    }

    private static JsonObject marker() {
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:correlated_sedimentary_activation",
                  "experiment": "geostrata:correlated_sedimentary_experiment"
                }
                """).getAsJsonObject();
    }
}
