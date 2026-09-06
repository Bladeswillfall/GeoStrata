package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreDepositExperimentTest {
    @Test
    void activationRollIsStableAndThresholdedByExperimentTuning() {
        OreDepositCandidatePlanner.Proposal proposal = proposal();

        assertEquals(0.8735449029866489, OreDepositExperiment.activationRoll(8675309L, proposal), 1.0e-16);
        assertEquals(
                OreDepositExperiment.activationRoll(8675309L, proposal),
                OreDepositExperiment.activationRoll(8675309L, "copper", -1, 0, 2),
                0.0
        );
        assertTrue(OreDepositExperiment.active(8675309L, proposal, experiment(true, 0.90)));
        assertTrue(OreDepositExperiment.active(8675309L, "copper", -1, 0, 2, experiment(true, 0.90)));
        assertFalse(OreDepositExperiment.active(8675309L, proposal, experiment(true, 0.80)));
        assertFalse(OreDepositExperiment.active(8675309L, "copper", -1, 0, 2, experiment(true, 0.80)));
        assertFalse(OreDepositExperiment.active(8675309L, proposal, experiment(false, 1.0)));
    }

    @Test
    void affinityMultiplierTiltsChanceWithoutChangingDeterministicRoll() {
        OreDepositCandidatePlanner.Proposal proposal = proposal();
        OreDepositExperiment.Snapshot experiment = experiment(true, 0.50);

        assertFalse(OreDepositExperiment.active(8675309L, proposal, 1.0, experiment));
        assertTrue(OreDepositExperiment.active(8675309L, proposal, 2.0, experiment));
        assertFalse(OreDepositExperiment.active(8675309L, proposal, 0.0, experiment));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreDepositExperiment.active(8675309L, proposal, -1.0, experiment)
        );
    }

    @Test
    void companionActivationKeepsTheConfiguredChance() {
        OreDepositExperiment.Snapshot configured = experiment(false, 0.36);
        OreDepositExperiment.Snapshot activated = configured.activated(true, true);

        assertTrue(activated.enabled());
        assertEquals("experimental_runtime", activated.runtimeStatus());
        assertEquals("core_common_overworld", activated.nativeGenerationSuppression());
        assertEquals(0.36, activated.activationChance("copper"));
    }

    @Test
    void coreRuntimeActivatesAllOwnedVanillaOverworldMaterials() {
        OreDepositExperiment.Snapshot configured = new OreDepositExperiment.Snapshot(
                "experimental_opt_in",
                false,
                "chunk_local_valid_host_clipping",
                "not_implemented",
                Map.of("coal", 0.8, "iron", 0.5, "copper", 0.36, "gold", 0.8, "emerald", 0.08)
        );

        OreDepositExperiment.Snapshot core = configured.activated(false, true);

        assertTrue(core.enabled());
        assertEquals("core_common_runtime", core.runtimeStatus());
        assertEquals("core_common_overworld", core.nativeGenerationSuppression());
        assertEquals(Set.of("coal", "iron", "copper", "gold", "emerald"), core.activationChancePerCandidate().keySet());
        assertEquals(0.8, core.activationChance("gold"));
        assertEquals(0.08, core.activationChance("emerald"));
        assertSame(configured, configured.activated(false, false));
    }

    @Test
    void coreSuppressesAllOwnedVanillaOres() throws IOException {
        String core = Files.readString(Path.of(
                "src/main/java/com/geostrata/platform/fabric/FabricWorldgenRegistration.java"
        ));
        String companion = Files.readString(Path.of(
                "experiment-companion/src/main/java/com/geostrata/experiment/CorrelatedExperimentCompanion.java"
        ));
        String workflow = Files.readString(Path.of(".github/workflows/ore-benchmark.yml"));

        assertTrue(core.contains("ORE_COAL_UPPER"));
        assertTrue(core.contains("ORE_IRON_UPPER"));
        assertTrue(core.contains("ORE_COPPER"));
        assertTrue(core.contains("ORE_GOLD"));
        assertTrue(core.contains("ORE_EMERALD"));
        assertTrue(core.contains("ORE_DIAMOND"));
        assertTrue(core.contains("ORE_DIAMOND_MEDIUM"));
        assertTrue(core.contains("ORE_DIAMOND_LARGE"));
        assertTrue(core.contains("ORE_DIAMOND_BURIED"));
        assertFalse(companion.contains("ORE_COAL_UPPER"));
        assertFalse(companion.contains("ORE_IRON_UPPER"));
        assertFalse(companion.contains("ORE_COPPER"));
        assertTrue(companion.contains("BENCHMARK_DIAMOND_ORES"));
        assertTrue(companion.contains("GEOSTRATA_BENCHMARK_SUPPRESS_VANILLA_DIAMOND"));
        assertTrue(core.contains("GEOSTRATA_BENCHMARK_DISABLE_CORE_COMMON_ORE_OWNERSHIP"));
        assertTrue(workflow.contains("GEOSTRATA_BENCHMARK_DISABLE_CORE_COMMON_ORE_OWNERSHIP"));
        assertTrue(workflow.contains("export GEOSTRATA_BENCHMARK_SUPPRESS_VANILLA_DIAMOND=true"));
        assertFalse(core.contains("ORE_REDSTONE"));
        assertFalse(core.contains("ORE_LAPIS"));
    }

    @Test
    void unknownMaterialAndNullInputsDoNotSilentlyActivate() {
        OreDepositExperiment.Snapshot experiment = experiment(true, 1.0);
        OreDepositCandidatePlanner.Proposal gold = new OreDepositCandidatePlanner.Proposal(
                "gold", "vein", -1, 0, 2, -48, 20, 96
        );

        assertFalse(OreDepositExperiment.active(8675309L, gold, experiment));
        assertFalse(OreDepositExperiment.active(8675309L, "gold", -1, 0, 2, experiment));
        assertThrows(IllegalArgumentException.class, () -> OreDepositExperiment.active(1L, null, experiment));
        assertThrows(IllegalArgumentException.class, () -> OreDepositExperiment.active(1L, proposal(), null));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreDepositExperiment.active(1L, null, -1, 0, 2, experiment)
        );
    }

    private static OreDepositExperiment.Snapshot experiment(boolean enabled, double chance) {
        return experiment("copper", enabled, chance);
    }

    private static OreDepositExperiment.Snapshot experiment(String material, boolean enabled, double chance) {
        return new OreDepositExperiment.Snapshot(
                "experimental_opt_in",
                enabled,
                "chunk_local_valid_host_clipping",
                "not_implemented",
                Map.of(material, chance)
        );
    }

    private static OreDepositCandidatePlanner.Proposal proposal() {
        return new OreDepositCandidatePlanner.Proposal(
                "copper", "vein", -1, 0, 2, -48, 20, 96
        );
    }
}
