package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SedimentSuitabilityResourceContractTest {
    private static final Path DATA = Path.of("src/main/resources/data/geostrata");
    private static final Path CONFIGURED = DATA.resolve("worldgen/configured_feature");
    private static final Path PLACED = DATA.resolve("worldgen/placed_feature");

    @Test
    void migratedSedimentsUseShallowDisksAndEvidenceGates() throws IOException {
        assertProfile("clay_loam_patch", "geostrata:worldgen/soil_replaceables", "clay_loam",
                "geostrata:has_river_soils", 0.04, 0.08, 0.12, 0.22, 0.30);
        assertProfile("silty_loam_patch", "geostrata:worldgen/soil_replaceables", "silty_loam",
                "geostrata:has_river_soils", 0.025, 0.10, 0.18, 0.25, 0.32);
        assertProfile("peat_soil_patch", "geostrata:worldgen/soil_replaceables", "peat_soil",
                "geostrata:has_swamp_soils", 0.005, 0.03, 0.04, 0.15, 0.55);
        assertProfile("wet_mud_patch", "geostrata:worldgen/hydric_sediment_replaceables", "wet_mud",
                "geostrata:has_swamp_soils", 0.01, 0.05, 0.06, 0.35, 0.40);
        assertProfile("compacted_mud_patch", "geostrata:worldgen/hydric_sediment_replaceables", "compacted_mud",
                "geostrata:has_jungle_soils", 0.02, 0.05, 0.06, -0.12, 0.35);

        JsonObject surfaceTag = read(DATA.resolve("tags/worldgen/biome/has_surface_sediments.json"));
        assertEquals(List.of("#minecraft:is_overworld"), surfaceTag.getAsJsonArray("values").asList().stream()
                .map(JsonElement::getAsString)
                .toList());
    }

    private static void assertProfile(
            String feature,
            String targetTag,
            String id,
            String preferredBiomeTag,
            double base,
            double flatness,
            double valley,
            double submerged,
            double biomeBonus
    ) throws IOException {
        JsonObject configured = read(CONFIGURED.resolve(feature + ".json"));
        assertEquals("minecraft:disk", configured.get("type").getAsString());
        assertEquals(
                targetTag,
                configured.getAsJsonObject("config").getAsJsonObject("target").get("tag").getAsString()
        );

        JsonObject placed = read(PLACED.resolve(feature + ".json"));
        JsonArray placement = placed.getAsJsonArray("placement");
        List<String> types = placement.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(entry -> entry.get("type").getAsString())
                .toList();
        assertTrue(types.contains("minecraft:in_square"), feature);
        assertTrue(types.contains("minecraft:heightmap"), feature);
        assertTrue(types.contains("geostrata:sediment_suitability"), feature);
        assertTrue(types.contains("minecraft:biome"), feature);
        assertFalse(types.contains("minecraft:height_range"), feature);
        assertFalse(types.contains("minecraft:count"), feature);

        JsonObject heightmap = placement.get(types.indexOf("minecraft:heightmap")).getAsJsonObject();
        assertEquals("OCEAN_FLOOR_WG", heightmap.get("heightmap").getAsString());

        JsonObject gate = placement.get(types.indexOf("geostrata:sediment_suitability")).getAsJsonObject();
        assertEquals(id, gate.get("id").getAsString());
        assertEquals(preferredBiomeTag, gate.get("preferred_biome_tag").getAsString());
        assertEquals(base, gate.get("base_chance").getAsDouble(), 1.0e-12);
        assertEquals(flatness, gate.get("flatness_weight").getAsDouble(), 1.0e-12);
        assertEquals(valley, gate.get("valley_weight").getAsDouble(), 1.0e-12);
        assertEquals(submerged, gate.get("submerged_weight").getAsDouble(), 1.0e-12);
        assertEquals(biomeBonus, gate.get("preferred_biome_bonus").getAsDouble(), 1.0e-12);
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
