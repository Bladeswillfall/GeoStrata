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
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Loads diagnostic-only physical scales for structural transform primitives. */
public final class StructuralTransformProfiles implements SimpleSynchronousResourceReloadListener {
    private static final StructuralTransformProfiles INSTANCE = new StructuralTransformProfiles();
    private static final Identifier RELOAD_ID = GeoStrata.id("structural_transform_profiles");
    private static final Identifier RESOURCE = GeoStrata.id("geology/structural_transform_profiles.json");

    private volatile Snapshot snapshot = Snapshot.unloaded();

    private StructuralTransformProfiles() {
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
            Snapshot loaded = parse(readObject(manager, RESOURCE));
            snapshot = loaded;
            GeoStrata.LOGGER.info(
                    "Loaded GeoStrata structural transform profiles: {} provinces",
                    loaded.profiles().size()
            );
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata structural transform profiles", exception);
        }
    }

    static Snapshot parse(JsonObject root) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:structural_transform_profiles");
        requireString(root, "runtimeStatus", "metadata_only");

        Map<GeologyProvince, Profile> profiles = parseProfiles(requiredArray(root, "profiles"));
        requireExactProvinceCoverage(profiles.keySet());
        return new Snapshot("metadata_only", Collections.unmodifiableMap(profiles));
    }

    private static Map<GeologyProvince, Profile> parseProfiles(JsonArray array) {
        if (array.size() == 0) {
            throw new IllegalArgumentException("structural transform profiles must not be empty");
        }

        EnumMap<GeologyProvince, Profile> profiles = new EnumMap<>(GeologyProvince.class);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("structural transform profile entry must be an object");
            }
            Profile profile = parseProfile(element.getAsJsonObject());
            if (profiles.put(profile.province(), profile) != null) {
                throw new IllegalArgumentException("duplicate transform profile for " + profile.province().id());
            }
        }
        return profiles;
    }

    private static Profile parseProfile(JsonObject object) {
        return new Profile(
                requireProvince(requireString(object, "province")),
                requireDouble(object, "maxDipDegrees"),
                requireDouble(object, "maxFoldAmplitudeBlocks"),
                requireDouble(object, "foldWavelengthBlocks"),
                requireDouble(object, "maxFaultDisplacementBlocks"),
                requireDouble(object, "faultPlaneOffsetRangeBlocks")
        );
    }

    private static GeologyProvince requireProvince(String id) {
        for (GeologyProvince province : GeologyProvince.values()) {
            if (province.id().equals(id)) {
                return province;
            }
        }
        throw new IllegalArgumentException("unknown geological province in transform profiles: " + id);
    }

    private static void requireExactProvinceCoverage(Set<GeologyProvince> provided) {
        Set<GeologyProvince> required = Set.of(GeologyProvince.values());
        if (provided.equals(required)) {
            return;
        }
        Set<GeologyProvince> missing = new HashSet<>(required);
        missing.removeAll(provided);
        Set<GeologyProvince> extra = new HashSet<>(provided);
        extra.removeAll(required);
        throw new IllegalArgumentException(
                "structural transform profiles must cover provinces exactly; missing="
                        + missing + ", extra=" + extra
        );
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

    public record Profile(
            GeologyProvince province,
            double maxDipDegrees,
            double maxFoldAmplitudeBlocks,
            double foldWavelengthBlocks,
            double maxFaultDisplacementBlocks,
            double faultPlaneOffsetRangeBlocks
    ) {
        public Profile {
            if (province == null) {
                throw new IllegalArgumentException("transform profile province must not be null");
            }
            requireRange(maxDipDegrees, 0.0, 75.0, "maximum dip degrees");
            requireRange(maxFoldAmplitudeBlocks, 0.0, 96.0, "maximum fold amplitude");
            requireRange(foldWavelengthBlocks, 64.0, 2048.0, "fold wavelength");
            requireRange(maxFaultDisplacementBlocks, 0.0, 128.0, "maximum fault displacement");
            requireRange(faultPlaneOffsetRangeBlocks, 32.0, 384.0, "fault-plane offset range");
            if (foldWavelengthBlocks < maxFoldAmplitudeBlocks * 4.0) {
                throw new IllegalArgumentException(
                        province.id() + " fold wavelength must be at least four times fold amplitude"
                );
            }
        }

        private static void requireRange(double value, double min, double max, String name) {
            if (!Double.isFinite(value) || value < min || value > max) {
                throw new IllegalArgumentException(name + " must be finite and within " + min + ".." + max);
            }
        }
    }

    public record Snapshot(String runtimeStatus, Map<GeologyProvince, Profile> profiles) {
        private static Snapshot unloaded() {
            return new Snapshot("unloaded", Map.of());
        }

        public boolean loaded() {
            return profiles.size() == GeologyProvince.values().length;
        }

        public Profile profileFor(GeologyProvince province) {
            Profile profile = profiles.get(province);
            if (profile == null) {
                throw new IllegalArgumentException("missing structural transform profile for " + province);
            }
            return profile;
        }
    }
}
