package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExperimentCompanionActivationTest {
    @Test
    void resolvedProviderOresRunInCoreWithoutCompanion() {
        OreDepositExperiment.Snapshot ore = new OreDepositExperiment.Snapshot(
                "experimental_opt_in",
                false,
                "chunk_local_valid_host_clipping",
                "not_implemented",
                Map.of(
                        "emerald", 0.08,
                        "gold", 0.8,
                        "coal", 0.8,
                        "iron", 1.0,
                        "copper", 0.36,
                        "tin", 0.0008
                )
        );
        DiamondGeologyExperiment.Snapshot diamond = new DiamondGeologyExperiment.Snapshot(
                "core_runtime",
                true,
                "core_overworld",
                Map.of("kimberlite", 0.06, "lamproite", 0.02),
                0.12
        );

        OreDepositExperiment.Snapshot coreOre = ore.activated(false, true);
        OreDepositExperiment.Snapshot companionOre = ore.activated(true, true);
        OreDepositExperiment.Snapshot benchmarkOre = ore.activated(false, false);
        DiamondGeologyExperiment.Snapshot activeDiamond = diamond.activated(true);

        assertTrue(coreOre.enabled());
        assertEquals("core_ore_runtime", coreOre.runtimeStatus());
        assertEquals("core_common_overworld", coreOre.nativeGenerationSuppression());
        assertEquals(
                Set.of("coal", "iron", "copper", "gold", "emerald", "tin"),
                coreOre.activationChancePerCandidate().keySet()
        );
        assertEquals(0.0008, coreOre.activationChance("tin"), 1.0e-12);
        assertEquals(coreOre, companionOre);

        assertTrue(benchmarkOre.enabled());
        assertEquals("core_ore_runtime", benchmarkOre.runtimeStatus());
        assertEquals(Set.of("tin"), benchmarkOre.activationChancePerCandidate().keySet());
        assertEquals("not_implemented", benchmarkOre.nativeGenerationSuppression());

        assertTrue(activeDiamond.enabled());
        assertEquals("core_runtime", activeDiamond.runtimeStatus());
        assertEquals("core_overworld", activeDiamond.nativeGenerationSuppression());
        assertEquals(0.06, activeDiamond.pipeActivationChance("kimberlite"), 1.0e-12);
        assertEquals(0.12, activeDiamond.structuralActivationChancePerCell(), 1.0e-12);
        assertSame(diamond, activeDiamond);
    }
}
