package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExperimentCompanionActivationTest {
    @Test
    void companionLeavesCoreVanillaOwnershipIntactWhileExtendingOptionalOres() {
        OreDepositExperiment.Snapshot ore = new OreDepositExperiment.Snapshot(
                "experimental_opt_in",
                false,
                "chunk_local_valid_host_clipping",
                "not_implemented",
                Map.of("emerald", 0.08, "gold", 0.8, "coal", 0.8, "iron", 1.0, "copper", 0.36)
        );
        DiamondGeologyExperiment.Snapshot diamond = new DiamondGeologyExperiment.Snapshot(
                "core_runtime",
                true,
                "core_overworld",
                Map.of("kimberlite", 0.06, "lamproite", 0.02),
                0.12
        );

        OreDepositExperiment.Snapshot coreOre = ore.activated(false, true);
        OreDepositExperiment.Snapshot activeOre = ore.activated(true, true);
        OreDepositExperiment.Snapshot benchmarkCompanionOre = ore.activated(true, false);
        DiamondGeologyExperiment.Snapshot activeDiamond = diamond.activated(true);

        assertTrue(coreOre.enabled());
        assertEquals("core_common_runtime", coreOre.runtimeStatus());
        assertEquals(
                Set.of("coal", "iron", "copper", "gold", "emerald"),
                coreOre.activationChancePerCandidate().keySet()
        );

        assertTrue(activeOre.enabled());
        assertEquals("experimental_runtime", activeOre.runtimeStatus());
        assertEquals("core_common_overworld", activeOre.nativeGenerationSuppression());
        assertEquals(0.08, activeOre.activationChance("emerald"), 1.0e-12);
        assertEquals(0.8, activeOre.activationChance("gold"), 1.0e-12);
        assertEquals(0.8, activeOre.activationChance("coal"), 1.0e-12);
        assertEquals(1.0, activeOre.activationChance("iron"), 1.0e-12);
        assertEquals(0.36, activeOre.activationChance("copper"), 1.0e-12);

        assertTrue(benchmarkCompanionOre.enabled());
        assertEquals("experimental_runtime", benchmarkCompanionOre.runtimeStatus());
        assertTrue(benchmarkCompanionOre.activationChancePerCandidate().isEmpty());
        assertEquals("not_implemented", benchmarkCompanionOre.nativeGenerationSuppression());

        assertTrue(activeDiamond.enabled());
        assertEquals("core_runtime", activeDiamond.runtimeStatus());
        assertEquals("core_overworld", activeDiamond.nativeGenerationSuppression());
        assertEquals(0.06, activeDiamond.pipeActivationChance("kimberlite"), 1.0e-12);
        assertEquals(0.12, activeDiamond.structuralActivationChancePerCell(), 1.0e-12);
        assertSame(diamond, activeDiamond);
        assertSame(ore, ore.activated(false, false));
    }
}
