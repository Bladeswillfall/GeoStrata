package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class LithologyGenerationMethodTest {
    private static final Path RESOURCES = Path.of("src/main/resources/data/geostrata");

    @Test
    void naturalLithologyBaselinesDoNotUseVanillaOreBlobs() throws IOException {
        JsonObject catalog = read(RESOURCES.resolve("geology/lithologies.json"));
        for (var element : catalog.getAsJsonArray("lithologies")) {
            JsonObject lithology = element.getAsJsonObject();
            String feature = lithology.get("baselineFeature").getAsString();
            JsonObject configured = read(RESOURCES.resolve("worldgen/configured_feature/" + feature + ".json"));
            assertNotEquals("minecraft:ore", configured.get("type").getAsString(), feature);
        }
    }

    @Test
    void sandyLoamUsesTheSurfaceDiskPath() throws IOException {
        JsonObject configured = read(RESOURCES.resolve("worldgen/configured_feature/sandy_loam_patch.json"));
        assertEquals("minecraft:disk", configured.get("type").getAsString());

        JsonObject placed = read(RESOURCES.resolve("worldgen/placed_feature/sandy_loam_patch.json"));
        var types = placed.getAsJsonArray("placement").asList().stream()
                .map(element -> element.getAsJsonObject().get("type").getAsString())
                .toList();
        assertEquals(1, types.stream().filter("minecraft:heightmap"::equals).count());
        assertEquals(1, types.stream().filter("geostrata:sediment_suitability"::equals).count());
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
