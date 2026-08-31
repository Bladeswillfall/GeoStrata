package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CratonicShieldModelTest {
    private static final Set<String> LITHOLOGIES = Set.of("gneiss", "schist", "quartzite", "marble");

    @Test
    void samplingIsDeterministic() {
        CratonicShieldModel.Context context = CratonicShieldModel.forSite(123456789L, 170, -575, 63.0);
        assertEquals(
                context.column(384, -192, 7.5).sample(-18.0),
                context.column(384, -192, 7.5).sample(-18.0)
        );
    }

    @Test
    void architectureProducesVariedMetamorphicBasementIncludingMarble() {
        CratonicShieldModel.Context context = CratonicShieldModel.forSite(987654321L, 64, -128, 63.0);
        Set<String> found = new HashSet<>();
        for (int x = -768; x <= 768; x += 24) {
            for (int z = -768; z <= 768; z += 24) {
                CratonicShieldModel.Column column = context.column(x, z, 6.0);
                for (int y = -48; y <= 96; y += 12) {
                    String lithology = column.sample(y).lithology();
                    assertTrue(LITHOLOGIES.contains(lithology), lithology);
                    found.add(lithology);
                }
            }
        }

        assertTrue(found.contains("gneiss"), found.toString());
        assertTrue(found.contains("schist"), found.toString());
        assertTrue(found.contains("quartzite"), found.toString());
        assertTrue(found.contains("marble"), found.toString());
    }
}
