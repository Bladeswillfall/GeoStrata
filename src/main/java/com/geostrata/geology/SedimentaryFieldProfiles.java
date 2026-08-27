package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads metadata-only tuning for the diagnostic sedimentary stratigraphic field. */
public final class SedimentaryFieldProfiles {
    private static volatile Snapshot snapshot = Snapshot.unloaded();

    private SedimentaryFieldProfiles() {
    }

    public static Snapshot current() {
        return snapshot;
    }

    static void install(Snapshot loaded) {
        snapshot = loaded;
    }

    static Snapshot parse(SedimentarySuccessions.Snapshot successions, JsonObject profilesRoot) {
        if (!successions.loaded()) {
            throw new IllegalArgumentException("sedimentary successions must be loaded before field profiles");
        }
        Set<String> continuities = continuitySet(successions.successions());
        LinkedHashMap<String, SedimentaryStratigraphicField.Parameters> parameters = parseProfiles(
                profilesRoot,
                continuities
        );
        requireExactContinuityCoverage(parameters.keySet(), continuities);
        requireMinimumBedThickness(successions.successions(), parameters);
        return new Snapshot("metadata_only", Collections.unmodifiableMap(parameters));
    }

    private static Set<String> continuitySet(List<SedimentarySuccessions.Succession> successions) {
        Set<String> continuities = new HashSet<>();
        for (SedimentarySuccessions.Succession succession : successions) {
            continuities.add(succession.continuity());
        }
        return continuities;
    }

    private static LinkedHashMap<String, SedimentaryStratigraphicField.Parameters> parseProfiles(
            JsonObject root,
            Set<String> continuities
    ) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:sedimentary_field_profiles");
        requireString(root, "runtimeStatus", "metadata_only");

        JsonArray rawProfiles = requiredArray(root, "profiles");
        if (rawProfiles.size() == 0) {
            throw new IllegalArgumentException("sedimentary field profiles must not be empty");
        }

        LinkedHashMap<String, SedimentaryStratigraphicField.Parameters> parameters = new LinkedHashMap<>();
        for (JsonElement element : rawProfiles) {
            ParsedProfile profile = parseProfile(element, continuities);
            if (parameters.put(profile.continuity(), profile.parameters()) != null) {
                throw new IllegalArgumentException(
                        "duplicate sedimentary field continuity profile: " + profile.continuity()
                );
            }
        }
        return parameters;
    }

    private static ParsedProfile parseProfile(JsonElement element, Set<String> continuities) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("sedimentary field profile entry must be an object");
        }
        JsonObject profile = element.getAsJsonObject();
        String continuity = requireString(profile, "continuity");
        if (!continuities.contains(continuity)) {
            throw new IllegalArgumentException("unused sedimentary field continuity profile: " + continuity);
        }
        return new ParsedProfile(continuity, parseParameters(continuity, profile));
    }

    private static SedimentaryStratigraphicField.Parameters parseParameters(
            String continuity,
            JsonObject profile
    ) {
        double cycle = requireDouble(profile, "cycleThicknessBlocks");
        double maxDip = requireDouble(profile, "maxDip");
        double warpAmplitude = requireDouble(profile, "warpAmplitudeBlocks");
        double warpWavelength = requireDouble(profile, "warpWavelengthBlocks");

        requireCycleRange(continuity, cycle);
        requireDipRange(continuity, maxDip);
        requireWarpAmplitudeRange(continuity, warpAmplitude, cycle);
        requireWarpWavelengthRange(continuity, warpWavelength, cycle);
        return new SedimentaryStratigraphicField.Parameters(cycle, maxDip, warpAmplitude, warpWavelength);
    }

    private static void requireCycleRange(String continuity, double cycle) {
        if (cycle < 8.0 || cycle > 256.0) {
            throw new IllegalArgumentException(continuity + " cycle thickness must be within 8..256 blocks");
        }
    }

    private static void requireDipRange(String continuity, double maxDip) {
        if (maxDip < 0.0 || maxDip > 0.35) {
            throw new IllegalArgumentException(continuity + " max dip must be within 0..0.35");
        }
    }

    private static void requireWarpAmplitudeRange(String continuity, double warpAmplitude, double cycle) {
        if (warpAmplitude < 0.0 || warpAmplitude > cycle * 0.25) {
            throw new IllegalArgumentException(continuity + " warp amplitude must be within 0..25% of cycle thickness");
        }
    }

    private static void requireWarpWavelengthRange(String continuity, double warpWavelength, double cycle) {
        if (warpWavelength < cycle * 2.0 || warpWavelength > 2048.0) {
            throw new IllegalArgumentException(
                    continuity + " warp wavelength must be at least two cycles and at most 2048 blocks"
            );
        }
    }

    private static void requireExactContinuityCoverage(Set<String> provided, Set<String> required) {
        if (provided.equals(required)) {
            return;
        }
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(provided);
        Set<String> extra = new HashSet<>(provided);
        extra.removeAll(required);
        throw new IllegalArgumentException(
                "sedimentary field profiles must cover succession continuities exactly; missing="
                        + missing + ", extra=" + extra
        );
    }

    private static void requireMinimumBedThickness(
            List<SedimentarySuccessions.Succession> successions,
            Map<String, SedimentaryStratigraphicField.Parameters> parameters
    ) {
        for (SedimentarySuccessions.Succession succession : successions) {
            double thinnest = thinnestBedBlocks(succession, parameters.get(succession.continuity()));
            if (thinnest < 2.0) {
                throw new IllegalArgumentException(
                        succession.id() + " would compress its thinnest diagnostic bed below two blocks: " + thinnest
                );
            }
        }
    }

    private static double thinnestBedBlocks(
            SedimentarySuccessions.Succession succession,
            SedimentaryStratigraphicField.Parameters profile
    ) {
        double total = succession.beds().stream()
                .mapToDouble(SedimentarySuccessions.Bed::relativeThickness)
                .sum();
        return succession.beds().stream()
                .mapToDouble(bed -> bed.relativeThickness() / total * profile.cycleThicknessBlocks())
                .min()
                .orElseThrow();
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return element.getAsJsonArray();
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
        return element.getAsString();
    }

    private static void requireString(JsonObject object, String key, String expected) {
        String value = requireString(object, key);
        if (!value.equals(expected)) {
            throw new IllegalArgumentException(key + " must be " + expected + ", found " + value);
        }
    }

    private static double requireDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be numeric");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return value;
    }

    private record ParsedProfile(
            String continuity,
            SedimentaryStratigraphicField.Parameters parameters
    ) {
    }

    public record Snapshot(
            String runtimeStatus,
            Map<String, SedimentaryStratigraphicField.Parameters> parametersByContinuity
    ) {
        private static Snapshot unloaded() {
            return new Snapshot("unloaded", Map.of());
        }

        public boolean loaded() {
            return !parametersByContinuity.isEmpty();
        }

        public SedimentaryStratigraphicField.Parameters parametersFor(String continuity) {
            SedimentaryStratigraphicField.Parameters parameters = parametersByContinuity.get(continuity);
            if (parameters == null) {
                throw new IllegalArgumentException("unknown sedimentary field continuity: " + continuity);
            }
            return parameters;
        }
    }
}
