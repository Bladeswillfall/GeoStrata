package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Loads metadata-only sedimentary succession motifs for diagnostics and future worldgen. */
public final class SedimentarySuccessions {
    private static volatile Snapshot snapshot = Snapshot.unloaded();

    private SedimentarySuccessions() {
    }

    public static Snapshot current() {
        return snapshot;
    }

    static void install(Snapshot loaded) {
        snapshot = loaded;
    }

    static Snapshot parse(LithologyCatalog.Snapshot catalog, JsonObject root) {
        Set<String> sedimentary = sedimentaryLithologies(catalog);
        requireRootContract(root);
        JsonArray rawSuccessions = requiredArray(root, "successions");
        if (rawSuccessions.size() == 0) {
            throw new IllegalArgumentException("sedimentary successions must not be empty");
        }

        List<Succession> parsed = new ArrayList<>();
        LinkedHashMap<String, Succession> byId = new LinkedHashMap<>();
        Set<String> covered = new HashSet<>();
        for (JsonElement element : rawSuccessions) {
            Succession succession = parseSuccession(element, sedimentary);
            if (byId.put(succession.id(), succession) != null) {
                throw new IllegalArgumentException("duplicate succession id: " + succession.id());
            }
            parsed.add(succession);
            addCoveredLithologies(covered, succession);
        }

        requireCatalogCoverage(covered, sedimentary);
        return new Snapshot(
                "metadata_only",
                Collections.unmodifiableList(parsed),
                Collections.unmodifiableMap(byId)
        );
    }

    private static void requireRootContract(JsonObject root) {
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:sedimentary_successions");
        requireString(root, "runtimeStatus", "metadata_only");
        requireString(root, "order", "lower_to_upper");
    }

    private static Succession parseSuccession(JsonElement element, Set<String> sedimentary) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("succession entry must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        String id = requireSuccessionId(object);
        List<GeologyProvince> contexts = parseContexts(id, object);
        String continuity = requireContinuity(id, object);
        List<Bed> beds = parseBeds(id, object, sedimentary);
        return new Succession(id, contexts, continuity, beds);
    }

    private static String requireSuccessionId(JsonObject object) {
        String id = requireString(object, "id");
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("invalid succession id: " + id);
        }
        return id;
    }

    private static List<GeologyProvince> parseContexts(String id, JsonObject object) {
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
            String provinceId = rawContext.getAsString();
            GeologyProvince province = provinceById(provinceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            id + " references unknown province context " + provinceId
                    ));
            if (!contextSet.add(province)) {
                throw new IllegalArgumentException(id + " contains duplicate context " + province.id());
            }
            contexts.add(province);
        }
        return contexts;
    }

    private static String requireContinuity(String id, JsonObject object) {
        String continuity = requireString(object, "continuity");
        if (!continuity.equals("local") && !continuity.equals("regional")) {
            throw new IllegalArgumentException(id + " continuity must be local or regional");
        }
        return continuity;
    }

    private static List<Bed> parseBeds(String id, JsonObject object, Set<String> sedimentary) {
        JsonArray rawBeds = requiredArray(object, "beds");
        if (rawBeds.size() < 3) {
            throw new IllegalArgumentException(id + " must contain at least three beds");
        }

        List<Bed> beds = new ArrayList<>();
        Set<String> distinct = new HashSet<>();
        for (JsonElement rawBed : rawBeds) {
            Bed bed = parseBed(id, rawBed, sedimentary);
            beds.add(bed);
            distinct.add(bed.lithology());
        }
        if (distinct.size() < 2) {
            throw new IllegalArgumentException(id + " must contain at least two distinct lithologies");
        }
        return beds;
    }

    private static Bed parseBed(String id, JsonElement rawBed, Set<String> sedimentary) {
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
        return new Bed(lithology, thickness);
    }

    private static void addCoveredLithologies(Set<String> covered, Succession succession) {
        for (Bed bed : succession.beds()) {
            covered.add(bed.lithology());
        }
    }

    private static void requireCatalogCoverage(Set<String> covered, Set<String> sedimentary) {
        if (covered.equals(sedimentary)) {
            return;
        }
        Set<String> missing = new HashSet<>(sedimentary);
        missing.removeAll(covered);
        throw new IllegalArgumentException("succession metadata does not cover all sedimentary lithologies; missing=" + missing);
    }

    private static Set<String> sedimentaryLithologies(LithologyCatalog.Snapshot catalog) {
        if (!catalog.loaded()) {
            throw new IllegalArgumentException("lithology catalog must be loaded before sedimentary successions");
        }
        Set<String> sedimentary = new HashSet<>();
        for (LithologyCatalog.Entry entry : catalog.entries()) {
            if ("sedimentary".equals(entry.rockClass())) {
                sedimentary.add(entry.id());
            }
        }
        if (sedimentary.isEmpty()) {
            throw new IllegalArgumentException("lithology catalog contains no sedimentary rocks");
        }
        return sedimentary;
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
