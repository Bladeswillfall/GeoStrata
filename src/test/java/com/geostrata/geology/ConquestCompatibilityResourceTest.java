package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConquestCompatibilityResourceTest {
    private static final Path ROOT = Path.of("compat/conquest-reforged");

    @Test
    void bridgeIsAStandaloneDataPackWithoutCopiedConquestAssets() throws Exception {
        JsonObject pack = read("pack.mcmeta").getAsJsonObject("pack");
        assertEquals(15, pack.get("pack_format").getAsInt());
        assertFalse(Files.exists(ROOT.resolve("assets")));
    }

    @Test
    void externalPaletteEntriesAreOptionalAndCurated() throws Exception {
        Set<String> rocks = optionalConquestIds("data/geostrata/tags/blocks/compat/conquest/natural_rocks.json", 18);
        Set<String> soils = optionalConquestIds("data/geostrata/tags/blocks/compat/conquest/natural_soils.json", 3);
        Set<String> muds = optionalConquestIds("data/geostrata/tags/blocks/compat/conquest/natural_muds.json", 2);
        Set<String> hydric = optionalConquestIds("data/geostrata/tags/blocks/compat/conquest/hydric_sediments.json", 10);

        assertTrue(rocks.contains("conquest:shale"));
        assertTrue(rocks.contains("conquest:rhyolite"));
        assertTrue(rocks.contains("conquest:gray_limestone"));
        assertTrue(soils.contains("conquest:unfertile_loamy_dirt"));
        assertTrue(muds.contains("conquest:mud"));
        assertTrue(hydric.contains("conquest:dark_silt"));
    }

    @Test
    void publicExtensionTagsOnlyReferencePrivateConquestPalettes() throws Exception {
        assertSingleTagReference(
                "data/geostrata/tags/blocks/worldgen/base_stone_replaceables.json",
                "#geostrata:compat/conquest/natural_rocks"
        );
        assertSingleTagReference(
                "data/geostrata/tags/blocks/worldgen/soil_replaceables.json",
                "#geostrata:compat/conquest/natural_soils"
        );
        assertSingleTagReference(
                "data/geostrata/tags/blocks/worldgen/mud_replaceables.json",
                "#geostrata:compat/conquest/natural_muds"
        );
        assertSingleTagReference(
                "data/geostrata/tags/blocks/worldgen/hydric_sediment_replaceables.json",
                "#geostrata:compat/conquest/hydric_sediments"
        );
        assertSingleTagReference(
                "data/minecraft/tags/blocks/sculk_replaceable.json",
                "#geostrata:compat/conquest/natural_rocks"
        );
    }

    private static JsonObject read(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
    }

    private static Set<String> optionalConquestIds(String relative, int minimumDirectEntries) throws Exception {
        JsonObject tag = read(relative);
        assertFalse(tag.get("replace").getAsBoolean());

        JsonArray values = tag.getAsJsonArray("values");
        Set<String> ids = new HashSet<>();
        for (JsonElement value : values) {
            if (value.isJsonPrimitive()) {
                assertTrue(value.getAsString().startsWith("#geostrata:compat/conquest/"));
                continue;
            }

            JsonObject entry = value.getAsJsonObject();
            String id = entry.get("id").getAsString();
            assertTrue(id.startsWith("conquest:"));
            assertFalse(entry.get("required").getAsBoolean());
            assertTrue(ids.add(id), "duplicate Conquest compatibility ID: " + id);
        }
        assertTrue(ids.size() >= minimumDirectEntries);
        return ids;
    }

    private static void assertSingleTagReference(String relative, String expected) throws Exception {
        JsonObject tag = read(relative);
        assertFalse(tag.get("replace").getAsBoolean());
        JsonArray values = tag.getAsJsonArray("values");
        assertEquals(1, values.size());
        assertEquals(expected, values.get(0).getAsString());
    }
}
