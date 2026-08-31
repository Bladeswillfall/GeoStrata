package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrogenicBeltModelTest {
    private static final Set<String> LITHOLOGIES = Set.of(
            "gneiss", "schist", "phyllite", "slate", "quartzite", "marble"
    );

    @Test
    void samplingIsDeterministic() {
        OrogenicBeltModel.Context context = OrogenicBeltModel.forSite(123456789L, 170, -575, 63.0);
        assertEquals(
                context.column(384, -192, 18.0).sample(24.0),
                context.column(384, -192, 18.0).sample(24.0)
        );
    }

    @Test
    void architectureContainsMetamorphicGradientAndMarble() {
        OrogenicBeltModel.Context context = OrogenicBeltModel.forSite(987654321L, 0, 0, 63.0);
        Set<String> found = new HashSet<>();
        for (int x = -640; x <= 640; x += 20) {
            for (int z = -640; z <= 640; z += 20) {
                OrogenicBeltModel.Column column = context.column(x, z, 14.0);
                for (int y = -48; y <= 112; y += 16) {
                    String lithology = column.sample(y).lithology();
                    assertTrue(LITHOLOGIES.contains(lithology), lithology);
                    found.add(lithology);
                }
            }
        }

        assertTrue(found.contains("gneiss"), found.toString());
        assertTrue(found.contains("schist"), found.toString());
        assertTrue(found.contains("phyllite"), found.toString());
        assertTrue(found.contains("slate"), found.toString());
        assertTrue(found.contains("quartzite"), found.toString());
        assertTrue(found.contains("marble"), found.toString());
    }
}
