package com.geostrata.worldgen.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrataLensFeatureTest {
    @Test
    void suitabilityActsAsAcceptanceProbability() {
        assertTrue(StrataLensFeature.passesSuitability(1.0, 0.999999));
        assertTrue(StrataLensFeature.passesSuitability(0.2, 0.199999));
        assertFalse(StrataLensFeature.passesSuitability(0.2, 0.2));
        assertFalse(StrataLensFeature.passesSuitability(0.05, 0.9));
    }

    @Test
    void suitabilityInputsAreSafelyClamped() {
        assertFalse(StrataLensFeature.passesSuitability(-1.0, 0.0));
        assertTrue(StrataLensFeature.passesSuitability(2.0, 0.999999));
    }
}
