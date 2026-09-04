package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreOccurrenceConstraintTest {
    @Test
    void terrainFilterUsesReliefAndPositiveProminence() {
        OreOccurrenceCatalog.TerrainFilter filter = new OreOccurrenceCatalog.TerrainFilter(24, true);
        assertFalse(filter.matches(new TerrainMorphologySample(100, 0, 0, 23, 10)));
        assertFalse(filter.matches(new TerrainMorphologySample(100, 0, 0, 30, -1)));
        assertTrue(filter.matches(new TerrainMorphologySample(100, 0, 0, 24, 1)));
    }

    @Test
    void naturalGradeCapKeepsEmeraldOutOfMassiveGeneration() {
        OreOccurrenceCatalog.Occurrence occurrence = new OreOccurrenceCatalog.Occurrence(
                "emerald",
                "minecraft",
                "minecraft:emerald",
                List.of("schist"),
                List.of(GeologyProvince.OROGENIC_BELT),
                List.of("vein"),
                OreOccurrenceCatalog.TerrainFilter.none(),
                OreGrade.RICH,
                Map.of(
                        OreGrade.POOR, "geostrata:poor_emerald_ore",
                        OreGrade.MEDIUM, "geostrata:medium_emerald_ore",
                        OreGrade.RICH, "geostrata:rich_emerald_ore",
                        OreGrade.MASSIVE, "geostrata:massive_emerald_ore"
                )
        );
        assertEquals(OreGrade.MEDIUM, occurrence.capNaturalGrade(OreGrade.MEDIUM));
        assertEquals(OreGrade.RICH, occurrence.capNaturalGrade(OreGrade.MASSIVE));
    }
}
