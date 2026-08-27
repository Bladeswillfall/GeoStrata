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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads the disabled-by-default correlated sedimentary experiment contract and
 * owns the single eligibility decision shared by diagnostics and future worldgen.
 */
public final class CorrelatedSedimentaryExperiment implements SimpleSynchronousResourceReloadListener {
    private static final CorrelatedSedimentaryExperiment INSTANCE = new CorrelatedSedimentaryExperiment();
    private static final Identifier RELOAD_ID = GeoStrata.id("correlated_sedimentary_experiment");
    private static final Identifier EXPERIMENT_RESOURCE = GeoStrata.id("geology/correlated_sedimentary_experiment.json");
    private static final Identifier SUCCESSIONS_RESOURCE = GeoStrata.id("geology/sedimentary_successions.json");
    private static final Identifier LITHOLOGIES_RESOURCE = GeoStrata.id("geology/lithologies.json");
    private static final Identifier PROFILES_RESOURCE = GeoStrata.id("geology/province_profiles.json");

    private volatile Snapshot snapshot = Snapshot.unloaded();

    private CorrelatedSedimentaryExperiment() {
    }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(INSTANCE);
    }

    public static Snapshot current() {
        return INSTANCE.snapshot;
    }

    public static Ownership ownershipAt(long worldSeed, int x, int z) {
        return evaluate(
                worldSeed,
                x,
                z,
                current(),
                GeologyProvinceProfiles.current(),
                SedimentarySuccessions.current()
        );
    }

    public static boolean suppressesBaselineLithology(String lithology, long worldSeed, int x, int z) {
        Snapshot experiment = current();
        return experiment.loaded()
                && experiment.supersededLithologies().contains(lithology)
                && ownershipAt(worldSeed, x, z).owned();
    }

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        try {
            Snapshot loaded = parse(
                    readObject(manager, EXPERIMENT_RESOURCE),
                    readObject(manager, SUCCESSIONS_RESOURCE),
                    readObject(manager, LITHOLOGIES_RESOURCE),
                    readObject(manager, PROFILES_RESOURCE)
            );
            snapshot = loaded;
            GeoStrata.LOGGER.info(
                    "Loaded GeoStrata correlated sedimentary experiment: enabled={}, targets={}, superseded={}",
                    loaded.enabled(),
                    loaded.targetSuccessionIds(),
                    loaded.supersededLithologies()
            );
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load GeoStrata correlated sedimentary experiment", exception);
        }
    }

    static Snapshot parse(
            JsonObject experiment,
            JsonObject successionsRoot,
            JsonObject lithologiesRoot,
            JsonObject profilesRoot
    ) {
        requireInt(experiment, "schemaVersion", 1);
        requireString(experiment, "model", "geostrata:correlated_sedimentary_experiment");
        boolean enabled = requireBoolean(experiment, "enabled");
        String runtimeStatus = requireString(experiment, "runtimeStatus");
        String expectedStatus = enabled ? "experimental_runtime" : "metadata_only";
        if (!runtimeStatus.equals(expectedStatus)) {
            throw new IllegalArgumentException(
                    "correlated experiment runtimeStatus must be " + expectedStatus + " when enabled=" + enabled
            );
        }

        requireInt(successionsRoot, "schemaVersion", 1);
        requireString(successionsRoot, "model", "geostrata:sedimentary_successions");
        JsonArray rawSuccessions = requiredArray(successionsRoot, "successions");
        Map<String, SuccessionReference> successions = new HashMap<>();
        for (JsonElement element : rawSuccessions) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("succession entry must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            String id = requireString(object, "id");
            String continuity = requireString(object, "continuity");
            Set<String> contexts = stringSet(requiredArray(object, "contexts"), id + " contexts");
            Set<String> beds = new LinkedHashSet<>();
            for (JsonElement rawBed : requiredArray(object, "beds")) {
                if (!rawBed.isJsonObject()) {
                    throw new IllegalArgumentException(id + " bed must be an object");
                }
                beds.add(requireString(rawBed.getAsJsonObject(), "lithology"));
            }
            if (successions.put(id, new SuccessionReference(continuity, contexts, beds)) != null) {
                throw new IllegalArgumentException("duplicate succession id: " + id);
            }
        }

        requireInt(lithologiesRoot, "schemaVersion", 1);
        requireString(lithologiesRoot, "model", "geostrata:lithology_catalog");
        Map<String, String> rockClasses = new HashMap<>();
        for (JsonElement element : requiredArray(lithologiesRoot, "lithologies")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("lithology entry must be an object");
            }
            JsonObject object = element.getAsJsonObject();
            String id = requireString(object, "id");
            String rockClass = requireString(object, "rockClass");
            if (rockClasses.put(id, rockClass) != null) {
                throw new IllegalArgumentException("duplicate lithology id: " + id);
            }
        }

        requireInt(profilesRoot, "schemaVersion", 1);
        requireString(profilesRoot, "model", "geostrata:province_profiles");
        int blendWidth = requireInt(profilesRoot, "blendWidthBlocks");
        Set<String> profileProvinceIds = new HashSet<>();
        for (JsonElement element : requiredArray(profilesRoot, "profiles")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("province profile entry must be an object");
            }
            profileProvinceIds.add(requireString(element.getAsJsonObject(), "province"));
        }

        Set<String> targetIds = stringSet(requiredArray(experiment, "targetSuccessionIds"), "targetSuccessionIds");
        if (targetIds.size() != 1) {
            throw new IllegalArgumentException("first correlated experiment must target exactly one succession");
        }

        Set<String> targetLithologies = new LinkedHashSet<>();
        Set<String> targetContexts = new LinkedHashSet<>();
        for (String targetId : targetIds) {
            SuccessionReference reference = successions.get(targetId);
            if (reference == null) {
                throw new IllegalArgumentException("unknown target succession: " + targetId);
            }
            if (!reference.continuity().equals("regional")) {
                throw new IllegalArgumentException("initial correlated experiment target must be regional: " + targetId);
            }
            targetLithologies.addAll(reference.lithologies());
            targetContexts.addAll(reference.contexts());
        }

        Set<GeologyProvince> allowedProvinces = new LinkedHashSet<>();
        for (String provinceId : stringSet(requiredArray(experiment, "allowedProvinces"), "allowedProvinces")) {
            if (!profileProvinceIds.contains(provinceId)) {
                throw new IllegalArgumentException("allowed province has no live profile: " + provinceId);
            }
            if (!targetContexts.contains(provinceId)) {
                throw new IllegalArgumentException("allowed province is not a target succession context: " + provinceId);
            }
            allowedProvinces.add(provinceById(provinceId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown geological province: " + provinceId)));
        }
        if (allowedProvinces.isEmpty()) {
            throw new IllegalArgumentException("allowedProvinces must not be empty");
        }

        Set<String> superseded = stringSet(
                requiredArray(experiment, "supersededLithologies"),
                "supersededLithologies"
        );
        if (!superseded.equals(targetLithologies)) {
            throw new IllegalArgumentException(
                    "supersededLithologies must exactly match target succession lithologies; expected="
                            + targetLithologies + ", actual=" + superseded
            );
        }
        for (String lithology : superseded) {
            if (!"sedimentary".equals(rockClasses.get(lithology))) {
                throw new IllegalArgumentException("experiment may only supersede sedimentary lithologies: " + lithology);
            }
        }

        int minimumBoundaryDistance = requireInt(experiment, "minimumBoundaryDistanceBlocks");
        if (minimumBoundaryDistance < 0 || minimumBoundaryDistance > blendWidth) {
            throw new IllegalArgumentException(
                    "minimumBoundaryDistanceBlocks must be within 0.." + blendWidth
            );
        }

        String registrationBiomeTag = requireOwnedIdentifier(experiment, "registrationBiomeTag");
        String hostBlockTag = requireOwnedIdentifier(experiment, "hostBlockTag");

        JsonObject vertical = requiredObject(experiment, "verticalWindow");
        requireString(vertical, "anchor", "sea_level");
        int minOffset = requireInt(vertical, "minOffsetBlocks");
        int maxOffset = requireInt(vertical, "maxOffsetBlocks");
        if (minOffset < -256 || maxOffset > 256 || minOffset >= maxOffset) {
            throw new IllegalArgumentException("vertical offsets must be ordered within -256..256 blocks");
        }
        int span = maxOffset - minOffset;
        if (span < 32 || span > 256) {
            throw new IllegalArgumentException("vertical experiment span must be within 32..256 blocks");
        }

        return new Snapshot(
                runtimeStatus,
                enabled,
                Collections.unmodifiableSet(new LinkedHashSet<>(targetIds)),
                Collections.unmodifiableSet(new LinkedHashSet<>(allowedProvinces)),
                Collections.unmodifiableSet(new LinkedHashSet<>(superseded)),
                minimumBoundaryDistance,
                registrationBiomeTag,
                hostBlockTag,
                new VerticalWindow(minOffset, maxOffset)
        );
    }

    static Ownership evaluate(
            long worldSeed,
            int x,
            int z,
            Snapshot experiment,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions
    ) {
        if (!experiment.loaded() || !profiles.loaded() || !successions.loaded()) {
            return Ownership.unowned("metadata_unavailable", null, Double.NaN, null);
        }
        if (!experiment.enabled()) {
            return Ownership.unowned("disabled", null, Double.NaN, null);
        }

        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(worldSeed, x, z);
        double boundaryDistance = sample.distanceToBoundary();
        if (!experiment.allowedProvinces().contains(sample.province())) {
            return Ownership.unowned("outside_allowed_province", sample.province(), boundaryDistance, null);
        }
        if (boundaryDistance < experiment.minimumBoundaryDistanceBlocks()) {
            return Ownership.unowned("province_boundary_exclusion", sample.province(), boundaryDistance, null);
        }

        SedimentarySuccessionSelector.Selection selection = SedimentarySuccessionSelector.selectForSite(
                worldSeed,
                sample.province(),
                sample.siteX(),
                sample.siteZ(),
                profiles,
                successions
        );
        String successionId = selection.succession().id();
        if (!experiment.targetSuccessionIds().contains(successionId)) {
            return Ownership.unowned("different_succession", sample.province(), boundaryDistance, successionId);
        }

        return new Ownership(true, "owned", sample.province(), boundaryDistance, successionId);
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

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static Set<String> stringSet(JsonArray array, String description) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(description + " entries must be strings");
            }
            String value = element.getAsString();
            if (value.isBlank()) {
                throw new IllegalArgumentException(description + " entries must not be blank");
            }
            if (!values.add(value)) {
                throw new IllegalArgumentException(description + " contains duplicate value: " + value);
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException(description + " must not be empty");
        }
        return values;
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

    private static boolean requireBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static String requireOwnedIdentifier(JsonObject object, String key) {
        String value = requireString(object, key);
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(key + " must be a namespaced identifier");
        }
        if (!GeoStrata.MOD_ID.equals(value.substring(0, separator))) {
            throw new IllegalArgumentException(key + " must use the geostrata namespace");
        }
        return value;
    }

    private static Optional<GeologyProvince> provinceById(String id) {
        for (GeologyProvince province : GeologyProvince.values()) {
            if (province.id().equals(id)) {
                return Optional.of(province);
            }
        }
        return Optional.empty();
    }

    private record SuccessionReference(String continuity, Set<String> contexts, Set<String> lithologies) {
    }

    public record VerticalWindow(int minOffsetBlocks, int maxOffsetBlocks) {
    }

    public record Snapshot(
            String runtimeStatus,
            boolean enabled,
            Set<String> targetSuccessionIds,
            Set<GeologyProvince> allowedProvinces,
            Set<String> supersededLithologies,
            int minimumBoundaryDistanceBlocks,
            String registrationBiomeTag,
            String hostBlockTag,
            VerticalWindow verticalWindow
    ) {
        private static Snapshot unloaded() {
            return new Snapshot(
                    "unloaded",
                    false,
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    0,
                    "",
                    "",
                    new VerticalWindow(0, 0)
            );
        }

        public boolean loaded() {
            return !targetSuccessionIds.isEmpty();
        }
    }

    public record Ownership(
            boolean owned,
            String reason,
            GeologyProvince province,
            double boundaryDistanceBlocks,
            String successionId
    ) {
        private static Ownership unowned(
                String reason,
                GeologyProvince province,
                double boundaryDistanceBlocks,
                String successionId
        ) {
            return new Ownership(false, reason, province, boundaryDistanceBlocks, successionId);
        }
    }
}
