package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SedimentarySuccessionsTest {
    @Test
    void parsesOrderedSedimentaryMotif() {
        SedimentarySuccessions.Snapshot snapshot = SedimentarySuccessions.parse(catalog(), successions(false));

        assertEquals(1, snapshot.successions().size());
        SedimentarySuccessions.Succession succession = snapshot.successions().get(0);
        assertEquals("test_cycle", succession.id());
        assertEquals(3, succession.beds().size());
        assertEquals("alpha", succession.beds().get(0).lithology());
        assertEquals("beta", succession.beds().get(1).lithology());
        assertEquals("alpha", succession.beds().get(2).lithology());
    }

    @Test
    void rejectsNonSedimentaryBed() {
        assertThrows(IllegalArgumentException.class,
                () -> SedimentarySuccessions.parse(catalog(), successions(true)));
    }

    private static JsonObject catalog() {
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:lithology_catalog",
                  "lithologies": [
                    {"id":"alpha","rockClass":"sedimentary"},
                    {"id":"beta","rockClass":"sedimentary"},
                    {"id":"basalt","rockClass":"igneous"}
                  ]
                }
                """).getAsJsonObject();
    }

    private static JsonObject successions(boolean includeBasalt) {
        String middle = includeBasalt ? "basalt" : "beta";
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:sedimentary_successions",
                  "runtimeStatus": "metadata_only",
                  "order": "lower_to_upper",
                  "successions": [
                    {
                      "id": "test_cycle",
                      "contexts": ["sedimentary_basin"],
                      "continuity": "regional",
                      "beds": [
                        {"lithology":"alpha","relativeThickness":1.0},
                        {"lithology":"%s","relativeThickness":0.8},
                        {"lithology":"alpha","relativeThickness":0.6}
                      ]
                    }
                  ]
                }
                """.formatted(middle)).getAsJsonObject();
    }
}
