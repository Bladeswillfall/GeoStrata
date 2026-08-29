package com.geostrata.geology;

import com.geostrata.GeoStrata;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Validates the correlated experiment against already parsed geology snapshots. */
final class CorrelatedSedimentaryExperimentParser {
    private CorrelatedSedimentaryExperimentParser() {
    }

    static CorrelatedSedimentaryExperiment.Snapshot parse(
            JsonObject experiment,
            SedimentarySuccessions.Snapshot successions,
            LithologyCatalog.Snapshot lithologies,
            GeologyProvinceProfiles.Snapshot profiles
    ) {
        if (!successions.loaded() || !lithologies.loaded() || !profiles.loaded()) {
            throw new IllegalArgumentException("correlated experiment dependencies must be loaded first");
        }

        requireInt(experiment, "schemaVersion", 2);
        requireString(experiment, "model", "geostrata:correlated_sedimentary_experiment");
        requireString(experiment, "runtimeStatus", "metadata_only");
        if (requireBoolean(experiment, "enabled")) {
            throw new IllegalArgumentException("core correlated experiment must remain disabled");
        }

        Set<String> targetIds = stringSet(requiredArray(experiment, "targetSuccessionIds"), "targetSuccessionIds");
        Set<SedimentarySuccessions.Succession> targets = resolveTargets(targetIds, successions);
        Set<GeologyProvince> allowedProvinces = parseAllowedProvinces(experiment, targets, profiles);
        Set<String> supersededLithologies = parseSuperseded(experiment, targets, lithologies);
        int minimumBoundaryDistance = requireInt(experiment, "minimumBoundaryDistanceBlocks");
        if (minimumBoundaryDistance < 0 || minimumBoundaryDistance > profiles.blendWidthBlocks()) {
            throw new IllegalArgumentException(
                    "minimumBoundaryDistanceBlocks must be within 0.." + profiles.blendWidthBlocks()
            );
        }
        requireString(experiment, "verticalDomain", "dimension_bounds");

        return new CorrelatedSedimentaryExperiment.Snapshot(
                "metadata_only",
                false,
                immutableCopy(targetIds),
                immutableCopy(allowedProvinces),
                immutableCopy(supersededLithologies),
                minimumBoundaryDistance,
                requireOwnedIdentifier(experiment, "registrationBiomeTag"),
                requireOwnedIdentifier(experiment, "hostBlockTag"),
                CorrelatedSedimentaryExperiment.VerticalWindow.fullDimension()
        );
    }

    private static Set<SedimentarySuccessions.Succession> resolveTargets(
            Set<String> targetIds,
            SedimentarySuccessions.Snapshot successions
    ) {
        Set<SedimentarySuccessions.Succession> targets = new LinkedHashSet<>();
        for (String targetId : targetIds) {
            SedimentarySuccessions.Succession target = successions.byId().get(targetId);
            if (target == null) {
                throw new IllegalArgumentException("unknown target succession: " + targetId);
            }
            targets.add(target);
        }
        return targets;
    }

    private static Set<GeologyProvince> parseAllowedProvinces(
            JsonObject experiment,
            Set<SedimentarySuccessions.Succession> targets,
            GeologyProvinceProfiles.Snapshot profiles
    ) {
        Set<GeologyProvince> allowed = new LinkedHashSet<>();
        for (String provinceId : stringSet(requiredArray(experiment, "allowedProvinces"), "allowedProvinces")) {
            GeologyProvince province = provinceById(provinceId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown geological province: " + provinceId));
            if (!profiles.weights().containsKey(province)) {
                throw new IllegalArgumentException("allowed province has no live profile: " + provinceId);
            }
            if (targets.stream().noneMatch(target -> target.contexts().contains(province))) {
                throw new IllegalArgumentException("allowed province is not a target succession context: " + provinceId);
            }
            allowed.add(province);
        }
        return allowed;
    }

    private static Set<String> parseSuperseded(
            JsonObject experiment,
            Set<SedimentarySuccessions.Succession> targets,
            LithologyCatalog.Snapshot lithologies
    ) {
        Set<String> expected = new LinkedHashSet<>();
        for (SedimentarySuccessions.Succession target : targets) {
            for (SedimentarySuccessions.Bed bed : target.beds()) {
                expected.add(bed.lithology());
            }
        }

        Set<String> superseded = stringSet(
                requiredArray(experiment, "supersededLithologies"),
                "supersededLithologies"
        );
        if (!superseded.equals(expected)) {
            throw new IllegalArgumentException(
                    "supersededLithologies must exactly match target succession lithologies; expected="
                            + expected + ", actual=" + superseded
            );
        }
        for (String lithology : superseded) {
            if (!"sedimentary".equals(lithologies.require(lithology).rockClass())) {
                throw new IllegalArgumentException("experiment may only supersede sedimentary lithologies: " + lithology);
            }
        }
        return superseded;
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static Set<String> stringSet(JsonArray array, String description) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(description + " entries must be strings");
            }
            String value = element.getAsString();
            if (value.isBlank() || !values.add(value)) {
                throw new IllegalArgumentException(description + " entries must be unique and non-blank");
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException(description + " must not be empty");
        }
        return values;
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
        if (requireInt(object, key) != expected) {
            throw new IllegalArgumentException(key + " must be " + expected);
        }
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

    private static boolean requireBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static String requireOwnedIdentifier(JsonObject object, String key) {
        String value = requireString(object, key);
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(key + " must be a namespaced identifier");
        }
        if (!GeoStrata.MOD_ID.equals(value.substring(0, separator))) {
            throw new IllegalArgumentException(key + " must use the geostrata namespace");
        }
        return value;
    }

    private static Optional<GeologyProvince> provinceById(String id) {
        for (GeologyProvince province : GeologyProvince.values()) {
            if (province.id().equals(id)) {
                return Optional.of(province);
            }
        }
        return Optional.empty();
    }

    private static <T> Set<T> immutableCopy(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
