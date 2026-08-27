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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated host-rock occurrence contract for the staged ore system. */
public final class OreOccurrenceCatalog {
    private static final Pattern SIMPLE_ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Set<String> DEPOSIT_STYLES = Set.of(
            "coal_seam",
            "vein",
            "stratiform",
            "disseminated",
            "massive_lens_or_pocket"
    );
    private static final List<OreGrade> ECONOMIC_GRADES = List.of(OreGrade.values());

    private static volatile Snapshot snapshot = Snapshot.unloaded();

    private OreOccurrenceCatalog() {
    }

    public static Snapshot current() {
        return snapshot;
    }

    static void install(Snapshot loaded) {
        snapshot = loaded;
    }

    static Snapshot parse(LithologyCatalog.Snapshot lithologies, JsonObject root) {
        if (!lithologies.loaded()) {
            throw new IllegalArgumentException("lithology catalog must be loaded before ore occurrences");
        }
        requireInt(root, "schemaVersion", 2);
        requireString(root, "model", "geostrata:ore_occurrence_catalog");
        requireString(root, "runtimeStatus", "grade_economy_active");
        requireString(root, "generationOwner", "geostrata");
        requireString(root, "nativeGenerationSuppression", "not_implemented");

        GradeModel gradeModel = parseGradeModel(requiredObject(root, "gradeModel"));
        JsonArray rawOccurrences = requiredArray(root, "occurrences");
        if (rawOccurrences.isEmpty()) {
            throw new IllegalArgumentException("ore occurrence catalog must not be empty");
        }

        LinkedHashMap<String, Occurrence> byId = new LinkedHashMap<>();
        Set<String> claimedGradeBlocks = new HashSet<>();
        for (JsonElement rawOccurrence : rawOccurrences) {
            Occurrence occurrence = parseOccurrence(rawOccurrence, lithologies.byId().keySet());
            if (byId.put(occurrence.id(), occurrence) != null) {
                throw new IllegalArgumentException("duplicate ore occurrence id: " + occurrence.id());
            }
            for (String block : occurrence.gradeBlocks().values()) {
                if (!claimedGradeBlocks.add(block)) {
                    throw new IllegalArgumentException("graded ore block is claimed more than once: " + block);
                }
            }
        }
        return new Snapshot(
                "grade_economy_active",
                "geostrata",
                "not_implemented",
                gradeModel,
                List.copyOf(byId.values()),
                Collections.unmodifiableMap(byId)
        );
    }

    private static GradeModel parseGradeModel(JsonObject object) {
        requireString(object, "runtimeStatus", "block_loot_xp_active");
        List<String> grades = stringList(requiredArray(object, "economicGrades"), "economicGrades");
        List<String> expectedGrades = ECONOMIC_GRADES.stream().map(OreGrade::id).toList();
        if (!grades.equals(expectedGrades)) {
            throw new IllegalArgumentException("economicGrades must be poor, medium, rich, massive in order");
        }
        JsonObject trace = requiredObject(object, "trace");
        if (requireBoolean(trace, "economic")) {
            throw new IllegalArgumentException("trace evidence must remain non-economic");
        }
        requireString(trace, "runtimeStatus", "evidence_only_not_implemented");
        requireString(object, "yieldStatus", "loot_tables_active");
        requireString(object, "experienceStatus", "block_runtime_active");
        requireString(object, "fortuneMode", "minecraft:ore_drops");
        requireString(object, "silkTouchMode", "drops_self");
        List<GradeEconomy> economies = parseEconomies(requiredObject(object, "economics"));
        return new GradeModel(
                economies,
                false,
                "loot_tables_active",
                "block_runtime_active",
                "minecraft:ore_drops",
                "drops_self"
        );
    }

    private static List<GradeEconomy> parseEconomies(JsonObject object) {
        Set<String> expected = ECONOMIC_GRADES.stream().map(OreGrade::id).collect(java.util.stream.Collectors.toSet());
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException("economics must define exactly poor, medium, rich and massive");
        }
        List<GradeEconomy> economies = ECONOMIC_GRADES.stream()
                .map(grade -> parseEconomy(grade, requiredObject(object, grade.id())))
                .toList();
        requireAscendingEconomics(economies);
        return economies;
    }

    private static GradeEconomy parseEconomy(OreGrade grade, JsonObject object) {
        int baseYield = requireInt(object, "baseYield");
        JsonObject experience = requiredObject(object, "experience");
        int experienceMin = requireInt(experience, "min");
        int experienceMax = requireInt(experience, "max");
        if (baseYield < 1 || experienceMin < 0 || experienceMax < experienceMin) {
            throw new IllegalArgumentException(grade.id() + " has invalid yield or experience range");
        }
        if (baseYield != grade.baseYield()
                || experienceMin != grade.experienceMin()
                || experienceMax != grade.experienceMax()) {
            throw new IllegalArgumentException(grade.id() + " economics do not match the fixed core grade contract");
        }
        return new GradeEconomy(grade, baseYield, experienceMin, experienceMax);
    }

    private static void requireAscendingEconomics(List<GradeEconomy> economies) {
        for (int index = 1; index < economies.size(); index++) {
            GradeEconomy previous = economies.get(index - 1);
            GradeEconomy current = economies.get(index);
            if (current.baseYield() <= previous.baseYield()
                    || current.experienceMin() < previous.experienceMin()
                    || current.experienceMax() <= previous.experienceMax()) {
                throw new IllegalArgumentException("ore grade yield and XP must increase with concentration");
            }
        }
    }

    private static Occurrence parseOccurrence(JsonElement element, Set<String> knownLithologies) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("ore occurrence entry must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        String id = simpleId(object, "id");
        String providerMod = simpleId(object, "providerMod");
        String outputItem = identifier(object, "outputItem");
        List<String> hosts = stringList(requiredArray(object, "hostLithologies"), id + " hostLithologies");
        requireKnownHosts(id, hosts, knownLithologies);
        List<GeologyProvince> contexts = parseContexts(id, requiredArray(object, "provinceContexts"));
        List<String> styles = stringList(requiredArray(object, "depositStyles"), id + " depositStyles");
        requireDepositStyles(id, styles);
        Map<OreGrade, String> gradeBlocks = parseGradeBlocks(id, requiredObject(object, "gradeBlocks"));
        return new Occurrence(id, providerMod, outputItem, hosts, contexts, styles, gradeBlocks);
    }

    private static Map<OreGrade, String> parseGradeBlocks(String material, JsonObject object) {
        Set<String> expected = ECONOMIC_GRADES.stream().map(OreGrade::id).collect(java.util.stream.Collectors.toSet());
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException(material + " gradeBlocks must define every economic grade exactly once");
        }
        EnumMap<OreGrade, String> blocks = new EnumMap<>(OreGrade.class);
        for (OreGrade grade : ECONOMIC_GRADES) {
            blocks.put(grade, identifier(object, grade.id()));
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static void requireKnownHosts(String id, List<String> hosts, Set<String> knownLithologies) {
        for (String host : hosts) {
            if (!knownLithologies.contains(host)) {
                throw new IllegalArgumentException(id + " references unknown host lithology " + host);
            }
        }
    }

    private static List<GeologyProvince> parseContexts(String id, JsonArray rawContexts) {
        List<GeologyProvince> contexts = new ArrayList<>();
        for (String context : stringList(rawContexts, id + " provinceContexts")) {
            GeologyProvince province = provinceById(context)
                    .orElseThrow(() -> new IllegalArgumentException(
                            id + " references unknown province context " + context
                    ));
            contexts.add(province);
        }
        return List.copyOf(contexts);
    }

    private static void requireDepositStyles(String id, List<String> styles) {
        for (String style : styles) {
            if (!DEPOSIT_STYLES.contains(style)) {
                throw new IllegalArgumentException(id + " uses unsupported deposit style " + style);
            }
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
            if (!SIMPLE_ID.matcher(value).matches()) {
                throw new IllegalArgumentException(field + " contains invalid id " + value);
            }
            if (!unique.add(value)) {
                throw new IllegalArgumentException(field + " contains duplicate " + value);
            }
            values.add(value);
        }
        return List.copyOf(values);
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

    private static boolean requireBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    public record GradeEconomy(
            OreGrade grade,
            int baseYield,
            int experienceMin,
            int experienceMax
    ) {
    }

    public record GradeModel(
            List<GradeEconomy> economies,
            boolean traceEconomic,
            String yieldStatus,
            String experienceStatus,
            String fortuneMode,
            String silkTouchMode
    ) {
        public GradeModel {
            economies = List.copyOf(economies);
        }

        public List<String> economicGrades() {
            return economies.stream().map(economy -> economy.grade().id()).toList();
        }

        public Optional<GradeEconomy> find(OreGrade grade) {
            return economies.stream().filter(economy -> economy.grade() == grade).findFirst();
        }

        public GradeEconomy require(OreGrade grade) {
            return find(grade).orElseThrow(() -> new IllegalArgumentException("missing ore grade economy: " + grade.id()));
        }
    }

    public record Occurrence(
            String id,
            String providerMod,
            String outputItem,
            List<String> hostLithologies,
            List<GeologyProvince> provinceContexts,
            List<String> depositStyles,
            Map<OreGrade, String> gradeBlocks
    ) {
        public Occurrence {
            hostLithologies = List.copyOf(hostLithologies);
            provinceContexts = List.copyOf(provinceContexts);
            depositStyles = List.copyOf(depositStyles);
            EnumMap<OreGrade, String> copiedBlocks = new EnumMap<>(OreGrade.class);
            copiedBlocks.putAll(gradeBlocks);
            gradeBlocks = Collections.unmodifiableMap(copiedBlocks);
        }
    }

    public record Snapshot(
            String runtimeStatus,
            String generationOwner,
            String nativeGenerationSuppression,
            GradeModel gradeModel,
            List<Occurrence> occurrences,
            Map<String, Occurrence> byId
    ) {
        private static Snapshot unloaded() {
            return new Snapshot(
                    "unloaded",
                    "none",
                    "not_implemented",
                    new GradeModel(
                            List.of(),
                            false,
                            "not_implemented",
                            "not_implemented",
                            "not_implemented",
                            "not_implemented"
                    ),
                    List.of(),
                    Map.of()
            );
        }

        public boolean loaded() {
            return !occurrences.isEmpty();
        }

        public Occurrence require(String id) {
            Occurrence occurrence = byId.get(id);
            if (occurrence == null) {
                throw new IllegalArgumentException("unknown GeoStrata ore: " + id);
            }
            return occurrence;
        }
    }
}
