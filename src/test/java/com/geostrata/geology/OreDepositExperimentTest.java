package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreDepositExperimentTest {
    @Test
    void activationRollIsStableAndThresholdedByExperimentTuning() {
        OreDepositCandidatePlanner.Proposal proposal = proposal();

        assertEquals(0.8735449029866489, OreDepositExperiment.activationRoll(8675309L, proposal), 1.0e-16);
        assertTrue(OreDepositExperiment.active(8675309L, proposal, experiment(true, 0.90)));
        assertFalse(OreDepositExperiment.active(8675309L, proposal, experiment(true, 0.80)));
        assertFalse(OreDepositExperiment.active(8675309L, proposal, experiment(false, 1.0)));
    }

    @Test
    void companionActivationKeepsTheConfiguredChance() {
        OreDepositExperiment.Snapshot configured = experiment(false, 0.36);
        OreDepositExperiment.Snapshot activated = configured.activated(true);

        assertTrue(activated.enabled());
        assertEquals("experimental_runtime", activated.runtimeStatus());
        assertEquals("experimental_companion_overworld", activated.nativeGenerationSuppression());
        assertEquals(0.36, activated.activationChance("copper"));
    }

    @Test
    void companionSuppressesOnlyValidatedCommonVanillaOres() throws IOException {
        String source = Files.readString(Path.of(
                "experiment-companion/src/main/java/com/geostrata/experiment/CorrelatedExperimentCompanion.java"
        ));

        assertTrue(source.contains("ORE_COAL_UPPER"));
        assertTrue(source.contains("ORE_IRON_UPPER"));
        assertTrue(source.contains("ORE_COPPER"));
        assertFalse(source.contains("ORE_GOLD"));
        assertFalse(source.contains("ORE_EMERALD"));
        assertFalse(source.contains("ORE_DIAMOND"));
    }

    @Test
    void unknownMaterialAndNullInputsDoNotSilentlyActivate() {
        OreDepositExperiment.Snapshot experiment = experiment(true, 1.0);
        OreDepositCandidatePlanner.Proposal gold = new OreDepositCandidatePlanner.Proposal(
                "gold", "vein", -1, 0, 2, -48, 20, 96
        );

        assertFalse(OreDepositExperiment.active(8675309L, gold, experiment));
        assertThrows(IllegalArgumentException.class, () -> OreDepositExperiment.active(1L, null, experiment));
        assertThrows(IllegalArgumentException.class, () -> OreDepositExperiment.active(1L, proposal(), null));
    }

    private static OreDepositExperiment.Snapshot experiment(boolean enabled, double chance) {
        return new OreDepositExperiment.Snapshot(
                "experimental_opt_in",
                enabled,
                "chunk_local_valid_host_clipping",
                "not_implemented",
                Map.of("copper", chance)
        );
    }

    private static OreDepositCandidatePlanner.Proposal proposal() {
        return new OreDepositCandidatePlanner.Proposal(
                "copper", "vein", -1, 0, 2, -48, 20, 96
        );
    }
}
