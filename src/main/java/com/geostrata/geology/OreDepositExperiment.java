package com.geostrata.geology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Disabled-by-default tuning and activation boundary for real ore-deposit placement. */
public final class OreDepositExperiment {
    private static final long ACTIVATION_SALT = 0xDB4F0B9175AE2165L;
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

    /** Checks activation before X/Z anchor, style and proposal construction work is needed. */
    public static boolean active(long worldSeed, String material, int cellX, int cellY, int cellZ) {
        return active(worldSeed, material, cellX, cellY, cellZ, current());
    }

    static boolean active(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            Snapshot experiment
    ) {
        if (proposal == null || experiment == null) {
            throw new IllegalArgumentException("ore proposal and experiment must not be null");
        }
        return active(
                worldSeed,
                proposal.material(),
                proposal.cellX(),
                proposal.cellY(),
                proposal.cellZ(),
                proposal.anchorY(),
                experiment
        );
    }

    static boolean active(
            long worldSeed,
            String material,
            int cellX,
            int cellY,
            int cellZ,
            Snapshot experiment
    ) {
        if (material == null || experiment == null) {
            throw new IllegalArgumentException("ore material and experiment must not be null");
        }
        int anchorY = OreDepositCandidatePlanner.anchorYForCell(worldSeed, cellX, cellY, cellZ, material);
        return active(worldSeed, material, cellX, cellY, cellZ, anchorY, experiment);
    }

    private static boolean active(
            long worldSeed,
            String material,
            int cellX,
            int cellY,
            int cellZ,
            int anchorY,
            Snapshot experiment
    ) {
        if (!experiment.loaded() || !experiment.enabled()) {
            return false;
        }
        Double chance = experiment.activationChancePerCandidate().get(material);
        if (chance == null) {
            return false;
        }
        double adjustedChance = Math.min(1.0, chance * activationDepthMultiplier(material, anchorY));
        return GeologyDeterminism.passesChance(
                adjustedChance,
                activationRoll(worldSeed, material, cellX, cellY, cellZ)
        );
    }

    /** Broad iron bias only; geology and valid host rock still decide whether an active body can place. */
    static double activationDepthMultiplier(OreDepositCandidatePlanner.Proposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("ore proposal must not be null");
        }
        return activationDepthMultiplier(proposal.material(), proposal.anchorY());
    }

    private static double activationDepthMultiplier(String material, int anchorY) {
        if (!"iron".equals(material)) {
            return 1.0;
        }
        if (anchorY < 0) {
            return 0.5;
        }
        if (anchorY < 64) {
            return 1.5;
        }
        if (anchorY < 128) {
            return 1.9;
        }
        return 1.0;
    }

    static double activationRoll(long worldSeed, OreDepositCandidatePlanner.Proposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("ore proposal must not be null");
        }
        return activationRoll(
                worldSeed,
                proposal.material(),
                proposal.cellX(),
                proposal.cellY(),
                proposal.cellZ()
        );
    }

    static double activationRoll(long worldSeed, String material, int cellX, int cellY, int cellZ) {
        if (material == null) {
            throw new IllegalArgumentException("ore material must not be null");
        }
        long materialSalt = Integer.toUnsignedLong(material.hashCode());
        return GeologyDeterminism.unitRoll(
                worldSeed,
                cellX,
                cellY,
                cellZ,
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
            return new Snapshot(
                    "experimental_runtime",
                    true,
                    placementMode,
                    "experimental_companion_overworld",
                    activationChancePerCandidate
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
