package com.geostrata.worldgen.feature;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreDepositFeatureTest {
    private static final Set<String> IRON_HOSTS = Set.of("shale", "limestone", "quartzite", "basalt", "rhyolite");

    @Test
    void matchesFinalOrInheritedHostWithoutAcceptingUnrelatedMetamorphicRock() {
        assertTrue(OreDepositFeature.matchesHostLineage(IRON_HOSTS, "shale", Optional.empty()));
        assertTrue(OreDepositFeature.matchesHostLineage(IRON_HOSTS, "schist", Optional.of("shale")));
        assertFalse(OreDepositFeature.matchesHostLineage(IRON_HOSTS, "schist", Optional.of("mudstone")));
        assertFalse(OreDepositFeature.matchesHostLineage(IRON_HOSTS, "schist", Optional.empty()));
    }
}
