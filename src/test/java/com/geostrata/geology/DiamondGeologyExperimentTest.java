package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiamondGeologyExperimentTest {
    @Test
    void parsesCoreDiamondRuntime() {
        DiamondGeologyExperiment.Snapshot snapshot = DiamondGeologyExperiment.parse(valid());

        assertTrue(snapshot.enabled());
        assertEquals("core_runtime", snapshot.runtimeStatus());
        assertEquals("core_overworld", snapshot.nativeGenerationSuppression());
        assertEquals(0.06, snapshot.pipeActivationChance("kimberlite"), 1.0e-12);
        assertEquals(0.02, snapshot.pipeActivationChance("lamproite"), 1.0e-12);
        assertEquals(0.12, snapshot.structuralActivationChancePerCell(), 1.0e-12);
    }

    @Test
    void companionNoLongerControlsDiamondActivation() {
        DiamondGeologyExperiment.Snapshot snapshot = DiamondGeologyExperiment.parse(valid());
        assertSame(snapshot, snapshot.activated(false));
        assertSame(snapshot, snapshot.activated(true));
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
                  "runtimeStatus": "core_runtime",
                  "enabled": true,
                  "nativeGenerationSuppression": "core_overworld",
                  "pipeActivationChancePerCell": {
                    "kimberlite": 0.06,
                    "lamproite": 0.02
                  },
                  "structuralActivationChancePerCell": 0.12
                }
                """).getAsJsonObject();
    }
}
