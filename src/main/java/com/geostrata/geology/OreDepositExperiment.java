package com.geostrata.geology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Disabled-by-default tuning and activation boundary for real ore-deposit placement. */
public final class OreDepositExperiment {
    private static final long ACTIVATION_SALT = 0xDB4F0B9175AE2165L;
    private static final double COMPANION_VALIDATION_MULTIPLIER = 20.0;
    private static volatile Snapshot snapshot = Snapshot.unloaded();

    private OreDepositExperiment() {
    }

    public static Snapshot current() {
        return snapshot;
    }

    static void install(Snapshot loaded) {
        snapshot = loaded;
    }

    static Snapshot parse(JsonObject root, OreOccurrenceCatalog.Snapshot occurrences) {
        if (!occurrences.loaded()) {
            throw new IllegalArgumentException("ore occurrences must be loaded before the deposit experiment");
        }
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:ore_deposit_experiment");
        requireString(root, "runtimeStatus", "experimental_opt_in");
        boolean enabled = requireBoolean(root, "enabled");
        requireString(root, "placementMode", "chunk_local_valid_host_clipping");
        requireString(root, "nativeGenerationSuppression", "not_implemented");

        JsonObject rawChances = requiredObject(root, "activationChancePerCandidate");
        if (!rawChances.keySet().equals(occurrences.byId().keySet())) {
            throw new IllegalArgumentException(
                    "activationChancePerCandidate must define exactly the loaded ore materials"
            );
        }

        LinkedHashMap<String, Double> chances = new LinkedHashMap<>();
        for (OreOccurrenceCatalog.Occurrence occurrence : occurrences.occurrences()) {
            double chance = requireDouble(rawChances, occurrence.id());
            if (chance < 0.0 || chance > 1.0) {
                throw new IllegalArgumentException(
                        occurrence.id() + " activation chance must be between 0 and 1"
                );
            }
            chances.put(occurrence.id(), chance);
        }
        return new Snapshot(
                "experimental_opt_in",
                enabled,
                "chunk_local_valid_host_clipping",
                "not_implemented",
                Collections.unmodifiableMap(chances)
        );
    }

    public static boolean active(long worldSeed, OreDepositCandidatePlanner.Proposal proposal) {
        return active(worldSeed, proposal, current());
    }

    static boolean active(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            Snapshot experiment
    ) {
        if (proposal == null || experiment == null) {
            throw new IllegalArgumentException("ore proposal and experiment must not be null");
        }
        if (!experiment.loaded() || !experiment.enabled()) {
            return false;
        }
        Double chance = experiment.activationChancePerCandidate().get(proposal.material());
        if (chance == null) {
            return false;
        }
        return GeologyDeterminism.passesChance(chance, activationRoll(worldSeed, proposal));
    }

    static double activationRoll(long worldSeed, OreDepositCandidatePlanner.Proposal proposal) {
        long materialSalt = Integer.toUnsignedLong(proposal.material().hashCode());
        return GeologyDeterminism.unitRoll(
                worldSeed,
                proposal.cellX(),
                proposal.cellY(),
                proposal.cellZ(),
                ACTIVATION_SALT ^ materialSalt
        );
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

    private static double requireDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return value;
    }

    public record Snapshot(
            String runtimeStatus,
            boolean enabled,
            String placementMode,
            String nativeGenerationSuppression,
            Map<String, Double> activationChancePerCandidate
    ) {
        public Snapshot {
            activationChancePerCandidate = Map.copyOf(activationChancePerCandidate);
        }

        private static Snapshot unloaded() {
            return new Snapshot("unloaded", false, "none", "not_implemented", Map.of());
        }

        public boolean loaded() {
            return !activationChancePerCandidate.isEmpty();
        }

        Snapshot activated(boolean companionLoaded) {
            if (!companionLoaded || "experimental_runtime".equals(runtimeStatus)) {
                return this;
            }
            LinkedHashMap<String, Double> boosted = new LinkedHashMap<>();
            activationChancePerCandidate.forEach((material, chance) -> boosted.put(
                    material,
                    Math.min(1.0, chance * COMPANION_VALIDATION_MULTIPLIER)
            ));
            return new Snapshot(
                    "experimental_runtime",
                    true,
                    placementMode,
                    "experimental_companion_overworld",
                    boosted
            );
        }

        public double activationChance(String material) {
            Double chance = activationChancePerCandidate.get(material);
            if (chance == null) {
                throw new IllegalArgumentException("unknown ore activation material: " + material);
            }
            return chance;
        }
    }
}
