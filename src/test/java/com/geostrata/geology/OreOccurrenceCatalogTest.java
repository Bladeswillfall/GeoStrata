package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreOccurrenceCatalogTest {
    @Test
    void parsesHostOccurrenceGenerationAndOrderedGradeContract() {
        OreOccurrenceCatalog.Snapshot snapshot = OreOccurrenceCatalog.parse(lithologies(), occurrence("shale", "vein"));

        OreOccurrenceCatalog.Occurrence iron = snapshot.require("iron");
        assertEquals(List.of("shale"), iron.hostLithologies());
        assertEquals(List.of(GeologyProvince.OROGENIC_BELT), iron.provinceContexts());
        assertEquals(List.of("baseline"), iron.formationRoutes().stream().map(OreOccurrenceCatalog.FormationRoute::id).toList());
        assertEquals(0.5, iron.generation().activationChance(), 0.0);
        assertEquals(160, iron.generation().candidateGrid().horizontalCellSize());
        assertEquals(1.2, iron.generation().biomeMultiplier("geostrata:has_mountain_rocks"::equals), 0.0);
        assertEquals(List.of("poor", "medium", "rich", "massive"), snapshot.gradeModel().economicGrades());
        assertEquals(4, snapshot.gradeModel().require(OreGrade.RICH).baseYield());
        assertEquals(8, snapshot.gradeModel().require(OreGrade.MASSIVE).experienceMax());
        assertEquals("geostrata:rich_iron_ore", iron.gradeBlocks().get(OreGrade.RICH));
    }

    @Test
    void naturalBlockOverridesAreSparseAndValidated() {
        JsonObject root = occurrence("shale", "vein");
        JsonObject occurrence = root.getAsJsonArray("occurrences").get(0).getAsJsonObject();
        occurrence.add("naturalBlockOverrides", JsonParser.parseString("""
                {"massive": "minecraft:coal_block"}
                """));

        OreOccurrenceCatalog.Occurrence iron = OreOccurrenceCatalog.parse(lithologies(), root).require("iron");
        assertEquals("geostrata:rich_iron_ore", iron.naturalBlock(OreGrade.RICH));
        assertEquals("minecraft:coal_block", iron.naturalBlock(OreGrade.MASSIVE));

        occurrence.add("naturalBlockOverrides", JsonParser.parseString("""
                {"unknown": "minecraft:coal_block"}
                """));
        assertThrows(IllegalArgumentException.class, () -> OreOccurrenceCatalog.parse(lithologies(), root));
    }

    @Test
    void providerNativeOccurrenceCanReplaceEveryGradeWithoutGradeBlocks() {
        JsonObject root = occurrence("shale", "vein");
        JsonObject occurrence = root.getAsJsonArray("occurrences").get(0).getAsJsonObject();
        occurrence.remove("gradeBlocks");
        occurrence.add("naturalBlockOverrides", JsonParser.parseString("""
                {
                  "poor": "minecraft:iron_ore",
                  "medium": "minecraft:iron_ore",
                  "rich": "minecraft:iron_ore",
                  "massive": "minecraft:iron_ore"
                }
                """));

        OreOccurrenceCatalog.Occurrence iron = OreOccurrenceCatalog.parse(lithologies(), root).require("iron");
        assertTrue(iron.gradeBlocks().isEmpty());
        assertEquals("minecraft:iron_ore", iron.naturalBlock(OreGrade.POOR));
        assertEquals("minecraft:iron_ore", iron.naturalBlock(OreGrade.MASSIVE));

        occurrence.add("naturalBlockOverrides", JsonParser.parseString("""
                {"massive": "minecraft:iron_ore"}
                """));
        assertThrows(IllegalArgumentException.class, () -> OreOccurrenceCatalog.parse(lithologies(), root));
    }

    @Test
    void bodyStyleConstraintIsOptionalAndFailsClosedWithoutContext() {
        JsonObject root = occurrence("shale", "vein");
        root.getAsJsonArray("occurrences")
                .get(0).getAsJsonObject()
                .getAsJsonArray("formationRoutes")
                .get(0).getAsJsonObject()
                .add("bodyStyles", JsonParser.parseString("[\"pegmatite_fertile_margin\"]"));

        OreOccurrenceCatalog.Occurrence iron = OreOccurrenceCatalog.parse(lithologies(), root).require("iron");
        assertEquals(List.of("pegmatite_fertile_margin"), iron.formationRoutes().get(0).bodyStyles());
        assertTrue(iron.requiresBodyStyleContext("vein", GeologyProvince.OROGENIC_BELT));
        assertEquals(List.of(), iron.hostLithologiesFor("vein", GeologyProvince.OROGENIC_BELT));
        assertEquals(
                List.of("shale"),
                iron.hostLithologiesFor("vein", GeologyProvince.OROGENIC_BELT, "pegmatite_fertile_margin")
        );
        assertFalse(iron.matchesFormationRoute("vein", GeologyProvince.OROGENIC_BELT, "shale"));
        assertTrue(iron.matchesFormationRoute(
                "vein",
                GeologyProvince.OROGENIC_BELT,
                "shale",
                "pegmatite_fertile_margin"
        ));
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
    void rejectsFormationRouteSummaryDrift() {
        JsonObject root = occurrence("shale", "vein");
        root.getAsJsonArray("occurrences")
                .get(0).getAsJsonObject()
                .getAsJsonArray("formationRoutes")
                .get(0).getAsJsonObject()
                .getAsJsonArray("depositStyles")
                .set(0, JsonParser.parseString("\"stratiform\""));

        assertThrows(IllegalArgumentException.class, () -> OreOccurrenceCatalog.parse(lithologies(), root));
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

    @Test
    void biomeAffinityIsBonusOnly() {
        JsonObject root = occurrence("shale", "vein");
        root.getAsJsonArray("occurrences")
                .get(0).getAsJsonObject()
                .getAsJsonObject("generation")
                .getAsJsonObject("biomeMultipliers")
                .addProperty("geostrata:has_mountain_rocks", 0.8);

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
                  "schemaVersion": 3,
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
                    "formationRoutes": [{
                      "id": "baseline",
                      "hostLithologies": ["%s"],
                      "provinceContexts": ["orogenic_belt"],
                      "depositStyles": ["%s"]
                    }],
                    "generation": {
                      "activationChance": 0.5,
                      "candidateGrid": {
                        "horizontalCellSize": 160,
                        "verticalCellSize": 64,
                        "horizontalMargin": 16,
                        "verticalMargin": 8,
                        "horizontalSearchPaddingBlocks": 224,
                        "verticalSearchPaddingBlocks": 224
                      },
                      "biomeMultipliers": {"geostrata:has_mountain_rocks": 1.2}
                    },
                    "gradeBlocks": {
                      "poor": "geostrata:poor_iron_ore",
                      "medium": "geostrata:medium_iron_ore",
                      "rich": "geostrata:rich_iron_ore",
                      "massive": "geostrata:massive_iron_ore"
                    }
                  }]
                }
                """.formatted(host, style, host, style)).getAsJsonObject();
    }
}
