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
import java.util.Optional;
import java.util.Set;

/** Loads metadata-only sedimentary succession motifs for diagnostics and future worldgen. */
public final class SedimentarySuccessions implements SimpleSynchronousResourceReloadListener {
    private static final SedimentarySuccessions INSTANCE = new SedimentarySuccessions();
    private static final Identifier RELOAD_ID = GeoStrata.id("sedimentary_successions");
    private static final Identifier CATALOG_RESOURCE = GeoStrata.id("geology/lithologies.json");
    private static final Identifier SUCCESSIONS_RESOURCE = GeoStrata.id("geology/sedimentary_successions.json");

    private volatile Snapshot snapshot = Snapshot.unloaded();

    private SedimentarySuccessions() {
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
                    readObject(manager, CATALOG_RESOURCE),
                    readObject(manager, SUCCESSIONS_RESOURCE)
            );
            snapshot = loaded;
            GeoStrata.LOGGER.info("Loaded GeoStrata sedimentary succession metadata: {} motifs", loaded.successions().size());
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata sedimentary successions", exception);
        }
    }

    static Snapshot parse(JsonObject catalog, JsonObject root) {
        Set<String> sedimentary = sedimentaryLithologies(catalog);
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:sedimentary_successions");
        requireString(root, "runtimeStatus", "metadata_only");
        requireString(root, "order", "lower_to_upper");

        JsonArray rawSuccessions = requiredArray(root, "successions");
        if (rawSuccessions.size() == 0) {
            throw new IllegalArgumentException("sedimentary successions must not be empty");
        }

        List<Succession> parsed = new ArrayList<>();
        LinkedHashMap<String, Succession> byId = new LinkedHashMap<>();
        Set<String> covered = new HashSet<>();

        for (JsonElement element : rawSuccessions) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("succession entry must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            String id = requireString(object, "id");
            if (!id.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("invalid succession id: " + id);
            }
            if (byId.containsKey(id)) {
                throw new IllegalArgumentException("duplicate succession id: " + id);
            }

            JsonArray rawContexts = requiredArray(object, "contexts");
            if (rawContexts.size() == 0) {
                throw new IllegalArgumentException(id + " must declare at least one context");
            }
            List<GeologyProvince> contexts = new ArrayList<>();
            Set<GeologyProvince> contextSet = new HashSet<>();
            for (JsonElement rawContext : rawContexts) {
                if (!rawContext.isJsonPrimitive() || !rawContext.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(id + " context must be a string");
                }
                GeologyProvince province = provinceById(rawContext.getAsString())
                        .orElseThrow(() -> new IllegalArgumentException(
                                id + " references unknown province context " + rawContext.getAsString()
                        ));
                if (!contextSet.add(province)) {
                    throw new IllegalArgumentException(id + " contains duplicate context " + province.id());
                }
                contexts.add(province);
            }

            String continuity = requireString(object, "continuity");
            if (!continuity.equals("local") && !continuity.equals("regional")) {
                throw new IllegalArgumentException(id + " continuity must be local or regional");
            }

            JsonArray rawBeds = requiredArray(object, "beds");
            if (rawBeds.size() < 3) {
                throw new IllegalArgumentException(id + " must contain at least three beds");
            }
            List<Bed> beds = new ArrayList<>();
            Set<String> distinct = new HashSet<>();
            for (JsonElement rawBed : rawBeds) {
                if (!rawBed.isJsonObject()) {
                    throw new IllegalArgumentException(id + " bed must be an object");
                }
                JsonObject bed = rawBed.getAsJsonObject();
                String lithology = requireString(bed, "lithology");
                if (!sedimentary.contains(lithology)) {
                    throw new IllegalArgumentException(id + " uses unknown or non-sedimentary lithology " + lithology);
                }
                double thickness = requireDouble(bed, "relativeThickness");
                if (thickness < 0.1 || thickness > 4.0) {
                    throw new IllegalArgumentException(id + "/" + lithology + " relativeThickness must be within 0.1..4.0");
                }
                beds.add(new Bed(lithology, thickness));
                distinct.add(lithology);
                covered.add(lithology);
            }
            if (distinct.size() < 2) {
                throw new IllegalArgumentException(id + " must contain at least two distinct lithologies");
            }

            Succession succession = new Succession(id, contexts, continuity, beds);
            parsed.add(succession);
            byId.put(id, succession);
        }

        if (!covered.equals(sedimentary)) {
            Set<String> missing = new HashSet<>(sedimentary);
            missing.removeAll(covered);
            throw new IllegalArgumentException("succession metadata does not cover all sedimentary lithologies; missing=" + missing);
        }

        return new Snapshot(
                "metadata_only",
                Collections.unmodifiableList(parsed),
                Collections.unmodifiableMap(byId)
        );
    }

    private static Set<String> sedimentaryLithologies(JsonObject catalog) {
        requireInt(catalog, "schemaVersion", 1);
        requireString(catalog, "model", "geostrata:lithology_catalog");
        JsonArray entries = requiredArray(catalog, "lithologies");
        Set<String> sedimentary = new HashSet<>();
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("lithology entry must be an object");
            }
            JsonObject entry = element.getAsJsonObject();
            if ("sedimentary".equals(requireString(entry, "rockClass"))) {
                sedimentary.add(requireString(entry, "id"));
            }
        }
        if (sedimentary.isEmpty()) {
            throw new IllegalArgumentException("lithology catalog contains no sedimentary rocks");
        }
        return sedimentary;
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

    public record Bed(String lithology, double relativeThickness) {
    }

    public record Succession(String id, List<GeologyProvince> contexts, String continuity, List<Bed> beds) {
        public Succession {
            contexts = List.copyOf(contexts);
            beds = List.copyOf(beds);
        }

        public boolean matchesContext(GeologyProvince province) {
            return contexts.contains(province);
        }
    }

    public record Snapshot(String runtimeStatus, List<Succession> successions, Map<String, Succession> byId) {
        private static Snapshot unloaded() {
            return new Snapshot("unloaded", List.of(), Map.of());
        }

        public boolean loaded() {
            return !successions.isEmpty();
        }
    }
}
