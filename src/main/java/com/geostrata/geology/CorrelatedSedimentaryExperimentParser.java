package com.geostrata.geology;

import com.geostrata.GeoStrata;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parses and cross-validates the correlated sedimentary experiment's server-data contract. */
final class CorrelatedSedimentaryExperimentParser {
    private CorrelatedSedimentaryExperimentParser() {
    }

    static CorrelatedSedimentaryExperiment.Snapshot parse(
            JsonObject experiment,
            JsonObject successionsRoot,
            JsonObject lithologiesRoot,
            JsonObject profilesRoot
    ) {
        ActivationState activation = parseActivation(experiment);
        Map<String, SuccessionReference> successions = parseSuccessions(successionsRoot);
        Map<String, String> rockClasses = parseRockClasses(lithologiesRoot);
        ProfileReference profiles = parseProfileReference(profilesRoot);
        TargetReference target = parseTarget(experiment, successions);
        Set<GeologyProvince> allowedProvinces = parseAllowedProvinces(experiment, target, profiles.provinceIds());
        Set<String> superseded = parseSuperseded(experiment, target.lithologies(), rockClasses);
        int boundaryDistance = parseBoundaryDistance(experiment, profiles.blendWidth());

        return new CorrelatedSedimentaryExperiment.Snapshot(
                activation.runtimeStatus(),
                activation.enabled(),
                immutableCopy(target.ids()),
                immutableCopy(allowedProvinces),
                immutableCopy(superseded),
                boundaryDistance,
                requireOwnedIdentifier(experiment, "registrationBiomeTag"),
                requireOwnedIdentifier(experiment, "hostBlockTag"),
                parseVerticalWindow(experiment)
        );
    }

    private static ActivationState parseActivation(JsonObject experiment) {
        requireInt(experiment, "schemaVersion", 1);
        requireString(experiment, "model", "geostrata:correlated_sedimentary_experiment");
        boolean enabled = requireBoolean(experiment, "enabled");
        String runtimeStatus = requireString(experiment, "runtimeStatus");
        String expectedStatus = enabled ? "experimental_runtime" : "metadata_only";
        if (!runtimeStatus.equals(expectedStatus)) {
            throw new IllegalArgumentException(
                    "correlated experiment runtimeStatus must be " + expectedStatus + " when enabled=" + enabled
            );
        }
        return new ActivationState(enabled, runtimeStatus);
    }

    private static Map<String, SuccessionReference> parseSuccessions(JsonObject root) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:sedimentary_successions");
        Map<String, SuccessionReference> successions = new HashMap<>();
        for (JsonElement element : requiredArray(root, "successions")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("succession entry must be an object");
            }
            SuccessionReference reference = parseSuccession(element.getAsJsonObject());
            if (successions.put(reference.id(), reference) != null) {
                throw new IllegalArgumentException("duplicate succession id: " + reference.id());
            }
        }
        return successions;
    }

    private static SuccessionReference parseSuccession(JsonObject object) {
        String id = requireString(object, "id");
        String continuity = requireString(object, "continuity");
        Set<String> contexts = stringSet(requiredArray(object, "contexts"), id + " contexts");
        Set<String> lithologies = new LinkedHashSet<>();
        for (JsonElement rawBed : requiredArray(object, "beds")) {
            if (!rawBed.isJsonObject()) {
                throw new IllegalArgumentException(id + " bed must be an object");
            }
            lithologies.add(requireString(rawBed.getAsJsonObject(), "lithology"));
        }
        return new SuccessionReference(id, continuity, contexts, lithologies);
    }

    private static Map<String, String> parseRockClasses(JsonObject root) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:lithology_catalog");
        Map<String, String> rockClasses = new HashMap<>();
        for (JsonElement element : requiredArray(root, "lithologies")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("lithology entry must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            String id = requireString(object, "id");
            if (rockClasses.put(id, requireString(object, "rockClass")) != null) {
                throw new IllegalArgumentException("duplicate lithology id: " + id);
            }
        }
        return rockClasses;
    }

    private static ProfileReference parseProfileReference(JsonObject root) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:province_profiles");
        int blendWidth = requireInt(root, "blendWidthBlocks");
        Set<String> provinceIds = new HashSet<>();
        for (JsonElement element : requiredArray(root, "profiles")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("province profile entry must be an object");
            }
            provinceIds.add(requireString(element.getAsJsonObject(), "province"));
        }
        return new ProfileReference(blendWidth, provinceIds);
    }

    private static TargetReference parseTarget(
            JsonObject experiment,
            Map<String, SuccessionReference> successions
    ) {
        Set<String> targetIds = stringSet(requiredArray(experiment, "targetSuccessionIds"), "targetSuccessionIds");
        if (targetIds.size() != 1) {
            throw new IllegalArgumentException("first correlated experiment must target exactly one succession");
        }
        String targetId = targetIds.iterator().next();
        SuccessionReference reference = successions.get(targetId);
        if (reference == null) {
            throw new IllegalArgumentException("unknown target succession: " + targetId);
        }
        if (!reference.continuity().equals("regional")) {
            throw new IllegalArgumentException("initial correlated experiment target must be regional: " + targetId);
        }
        return new TargetReference(targetIds, reference.lithologies(), reference.contexts());
    }

    private static Set<GeologyProvince> parseAllowedProvinces(
            JsonObject experiment,
            TargetReference target,
            Set<String> profileProvinceIds
    ) {
        Set<GeologyProvince> allowed = new LinkedHashSet<>();
        for (String provinceId : stringSet(requiredArray(experiment, "allowedProvinces"), "allowedProvinces")) {
            if (!profileProvinceIds.contains(provinceId)) {
                throw new IllegalArgumentException("allowed province has no live profile: " + provinceId);
            }
            if (!target.contexts().contains(provinceId)) {
                throw new IllegalArgumentException("allowed province is not a target succession context: " + provinceId);
            }
            allowed.add(provinceById(provinceId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown geological province: " + provinceId)));
        }
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("allowedProvinces must not be empty");
        }
        return allowed;
    }

    private static Set<String> parseSuperseded(
            JsonObject experiment,
            Set<String> targetLithologies,
            Map<String, String> rockClasses
    ) {
        Set<String> superseded = stringSet(
                requiredArray(experiment, "supersededLithologies"),
                "supersededLithologies"
        );
        if (!superseded.equals(targetLithologies)) {
            throw new IllegalArgumentException(
                    "supersededLithologies must exactly match target succession lithologies; expected="
                            + targetLithologies + ", actual=" + superseded
            );
        }
        for (String lithology : superseded) {
            if (!"sedimentary".equals(rockClasses.get(lithology))) {
                throw new IllegalArgumentException("experiment may only supersede sedimentary lithologies: " + lithology);
            }
        }
        return superseded;
    }

    private static int parseBoundaryDistance(JsonObject experiment, int blendWidth) {
        int minimum = requireInt(experiment, "minimumBoundaryDistanceBlocks");
        if (minimum < 0 || minimum > blendWidth) {
            throw new IllegalArgumentException("minimumBoundaryDistanceBlocks must be within 0.." + blendWidth);
        }
        return minimum;
    }

    private static CorrelatedSedimentaryExperiment.VerticalWindow parseVerticalWindow(JsonObject experiment) {
        JsonObject vertical = requiredObject(experiment, "verticalWindow");
        requireString(vertical, "anchor", "sea_level");
        int minOffset = requireInt(vertical, "minOffsetBlocks");
        int maxOffset = requireInt(vertical, "maxOffsetBlocks");
        if (minOffset < -256 || maxOffset > 256 || minOffset >= maxOffset) {
            throw new IllegalArgumentException("vertical offsets must be ordered within -256..256 blocks");
        }
        int span = maxOffset - minOffset;
        if (span < 32 || span > 256) {
            throw new IllegalArgumentException("vertical experiment span must be within 32..256 blocks");
        }
        return new CorrelatedSedimentaryExperiment.VerticalWindow(minOffset, maxOffset);
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static Set<String> stringSet(JsonArray array, String description) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(description + " entries must be strings");
            }
            String value = element.getAsString();
            if (value.isBlank()) {
                throw new IllegalArgumentException(description + " entries must not be blank");
            }
            if (!values.add(value)) {
                throw new IllegalArgumentException(description + " contains duplicate value: " + value);
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
        int value = requireInt(object, key);
        if (value != expected) {
            throw new IllegalArgumentException(key + " must be " + expected + ", found " + value);
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
        if (!value.equals(expected)) {
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

    private record ActivationState(boolean enabled, String runtimeStatus) {
    }

    private record SuccessionReference(String id, String continuity, Set<String> contexts, Set<String> lithologies) {
    }

    private record ProfileReference(int blendWidth, Set<String> provinceIds) {
    }

    private record TargetReference(Set<String> ids, Set<String> lithologies, Set<String> contexts) {
    }
}
