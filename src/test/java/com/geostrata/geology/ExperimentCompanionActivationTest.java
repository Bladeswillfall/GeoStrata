package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExperimentCompanionActivationTest {
    @Test
    void companionPromotesOreAndDiamondExperimentsWithoutChangingTuning() {
        OreDepositExperiment.Snapshot ore = new OreDepositExperiment.Snapshot(
                "experimental_opt_in",
                false,
                "chunk_local_valid_host_clipping",
                "not_implemented",
                Map.of("emerald", 0.004)
        );
        DiamondGeologyExperiment.Snapshot diamond = new DiamondGeologyExperiment.Snapshot(
                "experimental_opt_in",
                false,
                "not_implemented",
                Map.of("kimberlite", 0.06, "lamproite", 0.02),
                0.12
        );

        OreDepositExperiment.Snapshot activeOre = ore.activated(true);
        DiamondGeologyExperiment.Snapshot activeDiamond = diamond.activated(true);

        assertTrue(activeOre.enabled());
        assertEquals("experimental_runtime", activeOre.runtimeStatus());
        assertEquals(0.004, activeOre.activationChance("emerald"), 1.0e-12);
        assertTrue(activeDiamond.enabled());
        assertEquals("experimental_runtime", activeDiamond.runtimeStatus());
        assertEquals(0.06, activeDiamond.pipeActivationChance("kimberlite"), 1.0e-12);
        assertEquals(0.12, activeDiamond.structuralActivationChancePerCell(), 1.0e-12);

        assertFalse(ore.enabled());
        assertFalse(diamond.enabled());
        assertSame(ore, ore.activated(false));
        assertSame(diamond, diamond.activated(false));
    }
}
