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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads metadata-only tuning for the diagnostic sedimentary stratigraphic field. */
public final class SedimentaryFieldProfiles implements SimpleSynchronousResourceReloadListener {
    private static final SedimentaryFieldProfiles INSTANCE = new SedimentaryFieldProfiles();
    private static final Identifier RELOAD_ID = GeoStrata.id("sedimentary_field_profiles");
    private static final Identifier SUCCESSIONS_RESOURCE = GeoStrata.id("geology/sedimentary_successions.json");
    private static final Identifier PROFILES_RESOURCE = GeoStrata.id("geology/sedimentary_field_profiles.json");

    private volatile Snapshot snapshot = Snapshot.unloaded();

    private SedimentaryFieldProfiles() {
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
            Snapshot loaded = parse(
                    readObject(manager, SUCCESSIONS_RESOURCE),
                    readObject(manager, PROFILES_RESOURCE)
            );
            snapshot = loaded;
            GeoStrata.LOGGER.info(
                    "Loaded GeoStrata diagnostic sedimentary field profiles: {} continuity classes",
                    loaded.parametersByContinuity().size()
            );
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata sedimentary field profiles", exception);
        }
    }

    static Snapshot parse(JsonObject successionsRoot, JsonObject profilesRoot) {
        requireInt(successionsRoot, "schemaVersion", 1);
        requireString(successionsRoot, "model", "geostrata:sedimentary_successions");
        requireString(successionsRoot, "runtimeStatus", "metadata_only");
        requireString(successionsRoot, "order", "lower_to_upper");

        JsonArray rawSuccessions = requiredArray(successionsRoot, "successions");
        if (rawSuccessions.size() == 0) {
            throw new IllegalArgumentException("sedimentary successions must not be empty");
        }

        Set<String> continuities = new HashSet<>();
        List<SuccessionScale> scales = new ArrayList<>();
        for (JsonElement element : rawSuccessions) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("succession entry must be an object");
            }
            JsonObject succession = element.getAsJsonObject();
            String id = requireString(succession, "id");
            String continuity = requireString(succession, "continuity");
            continuities.add(continuity);

            JsonArray rawBeds = requiredArray(succession, "beds");
            if (rawBeds.size() == 0) {
                throw new IllegalArgumentException(id + " must contain beds");
            }
            List<Double> thicknesses = new ArrayList<>(rawBeds.size());
            for (JsonElement rawBed : rawBeds) {
                if (!rawBed.isJsonObject()) {
                    throw new IllegalArgumentException(id + " bed must be an object");
                }
                double thickness = requireDouble(rawBed.getAsJsonObject(), "relativeThickness");
                if (!(thickness > 0.0)) {
                    throw new IllegalArgumentException(id + " relative thicknesses must be positive");
                }
                thicknesses.add(thickness);
            }
            scales.add(new SuccessionScale(id, continuity, List.copyOf(thicknesses)));
        }

        requireInt(profilesRoot, "schemaVersion", 1);
        requireString(profilesRoot, "model", "geostrata:sedimentary_field_profiles");
        requireString(profilesRoot, "runtimeStatus", "metadata_only");

        JsonArray rawProfiles = requiredArray(profilesRoot, "profiles");
        if (rawProfiles.size() == 0) {
            throw new IllegalArgumentException("sedimentary field profiles must not be empty");
        }

        LinkedHashMap<String, SedimentaryStratigraphicField.Parameters> parameters = new LinkedHashMap<>();
        for (JsonElement element : rawProfiles) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("sedimentary field profile entry must be an object");
            }
            JsonObject profile = element.getAsJsonObject();
            String continuity = requireString(profile, "continuity");
            if (!continuities.contains(continuity)) {
                throw new IllegalArgumentException("unused sedimentary field continuity profile: " + continuity);
            }
            if (parameters.containsKey(continuity)) {
                throw new IllegalArgumentException("duplicate sedimentary field continuity profile: " + continuity);
            }

            double cycle = requireDouble(profile, "cycleThicknessBlocks");
            double maxDip = requireDouble(profile, "maxDip");
            double warpAmplitude = requireDouble(profile, "warpAmplitudeBlocks");
            double warpWavelength = requireDouble(profile, "warpWavelengthBlocks");

            if (cycle < 8.0 || cycle > 256.0) {
                throw new IllegalArgumentException(continuity + " cycle thickness must be within 8..256 blocks");
            }
            if (maxDip < 0.0 || maxDip > 0.35) {
                throw new IllegalArgumentException(continuity + " max dip must be within 0..0.35");
            }
            if (warpAmplitude < 0.0 || warpAmplitude > cycle * 0.25) {
                throw new IllegalArgumentException(continuity + " warp amplitude must be within 0..25% of cycle thickness");
            }
            if (warpWavelength < cycle * 2.0 || warpWavelength > 2048.0) {
                throw new IllegalArgumentException(continuity + " warp wavelength must be at least two cycles and at most 2048 blocks");
            }

            parameters.put(
                    continuity,
                    new SedimentaryStratigraphicField.Parameters(cycle, maxDip, warpAmplitude, warpWavelength)
            );
        }

        if (!parameters.keySet().equals(continuities)) {
            Set<String> missing = new HashSet<>(continuities);
            missing.removeAll(parameters.keySet());
            Set<String> extra = new HashSet<>(parameters.keySet());
            extra.removeAll(continuities);
            throw new IllegalArgumentException(
                    "sedimentary field profiles must cover succession continuities exactly; missing="
                            + missing + ", extra=" + extra
            );
        }

        for (SuccessionScale scale : scales) {
            SedimentaryStratigraphicField.Parameters profile = parameters.get(scale.continuity());
            double total = scale.relativeThicknesses().stream().mapToDouble(Double::doubleValue).sum();
            double thinnest = scale.relativeThicknesses().stream()
                    .mapToDouble(value -> value / total * profile.cycleThicknessBlocks())
                    .min()
                    .orElseThrow();
            if (thinnest < 2.0) {
                throw new IllegalArgumentException(
                        scale.id() + " would compress its thinnest diagnostic bed below two blocks: " + thinnest
                );
            }
        }

        return new Snapshot("metadata_only", Collections.unmodifiableMap(parameters));
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

    private record SuccessionScale(String id, String continuity, List<Double> relativeThicknesses) {
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
