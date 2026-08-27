package com.geostrata.geology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Validates the optional companion activation marker and promotes a loaded experiment snapshot. */
final class CorrelatedExperimentActivation {
    private CorrelatedExperimentActivation() {
    }

    static CorrelatedSedimentaryExperiment.Snapshot apply(
            CorrelatedSedimentaryExperiment.Snapshot base,
            JsonObject marker
    ) {
        requireInt(marker, "schemaVersion", 1);
        requireString(marker, "model", "geostrata:correlated_sedimentary_activation");
        requireString(marker, "experiment", "geostrata:correlated_sedimentary_experiment");

        if (!base.loaded()) {
            throw new IllegalArgumentException("correlated experiment must be loaded before activation");
        }
        if (base.enabled()) {
            if (!"experimental_runtime".equals(base.runtimeStatus())) {
                throw new IllegalArgumentException("enabled correlated experiment must use experimental_runtime");
            }
            return base;
        }
        if (!"metadata_only".equals(base.runtimeStatus())) {
            throw new IllegalArgumentException("disabled correlated experiment must use metadata_only before activation");
        }

        return new CorrelatedSedimentaryExperiment.Snapshot(
                "experimental_runtime",
                true,
                base.targetSuccessionIds(),
                base.allowedProvinces(),
                base.supersededLithologies(),
                base.minimumBoundaryDistanceBlocks(),
                base.registrationBiomeTag(),
                base.hostBlockTag(),
                base.verticalWindow()
        );
    }

    private static void requireInt(JsonObject object, String key, int expected) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        double value = element.getAsDouble();
        int actual = element.getAsInt();
        if (!Double.isFinite(value) || value != actual || actual != expected) {
            throw new IllegalArgumentException(key + " must be " + expected);
        }
    }

    private static void requireString(JsonObject object, String key, String expected) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String actual = element.getAsString();
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(key + " must be " + expected + ", found " + actual);
        }
    }
}
