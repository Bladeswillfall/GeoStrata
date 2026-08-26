package com.geostrata.geology;

import com.geostrata.GeoStrata;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
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
public final class GeologyProvinceProfiles implements SimpleSynchronousResourceReloadListener {
    private static final GeologyProvinceProfiles INSTANCE = new GeologyProvinceProfiles();
    private static final Identifier RELOAD_ID = GeoStrata.id("geology_profiles");
    private static final Identifier CATALOG_RESOURCE = GeoStrata.id("geology/lithologies.json");
    private static final Identifier PROFILES_RESOURCE = GeoStrata.id("geology/province_profiles.json");

    private volatile Snapshot snapshot = Snapshot.unloaded();

    private GeologyProvinceProfiles() {
    }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(INSTANCE);
    }

    public static Snapshot current() {
        return INSTANCE.snapshot;
    }

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        try {
            JsonObject catalog = readObject(manager, CATALOG_RESOURCE);
            JsonObject profiles = readObject(manager, PROFILES_RESOURCE);
            Snapshot loaded = parse(catalog, profiles);
            snapshot = loaded;
            GeoStrata.LOGGER.info(
                    "Loaded GeoStrata province profiles: {} provinces, {} lithologies, {}-block blend width",
                    GeologyProvince.values().length,
                    loaded.lithologyIds().size(),
                    loaded.blendWidthBlocks()
            );
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata province profiles", exception);
        }
    }

    static Snapshot parse(JsonObject catalog, JsonObject profilesRoot) {
        Set<String> lithologyIds = parseLithologyIds(catalog);

        requireInt(profilesRoot, "schemaVersion", 1);
        requireString(profilesRoot, "model", "geostrata:province_profiles");
        String runtimeStatus = requireString(profilesRoot, "runtimeStatus");
        if (!runtimeStatus.equals("metadata_only") && !runtimeStatus.equals("runtime_bias")) {
            throw new IllegalArgumentException("unsupported province profile runtimeStatus: " + runtimeStatus);
        }

        int blendWidth = requireInt(profilesRoot, "blendWidthBlocks");
        if (blendWidth < 1 || blendWidth > GeologyProvinceSampler.CELL_SIZE / 2) {
            throw new IllegalArgumentException("blendWidthBlocks must be between 1 and half the province cell size");
        }

        JsonArray profiles = requiredArray(profilesRoot, "profiles");
        EnumMap<GeologyProvince, Map<String, Double>> weights = new EnumMap<>(GeologyProvince.class);

        for (JsonElement element : profiles) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("province profile entry must be an object");
            }
            JsonObject profile = element.getAsJsonObject();
            String provinceId = requireString(profile, "province");
            GeologyProvince province = provinceById(provinceId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown geological province: " + provinceId));
            if (weights.containsKey(province)) {
                throw new IllegalArgumentException("duplicate geological province profile: " + provinceId);
            }

            JsonObject rawWeights = requiredObject(profile, "lithologyWeights");
            if (!rawWeights.keySet().equals(lithologyIds)) {
                Set<String> missing = new HashSet<>(lithologyIds);
                missing.removeAll(rawWeights.keySet());
                Set<String> extra = new HashSet<>(rawWeights.keySet());
                extra.removeAll(lithologyIds);
                throw new IllegalArgumentException(
                        provinceId + " must cover every lithology exactly; missing=" + missing + ", extra=" + extra
                );
            }

            LinkedHashMap<String, Double> parsedWeights = new LinkedHashMap<>();
            for (String lithology : lithologyIds) {
                JsonElement rawWeight = rawWeights.get(lithology);
                if (rawWeight == null || !rawWeight.isJsonPrimitive() || !rawWeight.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException(provinceId + "/" + lithology + " weight must be numeric");
                }
                double weight = rawWeight.getAsDouble();
                if (!(weight > 0.0 && weight <= 1.0) || !Double.isFinite(weight)) {
                    throw new IllegalArgumentException(
                            provinceId + "/" + lithology + " weight must be finite, > 0 and <= 1"
                    );
                }
                parsedWeights.put(lithology, weight);
            }
            weights.put(province, Collections.unmodifiableMap(parsedWeights));
        }

        if (weights.size() != GeologyProvince.values().length) {
            List<String> missing = new ArrayList<>();
            for (GeologyProvince province : GeologyProvince.values()) {
                if (!weights.containsKey(province)) {
                    missing.add(province.id());
                }
            }
            throw new IllegalArgumentException("missing geological province profiles: " + missing);
        }

        return new Snapshot(
                runtimeStatus,
                blendWidth,
                Collections.unmodifiableSet(new HashSet<>(lithologyIds)),
                Collections.unmodifiableMap(weights)
        );
    }

    private static Set<String> parseLithologyIds(JsonObject catalog) {
        requireInt(catalog, "schemaVersion", 1);
        requireString(catalog, "model", "geostrata:lithology_catalog");
        JsonArray lithologies = requiredArray(catalog, "lithologies");
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<>();
        for (JsonElement element : lithologies) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("lithology entry must be an object");
            }
            String id = requireString(element.getAsJsonObject(), "id");
            if (ordered.put(id, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("duplicate lithology id: " + id);
            }
        }
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("lithology catalog must not be empty");
        }
        return Collections.unmodifiableSet(ordered.keySet());
    }

    private static JsonObject readObject(ResourceManager manager, Identifier id) throws IOException {
        Resource resource = manager.getResource(id)
                .orElseThrow(() -> new IOException("missing server-data resource " + id));
        try (BufferedReader reader = resource.getReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new JsonParseException(id + " root must be a JSON object");
            }
            return root.getAsJsonObject();
        }
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
