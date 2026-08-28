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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClayHydrologyResourceContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources/data/geostrata");
    private static final Path CONFIGURED = RESOURCES.resolve("worldgen/configured_feature");
    private static final Path PLACED = RESOURCES.resolve("worldgen/placed_feature");

    @Test
    void clayUsesWaterBiasedDisksWithRareBackgroundDeposits() throws IOException {
        assertConfiguredDisk("blue_clay_patch", "geostrata:blue_clay", 2, 4, 2);
        assertConfiguredDisk("red_clay_patch", "geostrata:red_clay", 2, 3, 1);

        assertWaterPlacement("blue_clay_patch", 3);
        assertWaterPlacement("red_clay_patch", 10);
        assertWaterPlacement("red_clay_badlands_patch", null);
        assertBackgroundPlacement("blue_clay_background_patch", 24);
        assertBackgroundPlacement("red_clay_background_patch", 40);

        JsonArray values = read(RESOURCES.resolve("tags/blocks/worldgen/hydric_sediment_replaceables.json"))
                .getAsJsonArray("values");
        Set<String> replaceables = values.asList().stream().map(JsonElement::getAsString).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                "minecraft:dirt",
                "minecraft:clay",
                "minecraft:sand",
                "minecraft:red_sand",
                "minecraft:gravel",
                "minecraft:mud"
        ), replaceables);
    }

    private static void assertConfiguredDisk(
            String name,
            String block,
            int minRadius,
            int maxRadius,
            int halfHeight
    ) throws IOException {
        JsonObject configured = read(CONFIGURED.resolve(name + ".json"));
        assertEquals("minecraft:disk", configured.get("type").getAsString());
        JsonObject config = configured.getAsJsonObject("config");
        assertEquals(halfHeight, config.get("half_height").getAsInt());

        JsonObject radius = config.getAsJsonObject("radius").getAsJsonObject("value");
        assertEquals(minRadius, radius.get("min_inclusive").getAsInt());
        assertEquals(maxRadius, radius.get("max_inclusive").getAsInt());

        JsonObject target = config.getAsJsonObject("target");
        assertEquals("minecraft:matching_block_tag", target.get("type").getAsString());
        assertEquals("geostrata:worldgen/hydric_sediment_replaceables", target.get("tag").getAsString());

        JsonObject state = config.getAsJsonObject("state_provider")
                .getAsJsonObject("fallback")
                .getAsJsonObject("state");
        assertEquals(block, state.get("Name").getAsString());
    }

    private static void assertWaterPlacement(String name, Integer rarity) throws IOException {
        JsonArray placement = read(PLACED.resolve(name + ".json")).getAsJsonArray("placement");
        List<String> types = types(placement);
        assertTrue(types.contains("minecraft:in_square"), name);
        assertTrue(types.contains("minecraft:heightmap"), name);
        assertTrue(types.contains("minecraft:block_predicate_filter"), name);
        assertTrue(types.contains("minecraft:biome"), name);
        assertHeightmap(placement, types, name);

        JsonObject filter = placement.get(types.indexOf("minecraft:block_predicate_filter")).getAsJsonObject();
        JsonObject predicate = filter.getAsJsonObject("predicate");
        assertEquals("minecraft:matching_fluids", predicate.get("type").getAsString(), name);
        assertEquals("minecraft:water", predicate.get("fluids").getAsString(), name);

        if (rarity == null) {
            assertFalse(types.contains("minecraft:rarity_filter"), name);
        } else {
            assertEquals(rarity.intValue(), rarity(placement, types, name));
        }
    }

    private static void assertBackgroundPlacement(String name, int rarity) throws IOException {
        JsonArray placement = read(PLACED.resolve(name + ".json")).getAsJsonArray("placement");
        List<String> types = types(placement);
        assertEquals(rarity, rarity(placement, types, name));
        assertTrue(types.contains("minecraft:in_square"), name);
        assertTrue(types.contains("minecraft:heightmap"), name);
        assertTrue(types.contains("minecraft:biome"), name);
        assertFalse(types.contains("minecraft:block_predicate_filter"), name);
        assertHeightmap(placement, types, name);
    }

    private static int rarity(JsonArray placement, List<String> types, String name) {
        int index = types.indexOf("minecraft:rarity_filter");
        assertTrue(index >= 0, name);
        return placement.get(index).getAsJsonObject().get("chance").getAsInt();
    }

    private static void assertHeightmap(JsonArray placement, List<String> types, String name) {
        int index = types.indexOf("minecraft:heightmap");
        assertTrue(index >= 0, name);
        assertEquals("OCEAN_FLOOR_WG", placement.get(index).getAsJsonObject().get("heightmap").getAsString(), name);
    }

    private static List<String> types(JsonArray placement) {
        return placement.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(modifier -> modifier.get("type").getAsString())
                .toList();
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
