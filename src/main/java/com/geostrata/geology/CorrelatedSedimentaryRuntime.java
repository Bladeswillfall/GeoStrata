package com.geostrata.geology;

import java.util.Optional;

/** Shared resolution of the exact correlated sedimentary site consumed by runtime worldgen and diagnostics. */
public final class CorrelatedSedimentaryRuntime {
    private CorrelatedSedimentaryRuntime() {
    }

    public static Optional<Site> resolve(long worldSeed, int blockX, int blockZ) {
        return resolve(
                worldSeed,
                blockX,
                blockZ,
                CorrelatedSedimentaryExperiment.current(),
                GeologyProvinceProfiles.current(),
                SedimentarySuccessions.current(),
                SedimentaryFieldProfiles.current()
        );
    }

    static Optional<Site> resolve(
            long worldSeed,
            int blockX,
            int blockZ,
            CorrelatedSedimentaryExperiment.Snapshot experiment,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        if (!ready(experiment, profiles, successions, fieldProfiles)) {
            return Optional.empty();
        }

        int centerX = CorrelatedExperimentChunkOwnership.centerCoordinate(blockX);
        int centerZ = CorrelatedExperimentChunkOwnership.centerCoordinate(blockZ);
        CorrelatedSedimentaryExperiment.Ownership ownership = CorrelatedSedimentaryExperiment.evaluate(
                worldSeed,
                centerX,
                centerZ,
                experiment,
                profiles,
                successions
        );
        if (!ownership.owned() || ownership.successionId() == null) {
            return Optional.empty();
        }

        SedimentarySuccessions.Succession succession = successions.byId().get(ownership.successionId());
        if (succession == null) {
            throw new IllegalStateException("Owned correlated succession is not loaded: " + ownership.successionId());
        }
        return Optional.of(buildSite(worldSeed, centerX, centerZ, ownership, succession, fieldProfiles));
    }

    private static boolean ready(
            CorrelatedSedimentaryExperiment.Snapshot experiment,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        return experiment.loaded()
                && experiment.enabled()
                && profiles.loaded()
                && successions.loaded()
                && fieldProfiles.loaded();
    }

    private static Site buildSite(
            long worldSeed,
            int centerX,
            int centerZ,
            CorrelatedSedimentaryExperiment.Ownership ownership,
            SedimentarySuccessions.Succession succession,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(worldSeed, centerX, centerZ);
        SedimentaryContactPlanner.Plan plan = SedimentaryContactPlanner.plan(
                worldSeed,
                province.siteX(),
                province.siteZ(),
                succession
        );
        SedimentaryStratigraphicField.Parameters parameters = fieldProfiles.parametersFor(succession.continuity());
        SedimentaryStratigraphicField.Field field = SedimentaryStratigraphicField.forSite(
                worldSeed,
                province.siteX(),
                province.siteZ(),
                parameters
        );
        return new Site(centerX, centerZ, ownership, province, succession, plan, parameters, field);
    }

    public record Site(
            int chunkCenterX,
            int chunkCenterZ,
            CorrelatedSedimentaryExperiment.Ownership ownership,
            GeologyProvinceSampler.Sample province,
            SedimentarySuccessions.Succession succession,
            SedimentaryContactPlanner.Plan plan,
            SedimentaryStratigraphicField.Parameters parameters,
            SedimentaryStratigraphicField.Field field
    ) {
        public SedimentaryStratigraphicField.Sample sample(int x, double y, int z) {
            return field.sample(x, y, z, plan);
        }
    }
}
