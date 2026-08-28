package com.geostrata.geology;

import net.minecraft.server.world.ServerWorld;

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

    /** Resolves the active-generator terrain transform used by worldgen and diagnostics. */
    public static Optional<TerrainAwareSite> resolve(ServerWorld world, int blockX, int blockZ) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }
        return resolve(world.getSeed(), blockX, blockZ)
                .map(site -> terrainAware(world, site));
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

    private static TerrainAwareSite terrainAware(ServerWorld world, Site site) {
        TerrainAwareStructuralField.Field field = ChunkGeneratorTerrainMorphologySampler.structuralField(
                world,
                site.chunkCenterX(),
                site.chunkCenterZ(),
                site.ownership().province(),
                site.field()
        );
        return new TerrainAwareSite(site, field);
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

    public record TerrainAwareSite(
            Site base,
            TerrainAwareStructuralField.Field field
    ) {
        public TerrainAwareSite {
            if (base == null || field == null) {
                throw new IllegalArgumentException("terrain-aware site components must not be null");
            }
        }

        public CorrelatedSedimentaryExperiment.Ownership ownership() {
            return base.ownership();
        }

        public SedimentarySuccessions.Succession succession() {
            return base.succession();
        }

        public SedimentaryContactPlanner.Plan plan() {
            return base.plan();
        }

        public SedimentaryStratigraphicField.Sample sample(int x, double y, int z) {
            return field.sample(x, y, z, base.plan());
        }

        public String outputLithology(
                long worldSeed,
                int x,
                int y,
                int z,
                LithologyCatalog.Snapshot catalog
        ) {
            String parent = sample(x, y, z).bed().lithology();
            if (ownership().province() != GeologyProvince.OROGENIC_BELT) {
                return parent;
            }

            String genesis = catalog.require(parent).genesis();
            if (!"mudrock".equals(genesis) && !"carbonate".equals(genesis)) {
                return parent;
            }

            MetamorphicIntensityField.Suitability suitability = MetamorphicIntensityField.sample(
                    worldSeed,
                    x,
                    z,
                    MetamorphicIntensityField.DEFAULT_PROVINCE_BLEND_WIDTH_BLOCKS,
                    field.localPatch().morphologyAt(x, z)
            ).suitability();
            Optional<MetamorphicBandPlanner.Selection> selection = MetamorphicBandPlanner.select(
                    worldSeed,
                    field.baseField().siteX(),
                    field.baseField().siteZ(),
                    y,
                    field.verticalOffset(x, z),
                    field.baseField().cycleThicknessBlocks(),
                    suitability
            );
            if (selection.isEmpty()) {
                return parent;
            }
            return "carbonate".equals(genesis) ? "marble" : selection.get().lithology();
        }
    }
}
