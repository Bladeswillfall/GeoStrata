package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VanillaCoreLithologyResourceTest {
    private static final Path GEOLOGY = Path.of("src/main/resources/data/geostrata/geology");

    @Test
    void vanillaBlocksBackTheNewCoreLithologies() throws IOException {
        Map<String, JsonObject> lithologies = indexed(read("lithologies.json").getAsJsonArray("lithologies"));

        assertLithology(lithologies.get("tuff"), "minecraft:tuff", "igneous", "volcanic_arc_complex");
        assertLithology(lithologies.get("calcite"), "minecraft:calcite", "sedimentary", "sedimentary_stratigraphy");
        assertLithology(lithologies.get("basalt"), "minecraft:basalt", "igneous", null);
    }

    @Test
    void calciteIsAThinCarbonateMemberRatherThanANewGenerator() throws IOException {
        Map<String, JsonObject> successions = indexed(
                read("sedimentary_successions.json").getAsJsonArray("successions")
        );
        JsonArray beds = successions.get("shelf_chalk_carbonate_cycle").getAsJsonArray("beds");

        boolean found = false;
        for (var raw : beds) {
            JsonObject bed = raw.getAsJsonObject();
            if ("calcite".equals(bed.get("lithology").getAsString())) {
                assertEquals(0.25, bed.get("relativeThickness").getAsDouble(), 0.0);
                found = true;
            }
        }
        assertTrue(found, "shelf carbonate succession must contain its thin calcite member");
    }

    @Test
    void basaltFallbackUsesTheVanillaBlock() throws IOException {
        JsonObject configured = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/geostrata/worldgen/configured_feature/basalt_ore.json"
        ))).getAsJsonObject();
        String state = configured.getAsJsonObject("config")
                .getAsJsonArray("targets")
                .get(0).getAsJsonObject()
                .getAsJsonObject("state")
                .get("Name").getAsString();

        assertEquals("minecraft:basalt", state);
    }

    private static void assertLithology(
            JsonObject entry,
            String block,
            String rockClass,
            String runtimeAuthority
    ) {
        assertEquals(block, entry.get("block").getAsString());
        assertEquals(rockClass, entry.get("rockClass").getAsString());
        if (runtimeAuthority != null) {
            assertEquals(runtimeAuthority, entry.get("runtimeAuthority").getAsString());
        }
    }

    private static Map<String, JsonObject> indexed(JsonArray entries) {
        Map<String, JsonObject> byId = new HashMap<>();
        entries.forEach(raw -> {
            JsonObject entry = raw.getAsJsonObject();
            byId.put(entry.get("id").getAsString(), entry);
        });
        return byId;
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(GEOLOGY.resolve(name))).getAsJsonObject();
    }
}
