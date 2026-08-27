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
        assertEquals(4, snapshot.gradeModel().require(OreGrade.RICH).baseYield());
        assertEquals(8, snapshot.gradeModel().require(OreGrade.MASSIVE).experienceMax());
        assertEquals("geostrata:rich_iron_ore", iron.gradeBlocks().get(OreGrade.RICH));
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

    @Test
    void rejectsEconomicsThatDoNotIncreaseWithGrade() {
        JsonObject root = occurrence("shale", "vein");
        root.getAsJsonObject("gradeModel")
                .getAsJsonObject("economics")
                .getAsJsonObject("rich")
                .addProperty("baseYield", 2);

        assertThrows(IllegalArgumentException.class, () -> OreOccurrenceCatalog.parse(lithologies(), root));
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
                  "schemaVersion": 2,
                  "model": "geostrata:ore_occurrence_catalog",
                  "runtimeStatus": "grade_economy_active",
                  "generationOwner": "geostrata",
                  "nativeGenerationSuppression": "not_implemented",
                  "gradeModel": {
                    "runtimeStatus": "block_loot_xp_active",
                    "economicGrades": ["poor", "medium", "rich", "massive"],
                    "trace": {"economic": false, "runtimeStatus": "evidence_only_not_implemented"},
                    "yieldStatus": "loot_tables_active",
                    "experienceStatus": "block_runtime_active",
                    "fortuneMode": "minecraft:ore_drops",
                    "silkTouchMode": "drops_self",
                    "economics": {
                      "poor": {"baseYield": 1, "experience": {"min": 0, "max": 1}},
                      "medium": {"baseYield": 2, "experience": {"min": 1, "max": 2}},
                      "rich": {"baseYield": 4, "experience": {"min": 2, "max": 4}},
                      "massive": {"baseYield": 8, "experience": {"min": 4, "max": 8}}
                    }
                  },
                  "occurrences": [{
                    "id": "iron",
                    "providerMod": "minecraft",
                    "outputItem": "minecraft:raw_iron",
                    "hostLithologies": ["%s"],
                    "provinceContexts": ["orogenic_belt"],
                    "depositStyles": ["%s"],
                    "gradeBlocks": {
                      "poor": "geostrata:poor_iron_ore",
                      "medium": "geostrata:medium_iron_ore",
                      "rich": "geostrata:rich_iron_ore",
                      "massive": "geostrata:massive_iron_ore"
                    }
                  }]
                }
                """.formatted(host, style)).getAsJsonObject();
    }
}
