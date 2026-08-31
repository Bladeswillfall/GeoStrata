package com.geostrata.geology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/** Disabled-by-default activation boundary for GeoStrata's diamond geology prototype. */
public final class DiamondGeologyExperiment {
    private static volatile Snapshot snapshot = Snapshot.unloaded();

    private DiamondGeologyExperiment() {
    }

    public static Snapshot current() {
        return snapshot;
    }

    static void install(Snapshot loaded) {
        snapshot = loaded;
    }

    static Snapshot parse(JsonObject root) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:diamond_geology_experiment");
        requireString(root, "runtimeStatus", "experimental_opt_in");
        boolean enabled = requireBoolean(root, "enabled");
        requireString(root, "nativeGenerationSuppression", "not_implemented");

        JsonObject rawPipeChances = requiredObject(root, "pipeActivationChancePerCell");
        Map<String, Double> pipeChances = Map.of(
                "kimberlite", chance(rawPipeChances, "kimberlite"),
                "lamproite", chance(rawPipeChances, "lamproite")
        );
        if (!rawPipeChances.keySet().equals(pipeChances.keySet())) {
            throw new IllegalArgumentException("pipeActivationChancePerCell must define exactly kimberlite and lamproite");
        }

        double structuralChance = chance(root, "structuralActivationChancePerCell");
        return new Snapshot(
                "experimental_opt_in",
                enabled,
                "not_implemented",
                pipeChances,
                structuralChance
        );
    }

    private static double chance(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(key + " must be finite and between 0 and 1");
        }
        return value;
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static String requireString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private static void requireString(JsonObject object, String key, String expected) {
        String value = requireString(object, key);
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(key + " must be " + expected + ", found " + value);
        }
    }

    private static int requireInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        double value = element.getAsDouble();
        int intValue = element.getAsInt();
        if (!Double.isFinite(value) || value != intValue) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return intValue;
    }

    private static void requireInt(JsonObject object, String key, int expected) {
        int value = requireInt(object, key);
        if (value != expected) {
            throw new IllegalArgumentException(key + " must be " + expected + ", found " + value);
        }
    }

    private static boolean requireBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    public record Snapshot(
            String runtimeStatus,
            boolean enabled,
            String nativeGenerationSuppression,
            Map<String, Double> pipeActivationChancePerCell,
            double structuralActivationChancePerCell
    ) {
        public Snapshot {
            pipeActivationChancePerCell = Map.copyOf(pipeActivationChancePerCell);
        }

        private static Snapshot unloaded() {
            return new Snapshot("unloaded", false, "not_implemented", Map.of(), 0.0);
        }

        public boolean loaded() {
            return !pipeActivationChancePerCell.isEmpty();
        }

        Snapshot activated(boolean companionLoaded) {
            if (!companionLoaded) {
                return this;
            }
            return new Snapshot(
                    "experimental_runtime",
                    true,
                    nativeGenerationSuppression,
                    pipeActivationChancePerCell,
                    structuralActivationChancePerCell
            );
        }

        public double pipeActivationChance(String kind) {
            Double chance = pipeActivationChancePerCell.get(kind);
            if (chance == null) {
                throw new IllegalArgumentException("unknown diamond pipe kind: " + kind);
            }
            return chance;
        }
    }
}
