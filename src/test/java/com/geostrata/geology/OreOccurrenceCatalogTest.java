package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OreOccurrenceCatalogTest {
    @Test
    void parsesHostOccurrenceAndOrderedGradeContract() {
        OreOccurrenceCatalog.Snapshot snapshot = OreOccurrenceCatalog.parse(lithologies(), occurrence("shale", "vein"));

        OreOccurrenceCatalog.Occurrence iron = snapshot.require("iron");
        assertEquals(List.of("shale"), iron.hostLithologies());
        assertEquals(List.of(GeologyProvince.OROGENIC_BELT), iron.provinceContexts());
        assertEquals(List.of("poor", "medium", "rich", "massive"), snapshot.gradeModel().economicGrades());
    }

    @Test
    void rejectsUnknownHostsAndUnsupportedDepositStyles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OreOccurrenceCatalog.parse(lithologies(), occurrence("granite", "vein"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> OreOccurrenceCatalog.parse(lithologies(), occurrence("shale", "blob"))
        );
    }

    private static LithologyCatalog.Snapshot lithologies() {
        LithologyCatalog.Entry shale = new LithologyCatalog.Entry(
                "shale",
                "geostrata:shale",
                "sedimentary",
                "mudrock",
                "bedded",
                "shallow_to_mid",
                "regional",
                "geostrata:has_common_rocks",
                "shale_ore"
        );
        return new LithologyCatalog.Snapshot("metadata_only", List.of(shale), Map.of("shale", shale));
    }

    private static JsonObject occurrence(String host, String style) {
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:ore_occurrence_catalog",
                  "runtimeStatus": "metadata_only",
                  "generationOwner": "geostrata",
                  "nativeGenerationSuppression": "not_implemented",
                  "gradeModel": {
                    "runtimeStatus": "names_only",
                    "economicGrades": ["poor", "medium", "rich", "massive"],
                    "trace": {"economic": false},
                    "yieldStatus": "not_implemented",
                    "experienceStatus": "not_implemented"
                  },
                  "occurrences": [{
                    "id": "iron",
                    "providerMod": "minecraft",
                    "outputItem": "minecraft:raw_iron",
                    "hostLithologies": ["%s"],
                    "provinceContexts": ["orogenic_belt"],
                    "depositStyles": ["%s"]
                  }]
                }
                """.formatted(host, style)).getAsJsonObject();
    }
}
