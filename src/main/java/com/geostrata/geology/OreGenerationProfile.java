package com.geostrata.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Human-editable tuning layered on top of hard geological ore eligibility. */
public record OreGenerationProfile(
        double activationChance,
        CandidateGrid candidateGrid,
        List<DepthBand> depthAffinity,
        Map<GeologyProvince, Double> provinceMultipliers,
        Map<String, Double> biomeMultipliers,
        Map<String, Double> depositStyleWeights,
        double bodyScale,
        double traceNormalScale,
        GradeTuning grades,
        DiscoveryStringers discoveryStringers
) {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public OreGenerationProfile {
        if (!Double.isFinite(activationChance) || activationChance < 0.0 || activationChance > 1.0) {
            throw new IllegalArgumentException("ore activation chance must be between 0 and 1");
        }
        if (candidateGrid == null || depthAffinity == null || provinceMultipliers == null
                || biomeMultipliers == null || depositStyleWeights == null || grades == null
                || discoveryStringers == null) {
            throw new IllegalArgumentException("ore generation profile fields must not be null");
        }
        if (!positive(bodyScale) || !positive(traceNormalScale)) {
            throw new IllegalArgumentException("ore body and trace scales must be finite and positive");
        }
        validateMultipliers(provinceMultipliers, 0.0, "province");
        validateMultipliers(biomeMultipliers, 1.0, "biome");
        validateMultipliers(depositStyleWeights, 0.0, "deposit style");
        depthAffinity = List.copyOf(depthAffinity);
        provinceMultipliers = Collections.unmodifiableMap(new LinkedHashMap<>(provinceMultipliers));
        biomeMultipliers = Collections.unmodifiableMap(new LinkedHashMap<>(biomeMultipliers));
        depositStyleWeights = Collections.unmodifiableMap(new LinkedHashMap<>(depositStyleWeights));
    }

    /** Compatibility constructor for callers that only need activation/affinity tuning. */
    public OreGenerationProfile(
            double activationChance,
            CandidateGrid candidateGrid,
            List<DepthBand> depthAffinity,
            Map<GeologyProvince, Double> provinceMultipliers,
            Map<String, Double> biomeMultipliers,
            Map<String, Double> depositStyleWeights
    ) {
        this(
                activationChance,
                candidateGrid,
                depthAffinity,
                provinceMultipliers,
                biomeMultipliers,
                depositStyleWeights,
                1.0,
                1.0,
                GradeTuning.defaults(),
                DiscoveryStringers.disabled()
        );
    }

    static OreGenerationProfile parse(
            String material,
            JsonObject object,
            List<GeologyProvince> allowedProvinces,
            List<String> depositStyles
    ) {
        double activationChance = requireDouble(object, "activationChance");
        CandidateGrid candidateGrid = parseCandidateGrid(material, requiredObject(object, "candidateGrid"));
        List<DepthBand> depthAffinity = parseDepthAffinity(material, optionalArray(object, "depthAffinity"));
        Map<GeologyProvince, Double> provinceMultipliers = parseProvinceMultipliers(
                material,
                optionalObject(object, "provinceMultipliers"),
                allowedProvinces
        );
        Map<String, Double> biomeMultipliers = parseBiomeMultipliers(
                material,
                optionalObject(object, "biomeMultipliers")
        );
        Map<String, Double> styleWeights = parseStyleWeights(
                material,
                optionalObject(object, "depositStyleWeights"),
                depositStyles
        );
        double bodyScale = optionalPositiveDouble(object, "bodyScale", 1.0);
        double traceNormalScale = optionalPositiveDouble(object, "traceNormalScale", 1.0);
        GradeTuning grades = parseGradeTuning(material, optionalObject(object, "gradeThresholds"));
        DiscoveryStringers discovery = parseDiscoveryStringers(
                material,
                optionalObject(object, "discoveryStringers")
        );
        return new OreGenerationProfile(
                activationChance,
                candidateGrid,
                depthAffinity,
                provinceMultipliers,
                biomeMultipliers,
                styleWeights,
                bodyScale,
                traceNormalScale,
                grades,
                discovery
        );
    }

    /** Compatibility profile for tests and programmatic occurrences that do not specify tuning. */
    public static OreGenerationProfile defaults() {
        return new OreGenerationProfile(
                1.0,
                new CandidateGrid(160, 64, 16, 8, 224, 224),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    public double depthMultiplier(int y) {
        for (DepthBand band : depthAffinity) {
            if (band.contains(y)) {
                return band.multiplierAt(y);
            }
        }
        return 1.0;
    }

    public double provinceMultiplier(GeologyProvince province) {
        return provinceMultipliers.getOrDefault(province, 1.0);
    }

    /** Strongest matching biome bonus wins so overlapping biome tags do not compound. */
    public double biomeMultiplier(Predicate<String> matchesTag) {
        double multiplier = 1.0;
        for (Map.Entry<String, Double> entry : biomeMultipliers.entrySet()) {
            if (matchesTag.test(entry.getKey())) {
                multiplier = Math.max(multiplier, entry.getValue());
            }
        }
        return multiplier;
    }

    public double depositStyleWeight(String style) {
        return depositStyleWeights.getOrDefault(style, 1.0);
    }

    private static CandidateGrid parseCandidateGrid(String material, JsonObject object) {
        try {
            return new CandidateGrid(
                    requireInt(object, "horizontalCellSize"),
                    requireInt(object, "verticalCellSize"),
                    requireInt(object, "horizontalMargin"),
                    requireInt(object, "verticalMargin"),
                    requireInt(object, "horizontalSearchPaddingBlocks"),
                    requireInt(object, "verticalSearchPaddingBlocks")
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(material + " candidateGrid: " + exception.getMessage(), exception);
        }
    }

    private static List<DepthBand> parseDepthAffinity(String material, JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<DepthBand> bands = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(material + " depthAffinity entries must be objects");
            }
            JsonObject object = element.getAsJsonObject();
            Integer minY = optionalInt(object, "minY");
            Integer maxY = optionalInt(object, "maxY");
            if (minY != null && maxY != null && minY > maxY) {
                throw new IllegalArgumentException(material + " depthAffinity minY must not exceed maxY");
            }

            JsonElement flat = object.get("multiplier");
            JsonElement start = object.get("startMultiplier");
            JsonElement end = object.get("endMultiplier");
            double startMultiplier;
            double endMultiplier;
            if (flat != null) {
                if (start != null || end != null) {
                    throw new IllegalArgumentException(material + " depthAffinity cannot mix multiplier with start/endMultiplier");
                }
                startMultiplier = finiteNonNegative(flat, material + " depthAffinity multiplier");
                endMultiplier = startMultiplier;
            } else {
                if (start == null || end == null || minY == null || maxY == null) {
                    throw new IllegalArgumentException(
                            material + " sloped depthAffinity requires minY, maxY, startMultiplier and endMultiplier"
                    );
                }
                startMultiplier = finiteNonNegative(start, material + " depthAffinity startMultiplier");
                endMultiplier = finiteNonNegative(end, material + " depthAffinity endMultiplier");
            }
            bands.add(new DepthBand(minY, maxY, startMultiplier, endMultiplier));
        }
        return List.copyOf(bands);
    }

    private static Map<GeologyProvince, Double> parseProvinceMultipliers(
            String material,
            JsonObject object,
            List<GeologyProvince> allowedProvinces
    ) {
        if (object == null) {
            return Map.of();
        }
        LinkedHashMap<GeologyProvince, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            GeologyProvince province = provinceById(entry.getKey());
            if (province == null) {
                throw new IllegalArgumentException(material + " references unknown province multiplier " + entry.getKey());
            }
            if (!allowedProvinces.contains(province)) {
                throw new IllegalArgumentException(material + " tunes province outside provinceContexts: " + entry.getKey());
            }
            result.put(province, finiteNonNegative(entry.getValue(), material + " province multiplier " + entry.getKey()));
        }
        return result;
    }

    private static Map<String, Double> parseBiomeMultipliers(String material, JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!IDENTIFIER.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException(material + " biome multiplier key must be a namespaced biome tag: " + entry.getKey());
            }
            double multiplier = finiteNonNegative(entry.getValue(), material + " biome multiplier " + entry.getKey());
            if (multiplier < 1.0) {
                throw new IllegalArgumentException(material + " biome multipliers are bonuses and must be at least 1.0");
            }
            result.put(entry.getKey(), multiplier);
        }
        return result;
    }

    private static Map<String, Double> parseStyleWeights(
            String material,
            JsonObject object,
            List<String> depositStyles
    ) {
        if (object == null) {
            return Map.of();
        }
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!depositStyles.contains(entry.getKey())) {
                throw new IllegalArgumentException(material + " weights undeclared deposit style " + entry.getKey());
            }
            result.put(entry.getKey(), finiteNonNegative(entry.getValue(), material + " deposit style weight " + entry.getKey()));
        }
        double total = depositStyles.stream().mapToDouble(style -> result.getOrDefault(style, 1.0)).sum();
        if (total <= 0.0) {
            throw new IllegalArgumentException(material + " deposit style weights must leave at least one selectable style");
        }
        return result;
    }

    private static GradeTuning parseGradeTuning(String material, JsonObject object) {
        if (object == null) {
            return GradeTuning.defaults();
        }
        try {
            return new GradeTuning(
                    requireDouble(object, "medium"),
                    requireDouble(object, "rich"),
                    requireDouble(object, "massive"),
                    requireDouble(object, "dither")
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(material + " gradeThresholds: " + exception.getMessage(), exception);
        }
    }

    private static DiscoveryStringers parseDiscoveryStringers(String material, JsonObject object) {
        if (object == null) {
            return DiscoveryStringers.disabled();
        }
        try {
            return new DiscoveryStringers(
                    requireInt(object, "count"),
                    requireDouble(object, "minLength"),
                    requireDouble(object, "maxLength"),
                    requireDouble(object, "minRadius"),
                    requireDouble(object, "maxRadius"),
                    requireDouble(object, "exposedHaloBlocks"),
                    optionalInt(object, "downwardBiasedCount", 0),
                    optionalDouble(object, "downwardBias", 0.0)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(material + " discoveryStringers: " + exception.getMessage(), exception);
        }
    }

    private static GeologyProvince provinceById(String id) {
        for (GeologyProvince province : GeologyProvince.values()) {
            if (province.id().equals(id)) {
                return province;
            }
        }
        return null;
    }

    private static void validateMultipliers(Map<?, Double> multipliers, double minimum, String kind) {
        for (Map.Entry<?, Double> entry : multipliers.entrySet()) {
            Double value = entry.getValue();
            if (entry.getKey() == null || value == null || !Double.isFinite(value) || value < minimum) {
                throw new IllegalArgumentException(
                        "ore " + kind + " multipliers must have non-null keys and finite values >= " + minimum
                );
            }
        }
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject optionalObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray optionalArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) {
            return null;
        }
        if (!element.isJsonArray()) {
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

    private static Integer optionalInt(JsonObject object, String key) {
        return object.has(key) ? requireInt(object, key) : null;
    }

    private static int optionalInt(JsonObject object, String key, int fallback) {
        Integer value = optionalInt(object, key);
        return value == null ? fallback : value;
    }

    private static double requireDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return finiteNonNegative(element, key);
    }

    private static double optionalDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        return element == null ? fallback : finite(element, key);
    }

    private static double optionalPositiveDouble(JsonObject object, String key, double fallback) {
        double value = optionalDouble(object, key, fallback);
        if (!positive(value)) {
            throw new IllegalArgumentException(key + " must be finite and positive");
        }
        return value;
    }

    private static double finiteNonNegative(JsonElement element, String field) {
        double value = finite(element, field);
        if (value < 0.0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static double finite(JsonElement element, String field) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return value;
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    public record CandidateGrid(
            int horizontalCellSize,
            int verticalCellSize,
            int horizontalMargin,
            int verticalMargin,
            int horizontalSearchPaddingBlocks,
            int verticalSearchPaddingBlocks
    ) {
        public CandidateGrid {
            if (horizontalCellSize < 1 || verticalCellSize < 1
                    || horizontalMargin < 0 || verticalMargin < 0
                    || horizontalMargin * 2 >= horizontalCellSize
                    || verticalMargin * 2 >= verticalCellSize
                    || horizontalSearchPaddingBlocks < 0 || verticalSearchPaddingBlocks < 0) {
                throw new IllegalArgumentException("dimensions must be positive and leave anchor space");
            }
        }
    }

    public record DepthBand(
            Integer minY,
            Integer maxY,
            double startMultiplier,
            double endMultiplier
    ) {
        public DepthBand {
            if (!Double.isFinite(startMultiplier) || !Double.isFinite(endMultiplier)
                    || startMultiplier < 0.0 || endMultiplier < 0.0) {
                throw new IllegalArgumentException("depth multipliers must be finite and non-negative");
            }
            if (minY != null && maxY != null && minY > maxY) {
                throw new IllegalArgumentException("depth band minY must not exceed maxY");
            }
            if ((minY == null || maxY == null) && startMultiplier != endMultiplier) {
                throw new IllegalArgumentException("open-ended depth bands must use a flat multiplier");
            }
        }

        boolean contains(int y) {
            return (minY == null || y >= minY) && (maxY == null || y <= maxY);
        }

        double multiplierAt(int y) {
            if (startMultiplier == endMultiplier || minY == null || maxY == null || minY.equals(maxY)) {
                return startMultiplier;
            }
            double t = (double) (y - minY) / (double) (maxY - minY);
            return startMultiplier + (endMultiplier - startMultiplier) * t;
        }
    }

    /** Concentration cutoffs used inside a body after deterministic dither. */
    public record GradeTuning(
            double mediumThreshold,
            double richThreshold,
            double massiveThreshold,
            double dither
    ) {
        public GradeTuning {
            if (!inUnitInterval(mediumThreshold)
                    || !inUnitInterval(richThreshold)
                    || !inUnitInterval(massiveThreshold)
                    || !inUnitInterval(dither)
                    || mediumThreshold >= richThreshold
                    || richThreshold >= massiveThreshold) {
                throw new IllegalArgumentException(
                        "grade thresholds must increase within 0..1 and dither must be within 0..1"
                );
            }
        }

        public static GradeTuning defaults() {
            return new GradeTuning(0.35, 0.60, 0.82, 0.12);
        }

        public OreGrade grade(double concentration) {
            if (concentration < mediumThreshold) {
                return OreGrade.POOR;
            }
            if (concentration < richThreshold) {
                return OreGrade.MEDIUM;
            }
            if (concentration < massiveThreshold) {
                return OreGrade.RICH;
            }
            return OreGrade.MASSIVE;
        }

        private static boolean inUnitInterval(double value) {
            return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
        }
    }

    /** Optional poor-grade discovery fractures genetically tied to the parent body. */
    public record DiscoveryStringers(
            int count,
            double minLength,
            double maxLength,
            double minRadius,
            double maxRadius,
            double exposedHaloBlocks,
            int downwardBiasedCount,
            double downwardBias
    ) {
        public DiscoveryStringers {
            if (count < 0 || downwardBiasedCount < 0 || downwardBiasedCount > count
                    || !Double.isFinite(minLength) || !Double.isFinite(maxLength)
                    || !Double.isFinite(minRadius) || !Double.isFinite(maxRadius)
                    || !Double.isFinite(exposedHaloBlocks) || !Double.isFinite(downwardBias)
                    || minLength < 0.0 || maxLength < minLength
                    || minRadius < 0.0 || maxRadius < minRadius || exposedHaloBlocks < 0.0
                    || downwardBias > 0.0) {
                throw new IllegalArgumentException("invalid discovery stringer tuning");
            }
            if (count > 0 && (minLength <= 0.0 || minRadius <= 0.0)) {
                throw new IllegalArgumentException("enabled discovery stringers need positive length and radius");
            }
        }

        public static DiscoveryStringers disabled() {
            return new DiscoveryStringers(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0);
        }

        public boolean enabled() {
            return count > 0;
        }
    }
}
