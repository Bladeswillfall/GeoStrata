package com.geostrata.geology;

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

final class BundledBiomeAffinityPolicyTest {
    private static final Path GEOLOGY = Path.of("src/main/resources/data/geostrata/geology");
    private static final String SWAMP = "geostrata:has_swamp_soils";
    private static final String BADLANDS = "geostrata:has_badlands_soils";
    private static final String MOUNTAIN = "geostrata:has_mountain_rocks";

    @Test
    void specialZoneBonusesTiltEligibleOreWithoutBecomingHardGates() throws IOException {
        OreOccurrenceCatalog.Snapshot ores = OreOccurrenceCatalog.parse(
                LithologyCatalog.parse(read("lithologies.json")),
                read("ore_occurrences.json")
        );

        OreOccurrenceCatalog.Occurrence coal = ores.require("coal");
        assertEquals(1.15, matchingBiomeMultiplier(coal, SWAMP), 0.0);
        assertEquals(1.0, unmatchedBiomeMultiplier(coal), 0.0);
        assertEquals(0.92, effectiveChance(coal, 32, SWAMP), 1.0e-12);
        assertEquals(0.8, effectiveChance(coal, 32, null), 1.0e-12);
        assertTrue(effectiveChance(coal, 32, SWAMP) < 1.0);
        assertTrue(coal.provinceContexts().contains(GeologyProvince.SEDIMENTARY_BASIN));
        assertFalse(coal.provinceContexts().contains(GeologyProvince.VOLCANIC_ARC));

        OreOccurrenceCatalog.Occurrence gold = ores.require("gold");
        assertEquals(1.15, matchingBiomeMultiplier(gold, BADLANDS), 0.0);
        assertEquals(1.0, unmatchedBiomeMultiplier(gold), 0.0);
        assertEquals(0.92, effectiveChance(gold, 32, BADLANDS), 1.0e-12);
        assertEquals(0.8, effectiveChance(gold, 32, null), 1.0e-12);
        assertFalse(gold.provinceContexts().contains(GeologyProvince.SEDIMENTARY_BASIN));

        OreOccurrenceCatalog.Occurrence emerald = ores.require("emerald");
        assertEquals(1.25, matchingBiomeMultiplier(emerald, MOUNTAIN), 0.0);
        assertEquals(1.0, unmatchedBiomeMultiplier(emerald), 0.0);
        assertTrue(effectiveChance(emerald, 64, MOUNTAIN) > effectiveChance(emerald, 64, null));
        assertTrue(effectiveChance(emerald, 64, null) > 0.0);
        assertTrue(effectiveChance(emerald, 64, MOUNTAIN) < 1.0);
        assertEquals(List.of(GeologyProvince.OROGENIC_BELT), emerald.provinceContexts());

        assertTrue(ores.require("iron").generation().biomeMultipliers().isEmpty());
    }

    private static double matchingBiomeMultiplier(OreOccurrenceCatalog.Occurrence occurrence, String tag) {
        return occurrence.generation().biomeMultiplier(candidate -> candidate.equals(tag));
    }

    private static double unmatchedBiomeMultiplier(OreOccurrenceCatalog.Occurrence occurrence) {
        return occurrence.generation().biomeMultiplier(ignored -> false);
    }

    private static double effectiveChance(
            OreOccurrenceCatalog.Occurrence occurrence,
            int y,
            String matchingTag
    ) {
        OreGenerationProfile generation = occurrence.generation();
        double biome = matchingTag == null
                ? unmatchedBiomeMultiplier(occurrence)
                : matchingBiomeMultiplier(occurrence, matchingTag);
        return Math.min(1.0, generation.activationChance() * generation.depthMultiplier(y) * biome);
    }

    private static JsonObject read(String name) throws IOException {
        return JsonParser.parseString(Files.readString(GEOLOGY.resolve(name))).getAsJsonObject();
    }
}
