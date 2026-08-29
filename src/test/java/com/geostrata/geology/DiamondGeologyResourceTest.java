package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiamondGeologyResourceTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void bundledDiamondExperimentIsSafeByDefault() throws IOException {
        JsonObject root = read("data/geostrata/geology/diamond_geology_experiment.json");
        DiamondGeologyExperiment.Snapshot snapshot = DiamondGeologyExperiment.parse(root);

        assertFalse(snapshot.enabled());
        assertEquals("not_implemented", snapshot.nativeGenerationSuppression());
        assertTrue(snapshot.pipeActivationChance("kimberlite") > snapshot.pipeActivationChance("lamproite"));
        assertTrue(snapshot.structuralActivationChancePerCell() > 0.0);
    }

    @Test
    void diamondWorldgenResourcesArePaired() throws IOException {
        assertPipe("kimberlite");
        assertPipe("lamproite");

        JsonObject configured = read("data/geostrata/worldgen/configured_feature/diamond_structural_experiment.json");
        assertEquals("geostrata:diamond_structural", configured.get("type").getAsString());
        JsonObject placed = read("data/geostrata/worldgen/placed_feature/diamond_structural_experiment.json");
        assertEquals("geostrata:diamond_structural_experiment", placed.get("feature").getAsString());
        assertTrue(placed.getAsJsonArray("placement").isEmpty());
    }

    private static void assertPipe(String kind) throws IOException {
        JsonObject configured = read("data/geostrata/worldgen/configured_feature/" + kind + "_pipe.json");
        assertEquals("geostrata:diamond_pipe", configured.get("type").getAsString());
        assertEquals(kind, configured.getAsJsonObject("config").get("pipe_kind").getAsString());
        assertEquals(
                "geostrata:" + kind,
                configured.getAsJsonObject("config")
                        .getAsJsonArray("targets")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("state")
                        .get("Name").getAsString()
        );

        JsonObject placed = read("data/geostrata/worldgen/placed_feature/" + kind + "_pipe.json");
        assertEquals("geostrata:" + kind + "_pipe", placed.get("feature").getAsString());
        assertTrue(placed.getAsJsonArray("placement").isEmpty());
    }

    private static JsonObject read(String relative) throws IOException {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve(relative))).getAsJsonObject();
    }
}
