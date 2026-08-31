package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LithologyCatalogTest {
    @Test
    void parsesSemanticLithologyEntry() {
        LithologyCatalog.Snapshot snapshot = LithologyCatalog.parse(catalog(
                "metadata_only",
                "sedimentary",
                "geostrata:limestone",
                "limestone_ore"
        ));

        assertEquals(1, snapshot.entries().size());
        LithologyCatalog.Entry entry = snapshot.require("limestone");
        assertEquals("geostrata:limestone", entry.block());
        assertEquals("sedimentary", entry.rockClass());
        assertEquals("carbonate", entry.genesis());
        assertEquals("bedded", entry.bodyStyle());
        assertEquals("regional", entry.continuity());
        assertEquals("geostrata:has_common_rocks", entry.biomeTag());
        assertEquals("limestone_ore", entry.baselineFeature());
        assertTrue(entry.geoStrataOwned());
    }

    @Test
    void parsesProviderOwnedBlockWithoutGeoStrataFallback() {
        LithologyCatalog.Snapshot snapshot = LithologyCatalog.parse(catalog(
                "metadata_only",
                "igneous",
                "minecraft:granite",
                null
        ));

        LithologyCatalog.Entry entry = snapshot.require("limestone");
        assertEquals("minecraft:granite", entry.block());
        assertNull(entry.baselineFeature());
        assertFalse(entry.geoStrataOwned());
    }

    @Test
    void rejectsAccidentalRuntimeActivation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LithologyCatalog.parse(catalog(
                        "runtime_bias",
                        "sedimentary",
                        "geostrata:limestone",
                        "limestone_ore"
                ))
        );
    }

    @Test
    void rejectsUnknownRockClass() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LithologyCatalog.parse(catalog(
                        "metadata_only",
                        "sedimentary-ish",
                        "geostrata:limestone",
                        "limestone_ore"
                ))
        );
    }

    @Test
    void rejectsMissingGeoStrataFallback() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LithologyCatalog.parse(catalog(
                        "metadata_only",
                        "sedimentary",
                        "geostrata:limestone",
                        null
                ))
        );
    }

    @Test
    void rejectsGeoStrataFallbackForProviderOwnedBlock() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LithologyCatalog.parse(catalog(
                        "metadata_only",
                        "igneous",
                        "minecraft:granite",
                        "granite_ore"
                ))
        );
    }

    @Test
    void rejectsDuplicateIdsAndUnknownLookup() {
        String json = """
                {
                  "schemaVersion": 1,
                  "model": "geostrata:lithology_catalog",
                  "runtimeStatus": "metadata_only",
                  "lithologies": [
                    %s,
                    %s
                  ]
                }
                """.formatted(
                entry("limestone", "geostrata:limestone", "limestone_ore"),
                entry("limestone", "geostrata:chalk", "chalk_ore")
        );
        assertThrows(IllegalArgumentException.class, () -> LithologyCatalog.parse(parse(json)));

        LithologyCatalog.Snapshot snapshot = LithologyCatalog.parse(catalog(
                "metadata_only",
                "sedimentary",
                "geostrata:limestone",
                "limestone_ore"
        ));
        assertThrows(IllegalArgumentException.class, () -> snapshot.require("gneiss"));
    }

    private static JsonObject catalog(String runtimeStatus, String rockClass, String block, String baselineFeature) {
        String json = """
                {
                  "schemaVersion": 1,
                  "model": "geostrata:lithology_catalog",
                  "runtimeStatus": "%s",
                  "lithologies": [
                    {
                      "id": "limestone",
                      "block": "%s",
                      "rockClass": "%s",
                      "genesis": "carbonate",
                      "bodyStyle": "bedded",
                      "depthAffinity": "shallow_to_mid",
                      "continuity": "regional",
                      "biomeTag": "geostrata:has_common_rocks",
                      "baselineFeature": %s
                    }
                  ]
                }
                """.formatted(runtimeStatus, block, rockClass, jsonStringOrNull(baselineFeature));
        return parse(json);
    }

    private static String entry(String id, String block, String baselineFeature) {
        return """
                {
                  "id": "%s",
                  "block": "%s",
                  "rockClass": "sedimentary",
                  "genesis": "carbonate",
                  "bodyStyle": "bedded",
                  "depthAffinity": "shallow_to_mid",
                  "continuity": "regional",
                  "biomeTag": "geostrata:has_common_rocks",
                  "baselineFeature": %s
                }
                """.formatted(id, block, jsonStringOrNull(baselineFeature));
    }

    private static String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
