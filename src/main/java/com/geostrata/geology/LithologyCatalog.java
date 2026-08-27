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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Runtime-safe semantic catalog for GeoStrata lithologies.
 *
 * <p>The catalog intentionally stores registry identifiers as validated strings.
 * Registry lookup belongs at the eventual world-mutation boundary, keeping this
 * metadata service independently testable and safe during server-data reload.</p>
 */
public final class LithologyCatalog implements SimpleSynchronousResourceReloadListener {
    private static final LithologyCatalog INSTANCE = new LithologyCatalog();
    private static final Identifier RELOAD_ID = GeoStrata.id("lithology_catalog");
    private static final Identifier RESOURCE_ID = GeoStrata.id("geology/lithologies.json");
    private static final Pattern SIMPLE_ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Set<String> ROCK_CLASSES = Set.of("sedimentary", "igneous", "metamorphic");
    private static final Set<String> CONTINUITIES = Set.of("local", "regional");

    private volatile Snapshot snapshot = Snapshot.unloaded();

    private LithologyCatalog() {
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
            Snapshot loaded = parse(readObject(manager, RESOURCE_ID));
            snapshot = loaded;
            GeoStrata.LOGGER.info("Loaded GeoStrata lithology catalog: {} lithologies", loaded.entries().size());
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata lithology catalog", exception);
        }
    }

    static Snapshot parse(JsonObject root) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:lithology_catalog");
        requireString(root, "runtimeStatus", "metadata_only");

        JsonArray rawEntries = requiredArray(root, "lithologies");
        if (rawEntries.size() == 0) {
            throw new IllegalArgumentException("lithology catalog must not be empty");
        }

        LinkedHashMap<String, Entry> byId = new LinkedHashMap<>();
        Set<String> blocks = new HashSet<>();
        Set<String> baselineFeatures = new HashSet<>();

        for (JsonElement element : rawEntries) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("lithology entry must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            String id = simpleId(object, "id");
            if (byId.containsKey(id)) {
                throw new IllegalArgumentException("duplicate lithology id: " + id);
            }

            String block = namespacedIdentifier(object, "block", "geostrata");
            if (!blocks.add(block)) {
                throw new IllegalArgumentException("duplicate lithology block: " + block);
            }

            String rockClass = requireString(object, "rockClass");
            if (!ROCK_CLASSES.contains(rockClass)) {
                throw new IllegalArgumentException(id + " has unsupported rockClass: " + rockClass);
            }

            String genesis = simpleId(object, "genesis");
            String bodyStyle = simpleId(object, "bodyStyle");
            String depthAffinity = simpleId(object, "depthAffinity");

            String continuity = requireString(object, "continuity");
            if (!CONTINUITIES.contains(continuity)) {
                throw new IllegalArgumentException(id + " has unsupported continuity: " + continuity);
            }

            String biomeTag = namespacedIdentifier(object, "biomeTag", "geostrata");
            String baselineFeature = simpleId(object, "baselineFeature");
            if (!baselineFeatures.add(baselineFeature)) {
                throw new IllegalArgumentException("duplicate baselineFeature: " + baselineFeature);
            }

            Entry entry = new Entry(
                    id,
                    block,
                    rockClass,
                    genesis,
                    bodyStyle,
                    depthAffinity,
                    continuity,
                    biomeTag,
                    baselineFeature
            );
            byId.put(id, entry);
        }

        return new Snapshot(
                "metadata_only",
                List.copyOf(byId.values()),
                Collections.unmodifiableMap(byId)
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

    private static String simpleId(JsonObject object, String key) {
        String value = requireString(object, key);
        if (!SIMPLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " must match " + SIMPLE_ID.pattern() + ", found " + value);
        }
        return value;
    }

    private static String namespacedIdentifier(JsonObject object, String key, String requiredNamespace) {
        String value = requireString(object, key);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " must be a valid namespaced identifier, found " + value);
        }
        int separator = value.indexOf(':');
        if (!value.substring(0, separator).equals(requiredNamespace)) {
            throw new IllegalArgumentException(key + " must use namespace " + requiredNamespace + ", found " + value);
        }
        return value;
    }

    public record Entry(
            String id,
            String block,
            String rockClass,
            String genesis,
            String bodyStyle,
            String depthAffinity,
            String continuity,
            String biomeTag,
            String baselineFeature
    ) {
    }

    public record Snapshot(String runtimeStatus, List<Entry> entries, Map<String, Entry> byId) {
        private static Snapshot unloaded() {
            return new Snapshot("unloaded", List.of(), Map.of());
        }

        public boolean loaded() {
            return !entries.isEmpty();
        }

        public Entry require(String id) {
            Entry entry = byId.get(id);
            if (entry == null) {
                throw new IllegalArgumentException("unknown GeoStrata lithology: " + id);
            }
            return entry;
        }
    }
}
