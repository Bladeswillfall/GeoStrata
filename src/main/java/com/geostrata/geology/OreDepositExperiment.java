package com.geostrata.geology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Activation boundary for core common ores and companion-only rare ore experiments. */
public final class OreDepositExperiment {
    private static final long ACTIVATION_SALT = 0xDB4F0B9175AE2165L;
    private static final List<String> CORE_COMMON_MATERIALS = List.of("coal", "iron", "copper");
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
        requireInt(root, "schemaVersion", 2);
        requireString(root, "model", "geostrata:ore_deposit_experiment");
        requireString(root, "runtimeStatus", "experimental_opt_in");
        boolean enabled = requireBoolean(root, "enabled");
        requireString(root, "placementMode", "chunk_local_valid_host_clipping");
        requireString(root, "nativeGenerationSuppression", "not_implemented");
        double activationScale = requireDouble(root, "activationScale");
        if (activationScale < 0.0) {
            throw new IllegalArgumentException("activationScale must not be negative");
        }

        LinkedHashMap<String, Double> chances = new LinkedHashMap<>();
        for (OreOccurrenceCatalog.Occurrence occurrence : occurrences.occurrences()) {
            chances.put(
                    occurrence.id(),
                    Math.min(1.0, occurrence.generation().activationChance() * activationScale)
            );
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
        return active(worldSeed, proposal, 1.0, current());
    }

    /** Applies data-driven geological/environmental affinity to the base material chance. */
    public static boolean active(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            double affinityMultiplier
    ) {
        return active(worldSeed, proposal, affinityMultiplier, current());
    }

    /** Compatibility overload for callers that only have a candidate cell. */
    public static boolean active(long worldSeed, String material, int cellX, int cellY, int cellZ) {
        return active(worldSeed, material, cellX, cellY, cellZ, 1.0, current());
    }

    static boolean active(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            Snapshot experiment
    ) {
        return active(worldSeed, proposal, 1.0, experiment);
    }

    static boolean active(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            double affinityMultiplier,
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
                affinityMultiplier,
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
        return active(worldSeed, material, cellX, cellY, cellZ, 1.0, experiment);
    }

    static boolean active(
            long worldSeed,
            String material,
            int cellX,
            int cellY,
            int cellZ,
            double affinityMultiplier,
            Snapshot experiment
    ) {
        if (material == null || experiment == null) {
            throw new IllegalArgumentException("ore material and experiment must not be null");
        }
        if (!Double.isFinite(affinityMultiplier) || affinityMultiplier < 0.0) {
            throw new IllegalArgumentException("ore affinity multiplier must be finite and non-negative");
        }
        if (!experiment.loaded() || !experiment.enabled()) {
            return false;
        }
        Double chance = experiment.activationChancePerCandidate().get(material);
        if (chance == null) {
            return false;
        }
        double adjustedChance = Math.min(1.0, chance * affinityMultiplier);
        return GeologyDeterminism.passesChance(
                adjustedChance,
                activationRoll(worldSeed, material, cellX, cellY, cellZ)
        );
    }

    /** Compatibility helper; runtime depth bias now lives in the occurrence generation LUT. */
    static double activationDepthMultiplier(OreDepositCandidatePlanner.Proposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("ore proposal must not be null");
        }
        OreOccurrenceCatalog.Occurrence occurrence = OreOccurrenceCatalog.current().byId().get(proposal.material());
        return occurrence == null ? 1.0 : occurrence.generation().depthMultiplier(proposal.anchorY());
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

        Snapshot activated(boolean companionLoaded, boolean coreCommonOwnershipEnabled) {
            if (companionLoaded) {
                if (coreCommonOwnershipEnabled) {
                    return new Snapshot(
                            "experimental_runtime",
                            true,
                            placementMode,
                            "core_common_overworld",
                            activationChancePerCandidate
                    );
                }
                LinkedHashMap<String, Double> rareChances = new LinkedHashMap<>(activationChancePerCandidate);
                CORE_COMMON_MATERIALS.forEach(rareChances::remove);
                return new Snapshot(
                        "experimental_runtime",
                        true,
                        placementMode,
                        "not_implemented",
                        rareChances
                );
            }
            if (!coreCommonOwnershipEnabled) {
                return this;
            }

            LinkedHashMap<String, Double> commonChances = new LinkedHashMap<>();
            for (String material : CORE_COMMON_MATERIALS) {
                Double chance = activationChancePerCandidate.get(material);
                if (chance == null) {
                    throw new IllegalArgumentException("core common ore runtime requires material " + material);
                }
                commonChances.put(material, chance);
            }
            return new Snapshot(
                    "core_common_runtime",
                    true,
                    placementMode,
                    "core_common_overworld",
                    commonChances
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
