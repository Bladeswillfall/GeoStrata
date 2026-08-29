package com.geostrata.geology;

import org.junit.jupiter.api.Test;

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
    void companionPromotesDisabledSnapshotWithoutChangingTuning() {
        OreDepositExperiment.Snapshot disabled = experiment(false, 0.25);

        assertFalse(disabled.activated(false).enabled());
        OreDepositExperiment.Snapshot active = disabled.activated(true);
        assertTrue(active.enabled());
        assertEquals("experimental_runtime", active.runtimeStatus());
        assertEquals(0.25, active.activationChance("copper"), 1.0e-12);
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
