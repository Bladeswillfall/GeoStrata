package com.geostrata.block;

import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.OreGrade;
import com.geostrata.geology.OreOccurrenceCatalog;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProviderResolvedOreLootTest {
    @Test
    void usesResolvedExternalProviderOutput() {
        OreOccurrenceCatalog.Occurrence zinc = occurrence("test_provider", "test:resolved_zinc");

        assertEquals("test:resolved_zinc", GradedOreBlock.providerOutputItem(zinc));
    }

    @Test
    void ignoresMinecraftOwnedAndMissingOccurrences() {
        assertNull(GradedOreBlock.providerOutputItem(occurrence("minecraft", "minecraft:raw_iron")));
        assertNull(GradedOreBlock.providerOutputItem(null));
    }

    private static OreOccurrenceCatalog.Occurrence occurrence(String provider, String output) {
        return new OreOccurrenceCatalog.Occurrence(
                "zinc",
                provider,
                output,
                List.of("shale"),
                List.of(GeologyProvince.SEDIMENTARY_BASIN),
                List.of("stratiform"),
                gradeBlocks("zinc")
        );
    }

    private static Map<OreGrade, String> gradeBlocks(String material) {
        EnumMap<OreGrade, String> blocks = new EnumMap<>(OreGrade.class);
        for (OreGrade grade : OreGrade.values()) {
            blocks.put(grade, "geostrata:" + grade.id() + "_" + material + "_ore");
        }
        return blocks;
    }
}
