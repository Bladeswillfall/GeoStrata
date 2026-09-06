package com.geostrata.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreHostTest {
    @Test
    void everyHostRoundTripsThroughItsStableId() {
        for (OreHost host : OreHost.values()) {
            assertTrue(OreHost.supports(host.asString()));
            assertEquals(host, OreHost.byId(host.asString()));
        }
    }

    @Test
    void semanticLithologyWithoutBakedOreTextureIsNotRenderable() {
        assertTrue(OreHost.supports("granite"));
        assertEquals(OreHost.GRANITE, OreHost.byId("granite"));
        assertTrue(OreHost.supports("gabbro"));
        assertTrue(OreHost.supports("peridotite"));
        assertTrue(OreHost.supports("sandstone"));
        assertEquals(OreHost.SANDSTONE, OreHost.byId("sandstone"));
        assertFalse(OreHost.supports("diorite"));
        assertFalse(OreHost.supports("hornfels"));
        assertThrows(IllegalArgumentException.class, () -> OreHost.byId("diorite"));
    }

    @Test
    void materialDefaultsAreGeologicallyValidHosts() {
        assertEquals(OreHost.SHALE, OreHost.defaultFor("coal"));
        assertEquals(OreHost.SHALE, OreHost.defaultFor("iron"));
        assertEquals(OreHost.SHALE, OreHost.defaultFor("copper"));
        assertEquals(OreHost.GRANITE, OreHost.defaultFor("tin"));
        assertEquals(OreHost.SLATE, OreHost.defaultFor("gold"));
        assertThrows(IllegalArgumentException.class, () -> OreHost.defaultFor("unsupported"));
    }

    @Test
    void everyGradedOreBlockstateCoversEveryRegisteredHost() throws IOException {
        Path blockstates = Path.of("src/main/resources/assets/geostrata/blockstates");
        List<Path> gradedOreFiles;
        try (var files = Files.list(blockstates)) {
            gradedOreFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .matches("(poor|medium|rich|massive)_.+_ore\\.json"))
                    .toList();
        }
        assertFalse(gradedOreFiles.isEmpty(), "graded ore blockstates must exist");

        for (Path path : gradedOreFiles) {
            JsonObject variants = JsonParser.parseString(Files.readString(path))
                    .getAsJsonObject()
                    .getAsJsonObject("variants");
            for (OreHost host : OreHost.values()) {
                assertTrue(
                        variants.has("host=" + host.asString()),
                        () -> path.getFileName() + " is missing host=" + host.asString()
                );
            }
        }
    }
}
