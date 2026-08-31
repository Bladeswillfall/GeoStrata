package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VolcanicArcModelTest {
    private static final Set<String> LITHOLOGIES = Set.of(
            "gneiss",
            "schist",
            "quartzite",
            "basalt",
            "rhyolite",
            "breccia"
    );

    @Test
    void samplingIsDeterministic() {
        VolcanicArcModel.Sample first = VolcanicArcModel.sample(
                123456789L,
                144,
                -18.0,
                -320,
                170,
                -575,
                4.5,
                63.0
        );
        VolcanicArcModel.Sample second = VolcanicArcModel.sample(
                123456789L,
                144,
                -18.0,
                -320,
                170,
                -575,
                4.5,
                63.0
        );

        assertEquals(first, second);
    }

    @Test
    void architectureOnlyReturnsVolcanicArcLithologies() {
        for (int x = -384; x <= 384; x += 32) {
            for (int z = -384; z <= 384; z += 32) {
                for (int y = -48; y <= 96; y += 24) {
                    VolcanicArcModel.Sample sample = VolcanicArcModel.sample(
                            987654321L,
                            x,
                            y,
                            z,
                            64,
                            -128,
                            6.0,
                            63.0
                    );
                    assertTrue(LITHOLOGIES.contains(sample.lithology()), sample.toString());
                }
            }
        }
    }

    @Test
    void basaltSillsTerminateLaterallyAroundVolcanicComplexes() {
        VolcanicArcModel.Context context = VolcanicArcModel.forSite(987654321L, 64, -128, 63.0);
        VolcanicArcModel.Column inside = null;
        VolcanicArcModel.Column outside = null;

        for (int x = -256; x <= 256 && (inside == null || outside == null); x += 4) {
            for (int z = -384; z <= 128 && (inside == null || outside == null); z += 4) {
                VolcanicArcModel.Column column = context.column(x, z, 0.0);
                VolcanicArcModel.Sample atSill = column.sample(column.sillCenterY());
                if (inside == null && column.sillFootprint() < 0.5 && "sill".equals(atSill.bodyStyle())) {
                    inside = column;
                }
                if (outside == null && column.sillFootprint() > 1.5 && !"sill".equals(atSill.bodyStyle())) {
                    outside = column;
                }
            }
        }

        assertNotNull(inside, "expected a sampled column inside a finite basalt sill");
        assertNotNull(outside, "expected a sampled column outside the sill footprint");
        assertEquals("sill", inside.sample(inside.sillCenterY()).bodyStyle());
        assertNotEquals("sill", outside.sample(outside.sillCenterY()).bodyStyle());
    }
}
