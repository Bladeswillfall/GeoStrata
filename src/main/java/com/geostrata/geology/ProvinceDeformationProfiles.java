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

/** Loads diagnostic-only province tuning for terrain-aware structural deformation. */
public final class ProvinceDeformationProfiles implements SimpleSynchronousResourceReloadListener {
    private static final ProvinceDeformationProfiles INSTANCE = new ProvinceDeformationProfiles();
    private static final Identifier RELOAD_ID = GeoStrata.id("province_deformation_profiles");
    private static final Identifier PROFILES_RESOURCE = GeoStrata.id("geology/province_deformation_profiles.json");
    private static final double WEIGHT_EPSILON = 1.0e-9;

    private volatile Snapshot snapshot = Snapshot.unloaded();

    private ProvinceDeformationProfiles() {
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
            Snapshot loaded = parse(readObject(manager, PROFILES_RESOURCE));
            snapshot = loaded;
            GeoStrata.LOGGER.info(
                    "Loaded GeoStrata province deformation profiles: {} provinces, {}-block blend width",
                    loaded.profiles().size(),
                    loaded.blendWidthBlocks()
            );
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata province deformation profiles", exception);
        }
    }

    static Snapshot parse(JsonObject root) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:province_deformation_profiles");
        requireString(root, "runtimeStatus", "metadata_only");

        int blendWidthBlocks = requireBlendWidth(root);
        Normalization normalization = parseNormalization(requiredObject(root, "morphologyNormalization"));
        Map<GeologyProvince, Profile> profiles = parseProfiles(requiredArray(root, "profiles"));
        requireExactProvinceCoverage(profiles.keySet());
        return new Snapshot(
                "metadata_only",
                blendWidthBlocks,
                normalization,
                Collections.unmodifiableMap(profiles)
        );
    }

    private static int requireBlendWidth(JsonObject root) {
        int blendWidthBlocks = requireInt(root, "blendWidthBlocks");
        if (blendWidthBlocks < 1 || blendWidthBlocks > 384) {
            throw new IllegalArgumentException("blendWidthBlocks must be within 1..384");
        }
        return blendWidthBlocks;
    }

    private static Normalization parseNormalization(JsonObject object) {
        return new Normalization(
                requireDouble(object, "reliefScaleBlocks"),
                requireDouble(object, "slopeScale"),
                requireDouble(object, "ridgeProminenceScaleBlocks"),
                requireDouble(object, "reliefWeight"),
                requireDouble(object, "slopeWeight"),
                requireDouble(object, "ridgeWeight")
        );
    }

    private static Map<GeologyProvince, Profile> parseProfiles(JsonArray array) {
        if (array.size() == 0) {
            throw new IllegalArgumentException("province deformation profiles must not be empty");
        }

        EnumMap<GeologyProvince, Profile> profiles = new EnumMap<>(GeologyProvince.class);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("province deformation profile entry must be an object");
            }
            Profile profile = parseProfile(element.getAsJsonObject());
            if (profiles.put(profile.province(), profile) != null) {
                throw new IllegalArgumentException("duplicate deformation profile for " + profile.province().id());
            }
        }
        return profiles;
    }

    private static Profile parseProfile(JsonObject object) {
        return new Profile(
                requireProvince(requireString(object, "province")),
                requireUnit(object, "baselineIntensity"),
                requireUnit(object, "terrainCoupling"),
                requireUnit(object, "dipPotential"),
                requireUnit(object, "foldPotential"),
                requireUnit(object, "faultPotential")
        );
    }

    private static GeologyProvince requireProvince(String id) {
        for (GeologyProvince province : GeologyProvince.values()) {
            if (province.id().equals(id)) {
                return province;
            }
        }
        throw new IllegalArgumentException("unknown geological province in deformation profiles: " + id);
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
                "province deformation profiles must cover provinces exactly; missing="
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

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return element.getAsJsonObject();
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

    private static double requireUnit(JsonObject object, String key) {
        double value = requireDouble(object, key);
        requireUnitValue(value, key);
        return value;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireUnitValue(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and within 0..1");
        }
    }

    public record Normalization(
            double reliefScaleBlocks,
            double slopeScale,
            double ridgeProminenceScaleBlocks,
            double reliefWeight,
            double slopeWeight,
            double ridgeWeight
    ) {
        public Normalization {
            requirePositive(reliefScaleBlocks, "relief scale");
            requirePositive(slopeScale, "slope scale");
            requirePositive(ridgeProminenceScaleBlocks, "ridge prominence scale");
            requireUnitValue(reliefWeight, "relief weight");
            requireUnitValue(slopeWeight, "slope weight");
            requireUnitValue(ridgeWeight, "ridge weight");
            double weightSum = reliefWeight + slopeWeight + ridgeWeight;
            if (Math.abs(weightSum - 1.0) > WEIGHT_EPSILON) {
                throw new IllegalArgumentException("morphology normalization weights must sum to 1.0");
            }
        }
    }

    public record Profile(
            GeologyProvince province,
            double baselineIntensity,
            double terrainCoupling,
            double dipPotential,
            double foldPotential,
            double faultPotential
    ) {
        public Profile {
            if (province == null) {
                throw new IllegalArgumentException("deformation profile province must not be null");
            }
            requireUnitValue(baselineIntensity, "baseline intensity");
            requireUnitValue(terrainCoupling, "terrain coupling");
            requireUnitValue(dipPotential, "dip potential");
            requireUnitValue(foldPotential, "fold potential");
            requireUnitValue(faultPotential, "fault potential");
            if (baselineIntensity + terrainCoupling > 1.0 + WEIGHT_EPSILON) {
                throw new IllegalArgumentException(
                        province.id() + " baseline intensity + terrain coupling must not exceed 1.0"
                );
            }
        }
    }

    public record Snapshot(
            String runtimeStatus,
            int blendWidthBlocks,
            Normalization normalization,
            Map<GeologyProvince, Profile> profiles
    ) {
        private static Snapshot unloaded() {
            return new Snapshot("unloaded", 0, null, Map.of());
        }

        public boolean loaded() {
            return blendWidthBlocks > 0
                    && normalization != null
                    && profiles.size() == GeologyProvince.values().length;
        }

        public Profile profileFor(GeologyProvince province) {
            Profile profile = profiles.get(province);
            if (profile == null) {
                throw new IllegalArgumentException("missing deformation profile for " + province);
            }
            return profile;
        }
    }
}
