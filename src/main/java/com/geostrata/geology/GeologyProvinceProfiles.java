package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads GeoStrata's province/lithology suitability model from server data.
 *
 * <p>The bundled JSON is therefore datapack-overridable without making the
 * worldgen implementation depend on a particular terrain/content mod. This
 * service only exposes the data in the current stage; worldgen consumers are
 * introduced separately.</p>
 */
public final class GeologyProvinceProfiles {
    private static volatile Snapshot snapshot = Snapshot.unloaded();

    private GeologyProvinceProfiles() {
    }

    public static Snapshot current() {
        return snapshot;
    }

    static void install(Snapshot loaded) {
        snapshot = loaded;
    }

    static Snapshot parse(LithologyCatalog.Snapshot catalog, JsonObject profilesRoot) {
        if (!catalog.loaded()) {
            throw new IllegalArgumentException("lithology catalog must be loaded before province profiles");
        }
        Set<String> lithologyIds = catalog.byId().keySet();
        ProfileHeader header = parseHeader(profilesRoot);
        EnumMap<GeologyProvince, Map<String, Double>> weights = parseProfiles(profilesRoot, lithologyIds);
        requireCompleteProvinceCoverage(weights);

        return new Snapshot(
                header.runtimeStatus(),
                header.blendWidthBlocks(),
                Collections.unmodifiableSet(new HashSet<>(lithologyIds)),
                Collections.unmodifiableMap(weights)
        );
    }

    private static ProfileHeader parseHeader(JsonObject profilesRoot) {
        requireInt(profilesRoot, "schemaVersion", 1);
        requireString(profilesRoot, "model", "geostrata:province_profiles");
        requireString(profilesRoot, "runtimeStatus", "runtime_bias");

        int blendWidth = requireInt(profilesRoot, "blendWidthBlocks");
        if (blendWidth < 1 || blendWidth > GeologyProvinceSampler.CELL_SIZE / 2) {
            throw new IllegalArgumentException("blendWidthBlocks must be between 1 and half the province cell size");
        }
        return new ProfileHeader("runtime_bias", blendWidth);
    }

    private static EnumMap<GeologyProvince, Map<String, Double>> parseProfiles(
            JsonObject profilesRoot,
            Set<String> lithologyIds
    ) {
        EnumMap<GeologyProvince, Map<String, Double>> weights = new EnumMap<>(GeologyProvince.class);
        for (JsonElement element : requiredArray(profilesRoot, "profiles")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("province profile entry must be an object");
            }
            ParsedProvinceProfile parsed = parseProfile(element.getAsJsonObject(), lithologyIds);
            if (weights.put(parsed.province(), parsed.weights()) != null) {
                throw new IllegalArgumentException("duplicate geological province profile: " + parsed.province().id());
            }
        }
        return weights;
    }

    private static ParsedProvinceProfile parseProfile(JsonObject profile, Set<String> lithologyIds) {
        String provinceId = requireString(profile, "province");
        GeologyProvince province = provinceById(provinceId)
                .orElseThrow(() -> new IllegalArgumentException("unknown geological province: " + provinceId));
        JsonObject rawWeights = requiredObject(profile, "lithologyWeights");
        requireExactLithologyCoverage(provinceId, rawWeights, lithologyIds);
        return new ParsedProvinceProfile(
                province,
                Collections.unmodifiableMap(parseWeights(provinceId, rawWeights, lithologyIds))
        );
    }

    private static void requireExactLithologyCoverage(
            String provinceId,
            JsonObject rawWeights,
            Set<String> lithologyIds
    ) {
        if (rawWeights.keySet().equals(lithologyIds)) {
            return;
        }
        Set<String> missing = new HashSet<>(lithologyIds);
        missing.removeAll(rawWeights.keySet());
        Set<String> extra = new HashSet<>(rawWeights.keySet());
        extra.removeAll(lithologyIds);
        throw new IllegalArgumentException(
                provinceId + " must cover every lithology exactly; missing=" + missing + ", extra=" + extra
        );
    }

    private static LinkedHashMap<String, Double> parseWeights(
            String provinceId,
            JsonObject rawWeights,
            Set<String> lithologyIds
    ) {
        LinkedHashMap<String, Double> parsed = new LinkedHashMap<>();
        for (String lithology : lithologyIds) {
            parsed.put(lithology, requireWeight(provinceId, lithology, rawWeights.get(lithology)));
        }
        return parsed;
    }

    private static double requireWeight(String provinceId, String lithology, JsonElement rawWeight) {
        if (rawWeight == null || !rawWeight.isJsonPrimitive() || !rawWeight.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(provinceId + "/" + lithology + " weight must be numeric");
        }
        double weight = rawWeight.getAsDouble();
        if (!(weight > 0.0 && weight <= 1.0) || !Double.isFinite(weight)) {
            throw new IllegalArgumentException(
                    provinceId + "/" + lithology + " weight must be finite, > 0 and <= 1"
            );
        }
        return weight;
    }

    private static void requireCompleteProvinceCoverage(
            EnumMap<GeologyProvince, Map<String, Double>> weights
    ) {
        if (weights.size() == GeologyProvince.values().length) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (GeologyProvince province : GeologyProvince.values()) {
            if (!weights.containsKey(province)) {
                missing.add(province.id());
            }
        }
        throw new IllegalArgumentException("missing geological province profiles: " + missing);
    }

    private static Optional<GeologyProvince> provinceById(String id) {
        for (GeologyProvince province : GeologyProvince.values()) {
            if (province.id().equals(id)) {
                return Optional.of(province);
            }
        }
        return Optional.empty();
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

    private record ProfileHeader(String runtimeStatus, int blendWidthBlocks) {
    }

    private record ParsedProvinceProfile(GeologyProvince province, Map<String, Double> weights) {
    }

    public record Snapshot(
            String runtimeStatus,
            int blendWidthBlocks,
            Set<String> lithologyIds,
            Map<GeologyProvince, Map<String, Double>> weights
    ) {
        private static Snapshot unloaded() {
            return new Snapshot("unloaded", 0, Set.of(), Map.of());
        }

        public boolean loaded() {
            return !weights.isEmpty();
        }

        public double weight(GeologyProvince province, String lithology) {
            Map<String, Double> profile = weights.get(province);
            if (profile == null || !profile.containsKey(lithology)) {
                throw new IllegalArgumentException("unknown province/lithology pair: " + province.id() + "/" + lithology);
            }
            return profile.get(lithology);
        }

        public double effectiveWeight(GeologyProvinceSampler.Sample sample, String lithology) {
            requireLoaded();
            double primary = weight(sample.province(), lithology);
            double neighbor = weight(sample.neighborProvince(), lithology);
            return blend(primary, neighbor, sample.interiorBlend(blendWidthBlocks));
        }

        public Map<String, Double> effectiveWeights(GeologyProvinceSampler.Sample sample) {
            requireLoaded();
            Map<String, Double> result = new HashMap<>();
            for (String lithology : lithologyIds) {
                result.put(lithology, effectiveWeight(sample, lithology));
            }
            return Collections.unmodifiableMap(result);
        }

        private void requireLoaded() {
            if (!loaded()) {
                throw new IllegalStateException("GeoStrata province profiles have not been loaded yet");
            }
        }
    }

    static double blend(double primary, double neighbor, double interiorBlend) {
        double clamped = Math.min(1.0, Math.max(0.0, interiorBlend));
        double primaryShare = 0.5 + 0.5 * clamped;
        return primary * primaryShare + neighbor * (1.0 - primaryShare);
    }
}
