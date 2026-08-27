package com.geostrata.geology;

import com.geostrata.GeoStrata;
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
        return CorrelatedSedimentaryExperimentParser.parse(
                experiment,
                successionsRoot,
                lithologiesRoot,
                profilesRoot
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
