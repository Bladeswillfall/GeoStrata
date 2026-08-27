package com.geostrata.geology;

import java.util.Optional;

/** Resolves the exact chunk-normalized sedimentary field used by experimental worldgen. */
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
        return Optional.of(new Site(centerX, centerZ, ownership, succession, plan, field));
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

    public record Site(
            int chunkCenterX,
            int chunkCenterZ,
            CorrelatedSedimentaryExperiment.Ownership ownership,
            SedimentarySuccessions.Succession succession,
            SedimentaryContactPlanner.Plan plan,
            SedimentaryStratigraphicField.Field field
    ) {
        public SedimentaryStratigraphicField.Sample sample(int x, double y, int z) {
            return field.sample(x, y, z, plan);
        }
    }
}
