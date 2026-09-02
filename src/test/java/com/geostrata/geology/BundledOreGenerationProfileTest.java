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

final class BundledOreGenerationProfileTest {
    private static final Path GEOLOGY = Path.of("src/main/resources/data/geostrata/geology");

    @Test
    void bundledProfilesPreserveLegacyMorphologyAndDiscoveryTuning() throws IOException {
        OreOccurrenceCatalog.Snapshot ores = OreOccurrenceCatalog.parse(
                LithologyCatalog.parse(read("lithologies.json")),
                read("ore_occurrences.json")
        );

        OreGenerationProfile coal = ores.require("coal").generation();
        OreGenerationProfile iron = ores.require("iron").generation();
        OreGenerationProfile copper = ores.require("copper").generation();
        OreGenerationProfile gold = ores.require("gold").generation();
        OreGenerationProfile emerald = ores.require("emerald").generation();

        assertEquals(3.0, coal.traceNormalScale(), 0.0);
        assertEquals(1.65, iron.bodyScale(), 0.0);
        assertEquals(0.45, gold.bodyScale(), 0.0);
        assertEquals(1.65, emerald.bodyScale(), 0.0);

        assertTrue(iron.discoveryStringers().enabled());
        assertEquals(14, iron.discoveryStringers().count());
        assertEquals(52.0, iron.discoveryStringers().minLength(), 0.0);
        assertEquals(96.0, iron.discoveryStringers().maxLength(), 0.0);
        assertEquals(1.0, iron.discoveryStringers().exposedHaloBlocks(), 0.0);
        assertEquals(0, iron.discoveryStringers().downwardBiasedCount());

        assertTrue(copper.discoveryStringers().enabled());
        assertEquals(12, copper.discoveryStringers().count());
        assertEquals(48.0, copper.discoveryStringers().minLength(), 0.0);
        assertEquals(88.0, copper.discoveryStringers().maxLength(), 0.0);
        assertEquals(4.0, copper.discoveryStringers().exposedHaloBlocks(), 0.0);
        assertEquals(4, copper.discoveryStringers().downwardBiasedCount());
        assertEquals(-1.0, copper.discoveryStringers().downwardBias(), 0.0);

        assertFalse(coal.discoveryStringers().enabled());
        assertFalse(gold.discoveryStringers().enabled());
        assertFalse(emerald.discoveryStringers().enabled());
        assertEquals(OreGenerationProfile.GradeTuning.defaults(), copper.grades());
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(GEOLOGY.resolve(name))).getAsJsonObject();
    }
}
