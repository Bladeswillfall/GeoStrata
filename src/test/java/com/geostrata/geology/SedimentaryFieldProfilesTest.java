package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SedimentaryFieldProfilesTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void parsesCompleteContinuityProfiles() {
        SedimentaryFieldProfiles.Snapshot snapshot = SedimentaryFieldProfiles.parse(
                successions("metadata_only", "regional", "local", 1.0, 1.0),
                profiles("metadata_only", true, 48.0, 32.0)
        );

        assertEquals("metadata_only", snapshot.runtimeStatus());
        assertEquals(2, snapshot.parametersByContinuity().size());

        SedimentaryStratigraphicField.Parameters regional = snapshot.parametersFor("regional");
        assertEquals(48.0, regional.cycleThicknessBlocks(), EPSILON);
        assertEquals(0.08, regional.maxDip(), EPSILON);
        assertEquals(4.0, regional.warpAmplitudeBlocks(), EPSILON);
        assertEquals(192.0, regional.warpWavelengthBlocks(), EPSILON);

        SedimentaryStratigraphicField.Parameters local = snapshot.parametersFor("local");
        assertEquals(32.0, local.cycleThicknessBlocks(), EPSILON);
        assertEquals(0.16, local.maxDip(), EPSILON);
        assertEquals(5.0, local.warpAmplitudeBlocks(), EPSILON);
        assertEquals(96.0, local.warpWavelengthBlocks(), EPSILON);
    }

    @Test
    void rejectsMissingContinuityCoverage() {
        assertThrows(IllegalArgumentException.class, () -> SedimentaryFieldProfiles.parse(
                successions("metadata_only", "regional", "local", 1.0, 1.0),
                profiles("metadata_only", false, 48.0, 32.0)
        ));
    }

    @Test
    void rejectsRuntimeActivationAndUnknownSuccessionContinuity() {
        assertThrows(IllegalArgumentException.class, () -> SedimentaryFieldProfiles.parse(
                successions("metadata_only", "regional", "local", 1.0, 1.0),
                profiles("runtime_bias", true, 48.0, 32.0)
        ));
        assertThrows(IllegalArgumentException.class, () -> SedimentaryFieldProfiles.parse(
                successions("metadata_only", "continental", "local", 1.0, 1.0),
                profiles("metadata_only", true, 48.0, 32.0)
        ));
    }

    @Test
    void rejectsProfileThatCompressesABedBelowTwoVirtualBlocks() {
        assertThrows(IllegalArgumentException.class, () -> SedimentaryFieldProfiles.parse(
                successions("metadata_only", "regional", "local", 0.1, 4.0),
                profiles("metadata_only", true, 48.0, 32.0)
        ));
    }

    @Test
    void rejectsUnknownProfileLookup() {
        SedimentaryFieldProfiles.Snapshot snapshot = SedimentaryFieldProfiles.parse(
                successions("metadata_only", "regional", "local", 1.0, 1.0),
                profiles("metadata_only", true, 48.0, 32.0)
        );
        assertThrows(IllegalArgumentException.class, () -> snapshot.parametersFor("missing"));
    }

    private static JsonObject successions(
            String runtimeStatus,
            String firstContinuity,
            String secondContinuity,
            double thinBed,
            double thickBed
    ) {
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:sedimentary_successions",
                  "runtimeStatus": "%s",
                  "order": "lower_to_upper",
                  "successions": [
                    {
                      "id": "first_cycle",
                      "continuity": "%s",
                      "beds": [
                        {"relativeThickness": %s},
                        {"relativeThickness": %s},
                        {"relativeThickness": 1.0}
                      ]
                    },
                    {
                      "id": "second_cycle",
                      "continuity": "%s",
                      "beds": [
                        {"relativeThickness": 1.0},
                        {"relativeThickness": 1.0},
                        {"relativeThickness": 1.0}
                      ]
                    }
                  ]
                }
                """.formatted(runtimeStatus, firstContinuity, thinBed, thickBed, secondContinuity)).getAsJsonObject();
    }

    private static JsonObject profiles(
            String runtimeStatus,
            boolean includeLocal,
            double regionalCycle,
            double localCycle
    ) {
        String local = includeLocal
                ? """
                  ,{
                    "continuity": "local",
                    "cycleThicknessBlocks": %s,
                    "maxDip": 0.16,
                    "warpAmplitudeBlocks": 5.0,
                    "warpWavelengthBlocks": 96.0
                  }
                  """.formatted(localCycle)
                : "";
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:sedimentary_field_profiles",
                  "runtimeStatus": "%s",
                  "profiles": [
                    {
                      "continuity": "regional",
                      "cycleThicknessBlocks": %s,
                      "maxDip": 0.08,
                      "warpAmplitudeBlocks": 4.0,
                      "warpWavelengthBlocks": 192.0
                    }
                    %s
                  ]
                }
                """.formatted(runtimeStatus, regionalCycle, local)).getAsJsonObject();
    }
}
