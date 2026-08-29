package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiamondGeologyExperimentTest {
    @Test
    void parsesDisabledDiamondExperiment() {
        DiamondGeologyExperiment.Snapshot snapshot = DiamondGeologyExperiment.parse(valid());

        assertFalse(snapshot.enabled());
        assertEquals("not_implemented", snapshot.nativeGenerationSuppression());
        assertEquals(0.06, snapshot.pipeActivationChance("kimberlite"), 1.0e-12);
        assertEquals(0.02, snapshot.pipeActivationChance("lamproite"), 1.0e-12);
        assertEquals(0.12, snapshot.structuralActivationChancePerCell(), 1.0e-12);
    }

    @Test
    void companionPromotesDisabledSnapshotWithoutChangingTuning() {
        DiamondGeologyExperiment.Snapshot disabled = DiamondGeologyExperiment.parse(valid());

        assertFalse(disabled.activated(false).enabled());
        DiamondGeologyExperiment.Snapshot active = disabled.activated(true);
        assertTrue(active.enabled());
        assertEquals("experimental_runtime", active.runtimeStatus());
        assertEquals(0.06, active.pipeActivationChance("kimberlite"), 1.0e-12);
        assertEquals(0.12, active.structuralActivationChancePerCell(), 1.0e-12);
    }

    @Test
    void rejectsUnknownPipeKinds() {
        JsonObject root = valid();
        root.getAsJsonObject("pipeActivationChancePerCell").addProperty("magic_pipe", 0.1);
        assertThrows(IllegalArgumentException.class, () -> DiamondGeologyExperiment.parse(root));
    }

    @Test
    void rejectsOutOfRangeChance() {
        JsonObject root = valid();
        root.addProperty("structuralActivationChancePerCell", 1.1);
        assertThrows(IllegalArgumentException.class, () -> DiamondGeologyExperiment.parse(root));
    }

    private static JsonObject valid() {
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:diamond_geology_experiment",
                  "runtimeStatus": "experimental_opt_in",
                  "enabled": false,
                  "nativeGenerationSuppression": "not_implemented",
                  "pipeActivationChancePerCell": {
                    "kimberlite": 0.06,
                    "lamproite": 0.02
                  },
                  "structuralActivationChancePerCell": 0.12
                }
                """).getAsJsonObject();
    }
}
