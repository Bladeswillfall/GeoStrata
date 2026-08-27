package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SedimentaryFieldProfilesTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void parsesCompleteContinuityProfiles() {
        SedimentaryFieldProfiles.Snapshot snapshot = SedimentaryFieldProfiles.parse(
                successions("regional", "local", 1.0, 1.0),
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
                successions("regional", "local", 1.0, 1.0),
                profiles("metadata_only", false, 48.0, 32.0)
        ));
    }

    @Test
    void rejectsRuntimeActivationAndUnknownSuccessionContinuity() {
        assertThrows(IllegalArgumentException.class, () -> SedimentaryFieldProfiles.parse(
                successions("regional", "local", 1.0, 1.0),
                profiles("runtime_bias", true, 48.0, 32.0)
        ));
        assertThrows(IllegalArgumentException.class, () -> SedimentaryFieldProfiles.parse(
                successions("continental", "local", 1.0, 1.0),
                profiles("metadata_only", true, 48.0, 32.0)
        ));
    }

    @Test
    void rejectsProfileThatCompressesABedBelowTwoVirtualBlocks() {
        assertThrows(IllegalArgumentException.class, () -> SedimentaryFieldProfiles.parse(
                successions("regional", "local", 0.1, 4.0),
                profiles("metadata_only", true, 48.0, 32.0)
        ));
    }

    @Test
    void rejectsUnknownProfileLookup() {
        SedimentaryFieldProfiles.Snapshot snapshot = SedimentaryFieldProfiles.parse(
                successions("regional", "local", 1.0, 1.0),
                profiles("metadata_only", true, 48.0, 32.0)
        );
        assertThrows(IllegalArgumentException.class, () -> snapshot.parametersFor("missing"));
    }

    private static SedimentarySuccessions.Snapshot successions(
            String firstContinuity,
            String secondContinuity,
            double thinBed,
            double thickBed
    ) {
        SedimentarySuccessions.Succession first = new SedimentarySuccessions.Succession(
                "first_cycle",
                List.of(GeologyProvince.SEDIMENTARY_BASIN),
                firstContinuity,
                List.of(bed(thinBed), bed(thickBed), bed(1.0))
        );
        SedimentarySuccessions.Succession second = new SedimentarySuccessions.Succession(
                "second_cycle",
                List.of(GeologyProvince.SEDIMENTARY_BASIN),
                secondContinuity,
                List.of(bed(1.0), bed(1.0), bed(1.0))
        );
        return new SedimentarySuccessions.Snapshot(
                "metadata_only",
                List.of(first, second),
                Map.of(first.id(), first, second.id(), second)
        );
    }

    private static SedimentarySuccessions.Bed bed(double relativeThickness) {
        return new SedimentarySuccessions.Bed("test", relativeThickness);
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
