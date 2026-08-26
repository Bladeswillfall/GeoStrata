package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LithologyCatalogTest {
    @Test
    void parsesSemanticLithologyEntry() {
        LithologyCatalog.Snapshot snapshot = LithologyCatalog.parse(catalog("metadata_only", "sedimentary", "geostrata:limestone"));

        assertEquals(1, snapshot.entries().size());
        LithologyCatalog.Entry entry = snapshot.require("limestone");
        assertEquals("geostrata:limestone", entry.block());
        assertEquals("sedimentary", entry.rockClass());
        assertEquals("carbonate", entry.genesis());
        assertEquals("bedded", entry.bodyStyle());
        assertEquals("regional", entry.continuity());
        assertEquals("geostrata:has_common_rocks", entry.biomeTag());
        assertEquals("limestone_ore", entry.baselineFeature());
    }

    @Test
    void rejectsAccidentalRuntimeActivation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LithologyCatalog.parse(catalog("runtime_bias", "sedimentary", "geostrata:limestone"))
        );
    }

    @Test
    void rejectsUnknownRockClass() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LithologyCatalog.parse(catalog("metadata_only", "sedimentary-ish", "geostrata:limestone"))
        );
    }

    @Test
    void rejectsExternalBlockNamespace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LithologyCatalog.parse(catalog("metadata_only", "sedimentary", "minecraft:stone"))
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
                """.formatted(entry("limestone", "geostrata:limestone"), entry("limestone", "geostrata:chalk"));
        assertThrows(IllegalArgumentException.class, () -> LithologyCatalog.parse(parse(json)));

        LithologyCatalog.Snapshot snapshot = LithologyCatalog.parse(catalog("metadata_only", "sedimentary", "geostrata:limestone"));
        assertThrows(IllegalArgumentException.class, () -> snapshot.require("gneiss"));
    }

    private static JsonObject catalog(String runtimeStatus, String rockClass, String block) {
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
                      "baselineFeature": "limestone_ore"
                    }
                  ]
                }
                """.formatted(runtimeStatus, block, rockClass);
        return parse(json);
    }

    private static String entry(String id, String block) {
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
                  "baselineFeature": "%s_ore"
                }
                """.formatted(id, block, id);
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
