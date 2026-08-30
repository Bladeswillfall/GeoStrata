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

        /**
         * Resolves all X/Z-only structural work once for a vertical worldgen column.
         * The returned column also caches contiguous Y-runs with identical output,
         * so solid rock does not repeat stratigraphic and metamorphic selection for
         * every block inside the same bed/band.
         */
        public Column column(long worldSeed, int x, int z) {
            return column(worldSeed, x, z, null);
        }

        /** Reuses a chunk-local province candidate context when worldgen has one. */
        public Column column(
                long worldSeed,
                int x,
                int z,
                GeologyProvinceSampler.Context provinceContext
        ) {
            double verticalOffset = field.verticalOffset(x, z);
            MetamorphicIntensityField.Suitability suitability = null;
            if (ownership().province() == GeologyProvince.OROGENIC_BELT) {
                TerrainMorphologySample morphology = field.localPatch().morphologyAt(x, z);
                suitability = provinceContext == null
                        ? MetamorphicIntensityField.sample(
                                worldSeed,
                                x,
                                z,
                                MetamorphicIntensityField.DEFAULT_PROVINCE_BLEND_WIDTH_BLOCKS,
                                morphology
                        ).suitability()
                        : MetamorphicIntensityField.sample(
                                worldSeed,
                                x,
                                z,
                                MetamorphicIntensityField.DEFAULT_PROVINCE_BLEND_WIDTH_BLOCKS,
                                morphology,
                                provinceContext.sample(x, z)
                        ).suitability();
            }
            return new Column(this, worldSeed, x, z, verticalOffset, suitability);
        }

        public String outputLithology(
                long worldSeed,
                int x,
                int y,
                int z,
                LithologyCatalog.Snapshot catalog
        ) {
            return column(worldSeed, x, z).outputLithology(y, catalog);
        }
    }

    /** Mutable only as a chunk-local run cache; one instance belongs to one worldgen column. */
    public static final class Column {
        private final TerrainAwareSite site;
        private final long worldSeed;
        private final int x;
        private final int z;
        private final double verticalOffset;
        private final MetamorphicIntensityField.Suitability metamorphicSuitability;
        private int cachedFromY = 1;
        private int cachedThroughY = 0;
        private String cachedLithology;

        private Column(
                TerrainAwareSite site,
                long worldSeed,
                int x,
                int z,
                double verticalOffset,
                MetamorphicIntensityField.Suitability metamorphicSuitability
        ) {
            if (site == null || !Double.isFinite(verticalOffset)) {
                throw new IllegalArgumentException("correlated column context must be valid");
            }
            this.site = site;
            this.worldSeed = worldSeed;
            this.x = x;
            this.z = z;
            this.verticalOffset = verticalOffset;
            this.metamorphicSuitability = metamorphicSuitability;
        }

        public TerrainAwareSite site() {
            return site;
        }

        public long worldSeed() {
            return worldSeed;
        }

        public int x() {
            return x;
        }

        public int z() {
            return z;
        }

        public double verticalOffset() {
            return verticalOffset;
        }

        public MetamorphicIntensityField.Suitability metamorphicSuitability() {
            return metamorphicSuitability;
        }

        public SedimentaryStratigraphicField.Sample sample(double y) {
            return site.field().baseField().sampleAtVerticalOffset(
                    y,
                    site.plan(),
                    verticalOffset
            );
        }

        public String outputLithology(int y, LithologyCatalog.Snapshot catalog) {
            if (catalog == null) {
                throw new IllegalArgumentException("lithology catalog must not be null");
            }
            if (cachedLithology != null && y >= cachedFromY && y <= cachedThroughY) {
                return cachedLithology;
            }

            SedimentaryStratigraphicField.Sample sample = sample(y);
            String parent = sample.bed().lithology();
            int runEndY = sedimentaryRunEndY(sample, y);
            if (metamorphicSuitability == null) {
                return cache(y, runEndY, parent);
            }

            String genesis = catalog.require(parent).genesis();
            if (!"mudrock".equals(genesis) && !"carbonate".equals(genesis)) {
                return cache(y, runEndY, parent);
            }

            double totalSuitability = metamorphicSuitability.slate()
                    + metamorphicSuitability.schist()
                    + metamorphicSuitability.gneiss();
            if (totalSuitability <= 0.0) {
                return cache(y, runEndY, parent);
            }
            if ("carbonate".equals(genesis)) {
                return cache(y, runEndY, "marble");
            }

            Optional<MetamorphicBandPlanner.Selection> selection = MetamorphicBandPlanner.select(
                    worldSeed,
                    site.field().baseField().siteX(),
                    site.field().baseField().siteZ(),
                    y,
                    verticalOffset,
                    site.field().baseField().cycleThicknessBlocks(),
                    metamorphicSuitability
            );
            if (selection.isEmpty()) {
                return cache(y, runEndY, parent);
            }
            runEndY = Math.min(runEndY, metamorphicRunEndY(selection.get().bandIndex(), y));
            return cache(y, runEndY, selection.get().lithology());
        }

        private int sedimentaryRunEndY(SedimentaryStratigraphicField.Sample sample, int y) {
            double nextContactCoordinate = sample.cycleIndex() + sample.bed().upperFraction();
            double nextContactY = verticalOffset
                    + site.field().baseField().cycleThicknessBlocks()
                    * (nextContactCoordinate - site.plan().phase());
            return integerBeforeBoundary(nextContactY, y);
        }

        private int metamorphicRunEndY(int bandIndex, int y) {
            double nextBandY = verticalOffset
                    + site.field().baseField().cycleThicknessBlocks() * ((double) bandIndex + 1.0);
            return integerBeforeBoundary(nextBandY, y);
        }

        private String cache(int fromY, int throughY, String lithology) {
            cachedFromY = fromY;
            cachedThroughY = Math.max(fromY, throughY);
            cachedLithology = lithology;
            return lithology;
        }

        private static int integerBeforeBoundary(double boundaryY, int currentY) {
            if (!Double.isFinite(boundaryY) || boundaryY >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            double firstDifferentY = Math.ceil(boundaryY);
            if (firstDifferentY <= currentY) {
                return currentY;
            }
            return (int) firstDifferentY - 1;
        }
    }
}
