package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Merges provider-owned ore definitions into the core occurrence snapshot before provider gating. */
final class ExternalOreOccurrenceCatalog {
    private static final Pattern SIMPLE_ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Set<String> DEPOSIT_STYLES = Set.of(
            "coal_seam",
            "vein",
            "micro_vein",
            "stratiform",
            "disseminated",
            "massive_lens_or_pocket"
    );

    private ExternalOreOccurrenceCatalog() {
    }

    static OreOccurrenceCatalog.Snapshot merge(
            LithologyCatalog.Snapshot lithologies,
            OreOccurrenceCatalog.Snapshot core,
            JsonObject root
    ) {
        if (!lithologies.loaded() || !core.loaded()) {
            throw new IllegalArgumentException("core lithologies and ore occurrences must load before external ores");
        }
        requireInt(root, "schemaVersion", 1);
        requireString(root, "model", "geostrata:external_ore_occurrence_catalog");
        requireString(root, "runtimeStatus", "optional_provider_gated");

        JsonArray rawOccurrences = requiredArray(root, "occurrences");
        LinkedHashMap<String, OreOccurrenceCatalog.Occurrence> byId = new LinkedHashMap<>(core.byId());
        Set<String> claimedGradeBlocks = new HashSet<>();
        core.occurrences().forEach(occurrence -> claimedGradeBlocks.addAll(occurrence.gradeBlocks().values()));

        for (JsonElement raw : rawOccurrences) {
            OreOccurrenceCatalog.Occurrence occurrence = parseOccurrence(raw, lithologies.byId().keySet());
            if (byId.putIfAbsent(occurrence.id(), occurrence) != null) {
                throw new IllegalArgumentException("external ore duplicates material id " + occurrence.id());
            }
            for (String block : occurrence.gradeBlocks().values()) {
                if (!claimedGradeBlocks.add(block)) {
                    throw new IllegalArgumentException("external ore reuses graded ore block " + block);
                }
            }
        }

        return new OreOccurrenceCatalog.Snapshot(
                core.runtimeStatus(),
                core.generationOwner(),
                core.nativeGenerationSuppression(),
                core.gradeModel(),
                List.copyOf(byId.values()),
                Collections.unmodifiableMap(byId)
        );
    }

    private static OreOccurrenceCatalog.Occurrence parseOccurrence(
            JsonElement raw,
            Set<String> knownLithologies
    ) {
        if (!raw.isJsonObject()) {
            throw new IllegalArgumentException("external ore occurrence entry must be an object");
        }
        JsonObject object = raw.getAsJsonObject();
        String id = simpleId(object, "id");
        String providerMod = simpleId(object, "providerMod");
        if ("minecraft".equals(providerMod)) {
            throw new IllegalArgumentException(id + " is external but declares the minecraft provider");
        }
        String outputItem = identifier(object, "outputItem");
        List<String> hosts = stringList(requiredArray(object, "hostLithologies"), id + " hostLithologies");
        requireKnownHosts(id, hosts, knownLithologies);
        List<GeologyProvince> contexts = parseContexts(id, requiredArray(object, "provinceContexts"));
        List<String> styles = stringList(requiredArray(object, "depositStyles"), id + " depositStyles");
        requireDepositStyles(id, styles);
        List<OreOccurrenceCatalog.FormationRoute> routes = parseFormationRoutes(
                id,
                requiredArray(object, "formationRoutes"),
                knownLithologies
        );
        OreGenerationProfile generation = OreGenerationProfile.parse(
                id,
                requiredObject(object, "generation"),
                contexts,
                styles
        );
        OreGrade maximumNaturalGrade = parseMaximumNaturalGrade(id, object);
        Map<OreGrade, String> gradeBlocks = parseGradeBlocks(id, requiredObject(object, "gradeBlocks"));
        return new OreOccurrenceCatalog.Occurrence(
                id,
                providerMod,
                outputItem,
                hosts,
                contexts,
                styles,
                routes,
                generation,
                OreOccurrenceCatalog.TerrainFilter.none(),
                maximumNaturalGrade,
                gradeBlocks
        );
    }

    private static List<OreOccurrenceCatalog.FormationRoute> parseFormationRoutes(
            String material,
            JsonArray rawRoutes,
            Set<String> knownLithologies
    ) {
        if (rawRoutes.isEmpty()) {
            throw new IllegalArgumentException(material + " formationRoutes must not be empty");
        }
        List<OreOccurrenceCatalog.FormationRoute> routes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement raw : rawRoutes) {
            if (!raw.isJsonObject()) {
                throw new IllegalArgumentException(material + " formationRoutes entries must be objects");
            }
            JsonObject object = raw.getAsJsonObject();
            String routeId = simpleId(object, "id");
            if (!ids.add(routeId)) {
                throw new IllegalArgumentException(material + " duplicates formation route " + routeId);
            }
            List<String> hosts = stringList(
                    requiredArray(object, "hostLithologies"),
                    material + " route " + routeId + " hostLithologies"
            );
            requireKnownHosts(material + " route " + routeId, hosts, knownLithologies);
            List<GeologyProvince> contexts = parseContexts(
                    material + " route " + routeId,
                    requiredArray(object, "provinceContexts")
            );
            List<String> styles = stringList(
                    requiredArray(object, "depositStyles"),
                    material + " route " + routeId + " depositStyles"
            );
            requireDepositStyles(material + " route " + routeId, styles);
            routes.add(new OreOccurrenceCatalog.FormationRoute(routeId, hosts, contexts, styles));
        }
        return List.copyOf(routes);
    }

    private static OreGrade parseMaximumNaturalGrade(String material, JsonObject object) {
        JsonElement raw = object.get("maximumNaturalGrade");
        if (raw == null) {
            return OreGrade.MASSIVE;
        }
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(material + " maximumNaturalGrade must be a string");
        }
        String id = raw.getAsString();
        for (OreGrade grade : OreGrade.values()) {
            if (grade.id().equals(id)) {
                return grade;
            }
        }
        throw new IllegalArgumentException(material + " uses unknown maximumNaturalGrade " + id);
    }

    private static Map<OreGrade, String> parseGradeBlocks(String material, JsonObject object) {
        Set<String> expected = new HashSet<>();
        for (OreGrade grade : OreGrade.values()) {
            expected.add(grade.id());
        }
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException(material + " gradeBlocks must define every economic grade exactly once");
        }
        EnumMap<OreGrade, String> blocks = new EnumMap<>(OreGrade.class);
        for (OreGrade grade : OreGrade.values()) {
            blocks.put(grade, identifier(object, grade.id()));
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static List<GeologyProvince> parseContexts(String material, JsonArray rawContexts) {
        List<GeologyProvince> contexts = new ArrayList<>();
        for (String context : stringList(rawContexts, material + " provinceContexts")) {
            GeologyProvince match = null;
            for (GeologyProvince province : GeologyProvince.values()) {
                if (province.id().equals(context)) {
                    match = province;
                    break;
                }
            }
            if (match == null) {
                throw new IllegalArgumentException(material + " references unknown province context " + context);
            }
            contexts.add(match);
        }
        return List.copyOf(contexts);
    }

    private static void requireKnownHosts(String material, List<String> hosts, Set<String> knownLithologies) {
        for (String host : hosts) {
            if (!knownLithologies.contains(host)) {
                throw new IllegalArgumentException(material + " references unknown host lithology " + host);
            }
        }
    }

    private static void requireDepositStyles(String material, List<String> styles) {
        for (String style : styles) {
            if (!DEPOSIT_STYLES.contains(style)) {
                throw new IllegalArgumentException(material + " uses unsupported deposit style " + style);
            }
        }
    }

    private static List<String> stringList(JsonArray array, String field) {
        if (array.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        List<String> values = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(field + " entries must be strings");
            }
            String value = element.getAsString();
            if (!SIMPLE_ID.matcher(value).matches() || !unique.add(value)) {
                throw new IllegalArgumentException(field + " contains invalid or duplicate id " + value);
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static String simpleId(JsonObject object, String key) {
        String value = requireString(object, key);
        if (!SIMPLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " contains invalid id " + value);
        }
        return value;
    }

    private static String identifier(JsonObject object, String key) {
        String value = requireString(object, key);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " must be a namespaced identifier, found " + value);
        }
        return value;
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
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(key + " must be " + expected + ", found " + value);
        }
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
}
