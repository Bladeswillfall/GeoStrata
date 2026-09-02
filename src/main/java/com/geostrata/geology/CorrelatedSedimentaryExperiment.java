package com.geostrata.geology;

import com.google.gson.JsonObject;
import java.util.Set;

/** Owns the correlated sedimentary runtime contract and shared worldgen eligibility decision. */
public final class CorrelatedSedimentaryExperiment {
    private static volatile Snapshot snapshot = Snapshot.unloaded();

    private CorrelatedSedimentaryExperiment() {
    }

    public static Snapshot current() {
        return snapshot;
    }

    static void install(Snapshot loaded) {
        snapshot = loaded;
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

    static Snapshot parse(
            JsonObject experiment,
            SedimentarySuccessions.Snapshot successions,
            LithologyCatalog.Snapshot lithologies,
            GeologyProvinceProfiles.Snapshot profiles
    ) {
        return CorrelatedSedimentaryExperimentParser.parse(
                experiment,
                successions,
                lithologies,
                profiles
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

    /**
     * Vertical mutation envelope used by existing clamped runtime call sites.
     *
     * <p>The full-dimension sentinel is deliberately far outside Minecraft's
     * legal dimension-height range. Existing callers clamp these values against
     * the active world's bottom/top, so the resulting mutation domain is exactly
     * the dimension's real vertical domain without adding a second height model.</p>
     */
    public record VerticalWindow(int minOffsetBlocks, int maxOffsetBlocks) {
        private static final int DIMENSION_BOUND_SENTINEL = Integer.MAX_VALUE / 4;

        public static VerticalWindow fullDimension() {
            return new VerticalWindow(-DIMENSION_BOUND_SENTINEL, DIMENSION_BOUND_SENTINEL);
        }

        public boolean isFullDimension() {
            return minOffsetBlocks == -DIMENSION_BOUND_SENTINEL
                    && maxOffsetBlocks == DIMENSION_BOUND_SENTINEL;
        }
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

        Snapshot activated(boolean companionLoaded) {
            if (!companionLoaded || enabled) {
                return this;
            }
            return new Snapshot(
                    "experimental_runtime",
                    true,
                    targetSuccessionIds,
                    allowedProvinces,
                    supersededLithologies,
                    minimumBoundaryDistanceBlocks,
                    registrationBiomeTag,
                    hostBlockTag,
                    verticalWindow
            );
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
