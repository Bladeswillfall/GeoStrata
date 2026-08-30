package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
