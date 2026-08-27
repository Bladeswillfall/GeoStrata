package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CorrelatedSedimentaryExperimentTest {
    @Test
    void parsesDisabledCoreContract() {
        CorrelatedSedimentaryExperiment.Snapshot snapshot = CorrelatedSedimentaryExperiment.parse(
                experiment(false, "metadata_only", 96, "alpha", "beta"),
                successions(),
                catalog(),
                profiles()
        );

        assertFalse(snapshot.enabled());
        assertEquals("metadata_only", snapshot.runtimeStatus());
        assertEquals(2, snapshot.supersededLithologies().size());
        assertEquals(-96, snapshot.verticalWindow().minOffsetBlocks());
        assertEquals(48, snapshot.verticalWindow().maxOffsetBlocks());
    }

    @Test
    void activeContractRequiresExplicitExperimentalRuntimeStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CorrelatedSedimentaryExperiment.parse(
                        experiment(true, "metadata_only", 96, "alpha", "beta"),
                        successions(),
                        catalog(),
                        profiles()
                )
        );

        CorrelatedSedimentaryExperiment.Snapshot active = CorrelatedSedimentaryExperiment.parse(
                experiment(true, "experimental_runtime", 96, "alpha", "beta"),
                successions(),
                catalog(),
                profiles()
        );
        assertTrue(active.enabled());
    }

    @Test
    void rejectsPartialBaselineSuppression() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CorrelatedSedimentaryExperiment.parse(
                        experiment(false, "metadata_only", 96, "alpha"),
                        successions(),
                        catalog(),
                        profiles()
                )
        );
    }

    @Test
    void evaluatesOwnedSedimentaryBasinInteriorDeterministically() {
        CorrelatedSedimentaryExperiment.Snapshot active = CorrelatedSedimentaryExperiment.parse(
                experiment(true, "experimental_runtime", 96, "alpha", "beta"),
                successions(),
                catalog(),
                profiles()
        );
        GeologyProvinceProfiles.Snapshot profileSnapshot = GeologyProvinceProfiles.parse(catalog(), profiles());
        SedimentarySuccessions.Snapshot successionSnapshot = SedimentarySuccessions.parse(catalog(), successions());

        CorrelatedSedimentaryExperiment.Ownership ownership = CorrelatedSedimentaryExperiment.evaluate(
                0L,
                -2000,
                -1104,
                active,
                profileSnapshot,
                successionSnapshot
        );

        assertTrue(ownership.owned());
        assertEquals("owned", ownership.reason());
        assertEquals(GeologyProvince.SEDIMENTARY_BASIN, ownership.province());
        assertEquals("test_cycle", ownership.successionId());
    }

    @Test
    void boundaryExclusionWinsBeforeMutationOwnership() {
        CorrelatedSedimentaryExperiment.Snapshot active = CorrelatedSedimentaryExperiment.parse(
                experiment(true, "experimental_runtime", 180, "alpha", "beta"),
                successions(),
                catalog(),
                profiles()
        );
        GeologyProvinceProfiles.Snapshot profileSnapshot = GeologyProvinceProfiles.parse(catalog(), profiles());
        SedimentarySuccessions.Snapshot successionSnapshot = SedimentarySuccessions.parse(catalog(), successions());

        CorrelatedSedimentaryExperiment.Ownership ownership = CorrelatedSedimentaryExperiment.evaluate(
                0L,
                -2000,
                -1104,
                active,
                profileSnapshot,
                successionSnapshot
        );

        assertFalse(ownership.owned());
        assertEquals("province_boundary_exclusion", ownership.reason());
    }

    @Test
    void disabledContractNeverOwnsWorldgen() {
        CorrelatedSedimentaryExperiment.Snapshot disabled = CorrelatedSedimentaryExperiment.parse(
                experiment(false, "metadata_only", 96, "alpha", "beta"),
                successions(),
                catalog(),
                profiles()
        );
        GeologyProvinceProfiles.Snapshot profileSnapshot = GeologyProvinceProfiles.parse(catalog(), profiles());
        SedimentarySuccessions.Snapshot successionSnapshot = SedimentarySuccessions.parse(catalog(), successions());

        CorrelatedSedimentaryExperiment.Ownership ownership = CorrelatedSedimentaryExperiment.evaluate(
                0L,
                -2000,
                -1104,
                disabled,
                profileSnapshot,
                successionSnapshot
        );

        assertFalse(ownership.owned());
        assertEquals("disabled", ownership.reason());
    }

    private static JsonObject experiment(
            boolean enabled,
            String runtimeStatus,
            int boundaryDistance,
            String... superseded
    ) {
        StringBuilder lithologies = new StringBuilder();
        for (int i = 0; i < superseded.length; i++) {
            if (i > 0) {
                lithologies.append(',');
            }
            lithologies.append('"').append(superseded[i]).append('"');
        }
        return parse("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:correlated_sedimentary_experiment",
                  "runtimeStatus": "%s",
                  "enabled": %s,
                  "targetSuccessionIds": ["test_cycle"],
                  "allowedProvinces": ["sedimentary_basin"],
                  "supersededLithologies": [%s],
                  "minimumBoundaryDistanceBlocks": %d,
                  "registrationBiomeTag": "geostrata:has_common_rocks",
                  "hostBlockTag": "geostrata:worldgen/base_stone_replaceables",
                  "verticalWindow": {
                    "anchor": "sea_level",
                    "minOffsetBlocks": -96,
                    "maxOffsetBlocks": 48
                  }
                }
                """.formatted(runtimeStatus, enabled, lithologies, boundaryDistance));
    }

    private static JsonObject catalog() {
        return parse("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:lithology_catalog",
                  "runtimeStatus": "metadata_only",
                  "lithologies": [
                    {
                      "id":"alpha","block":"geostrata:alpha","rockClass":"sedimentary",
                      "genesis":"mudrock","bodyStyle":"bedded","depthAffinity":"shallow",
                      "continuity":"regional","biomeTag":"geostrata:has_common_rocks","baselineFeature":"alpha_ore"
                    },
                    {
                      "id":"beta","block":"geostrata:beta","rockClass":"sedimentary",
                      "genesis":"carbonate","bodyStyle":"bedded","depthAffinity":"shallow",
                      "continuity":"regional","biomeTag":"geostrata:has_common_rocks","baselineFeature":"beta_ore"
                    }
                  ]
                }
                """);
    }

    private static JsonObject successions() {
        return parse("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:sedimentary_successions",
                  "runtimeStatus": "metadata_only",
                  "order": "lower_to_upper",
                  "successions": [
                    {
                      "id":"test_cycle",
                      "contexts":["sedimentary_basin"],
                      "continuity":"regional",
                      "beds":[
                        {"lithology":"alpha","relativeThickness":1.0},
                        {"lithology":"beta","relativeThickness":1.0},
                        {"lithology":"alpha","relativeThickness":0.5}
                      ]
                    }
                  ]
                }
                """);
    }

    private static JsonObject profiles() {
        String weights = "\"alpha\":1.0,\"beta\":1.0";
        return parse("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:province_profiles",
                  "runtimeStatus": "runtime_bias",
                  "blendWidthBlocks": 192,
                  "profiles": [
                    {"province":"sedimentary_basin","lithologyWeights":{%s}},
                    {"province":"cratonic_shield","lithologyWeights":{%s}},
                    {"province":"orogenic_belt","lithologyWeights":{%s}},
                    {"province":"volcanic_arc","lithologyWeights":{%s}},
                    {"province":"rift_province","lithologyWeights":{%s}}
                  ]
                }
                """.formatted(weights, weights, weights, weights, weights));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
