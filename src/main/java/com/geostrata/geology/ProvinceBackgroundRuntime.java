package com.geostrata.geology;

import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

/** Semantic province-background geology shared by mutation and pre-generation consumers. */
public final class ProvinceBackgroundRuntime {
    private static final int CHUNK_SIZE = 16;
    private static final String ARCHITECTURE_CONTINUITY = "regional";
    private static final String COMPANION_RUNTIME_STATUS = "experimental_runtime";

    private ProvinceBackgroundRuntime() {
    }

    public static Optional<Chunk> resolve(ServerWorld world, int blockX, int blockZ) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        SedimentarySuccessions.Snapshot successions = SedimentarySuccessions.current();
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!experiment.loaded()
                || !experiment.enabled()
                || !COMPANION_RUNTIME_STATUS.equals(experiment.runtimeStatus())
                || !profiles.loaded()
                || !successions.loaded()
                || !fieldProfiles.loaded()) {
            return Optional.empty();
        }

        int startX = Math.floorDiv(blockX, CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(blockZ, CHUNK_SIZE) * CHUNK_SIZE;
        return Optional.of(build(world, startX, startZ, profiles, successions, fieldProfiles));
    }

    private static Chunk build(
            ServerWorld world,
            int startX,
            int startZ,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        long worldSeed = world.getSeed();
        int centerX = startX + CHUNK_SIZE / 2;
        int centerZ = startZ + CHUNK_SIZE / 2;
        GeologyProvinceSampler.Context provinceContext = GeologyProvinceSampler.context(
                worldSeed,
                startX,
                startZ,
                startX + CHUNK_SIZE - 1,
                startZ + CHUNK_SIZE - 1
        );
        Column[] columns = new Column[CHUNK_SIZE * CHUNK_SIZE];
        Map<SiteKey, SiteContext> sites = new HashMap<>();

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int x = startX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int z = startZ + localZ;
                GeologyProvinceSampler.Sample sample = provinceContext.sample(x, z);
                SiteContext primary = site(
                        sites,
                        new SiteKey(sample.province(), sample.siteX(), sample.siteZ()),
                        world,
                        worldSeed,
                        centerX,
                        centerZ,
                        profiles,
                        successions,
                        fieldProfiles
                );
                if (!TerraneSuture.canCross(sample)) {
                    columns[columnIndex(localX, localZ)] = new Column(primary.resolve(x, z), null, null);
                    continue;
                }

                SiteContext neighbor = site(
                        sites,
                        new SiteKey(sample.neighborProvince(), sample.neighborSiteX(), sample.neighborSiteZ()),
                        world,
                        worldSeed,
                        centerX,
                        centerZ,
                        profiles,
                        successions,
                        fieldProfiles
                );
                TerraneSuture.Contact contact = TerraneSuture.forColumn(
                        sample,
                        primary.field().tectonicField(),
                        neighbor.field().tectonicField(),
                        world.getSeaLevel()
                );
                columns[columnIndex(localX, localZ)] = new Column(
                        primary.resolve(x, z),
                        neighbor.resolve(x, z),
                        contact
                );
            }
        }
        return new Chunk(startX, startZ, columns);
    }

    private static SiteContext site(
            Map<SiteKey, SiteContext> cache,
            SiteKey key,
            ServerWorld world,
            long worldSeed,
            int centerX,
            int centerZ,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        SiteContext cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        SiteContext created = createSite(
                world,
                worldSeed,
                centerX,
                centerZ,
                key,
                profiles,
                successions,
                fieldProfiles
        );
        cache.put(key, created);
        return created;
    }

    private static SiteContext createSite(
            ServerWorld world,
            long worldSeed,
            int centerX,
            int centerZ,
            SiteKey site,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        GeologyProvince province = site.province();
        SedimentaryStratigraphicField.Field architectureBase = SedimentaryStratigraphicField.forSite(
                worldSeed,
                site.siteX(),
                site.siteZ(),
                fieldProfiles.parametersFor(ARCHITECTURE_CONTINUITY)
        );
        TerrainAwareStructuralField.Field architectureField = ChunkGeneratorTerrainMorphologySampler.structuralField(
                world,
                centerX,
                centerZ,
                province,
                architectureBase
        );

        if (province == GeologyProvince.VOLCANIC_ARC) {
            VolcanicArcModel.Context model = VolcanicArcModel.forSite(
                    worldSeed,
                    site.siteX(),
                    site.siteZ(),
                    world.getSeaLevel()
            );
            ColumnResolver resolver = (x, z, structural) -> {
                VolcanicArcModel.Column column = model.column(x, z, structural.verticalOffset(0.0));
                return y -> {
                    VolcanicArcModel.Sample sample = column.sample(y);
                    return new ResolvedSample(sample.lithology(), sample.bodyStyle());
                };
            };
            return new SiteContext(province, architectureField, resolver);
        }
        if (province == GeologyProvince.CRATONIC_SHIELD) {
            CratonicShieldModel.Context model = CratonicShieldModel.forSite(
                    worldSeed,
                    site.siteX(),
                    site.siteZ(),
                    world.getSeaLevel()
            );
            ColumnResolver resolver = (x, z, structural) -> {
                CratonicShieldModel.Column column = model.column(x, z, structural.verticalOffset(0.0));
                return y -> {
                    CratonicShieldModel.Sample sample = column.sample(y);
                    return new ResolvedSample(sample.lithology(), sample.bodyStyle());
                };
            };
            return new SiteContext(province, architectureField, resolver);
        }
        if (province == GeologyProvince.OROGENIC_BELT) {
            OrogenicBeltModel.Context model = OrogenicBeltModel.forSite(
                    worldSeed,
                    site.siteX(),
                    site.siteZ(),
                    world.getSeaLevel()
            );
            ColumnResolver resolver = (x, z, structural) -> {
                OrogenicBeltModel.Column column = model.column(x, z, 0.0);
                return y -> {
                    if (FaultDamageZone.contains(province, structural.tectonicColumn(), y)) {
                        return new ResolvedSample("breccia", "fault_damage");
                    }
                    OrogenicBeltModel.Sample sample = column.sample(y, structural.verticalOffset(y));
                    return new ResolvedSample(sample.lithology(), sample.bodyStyle());
                };
            };
            return new SiteContext(province, architectureField, resolver);
        }
        return sedimentarySite(
                world,
                worldSeed,
                centerX,
                centerZ,
                site,
                profiles,
                successions,
                fieldProfiles
        );
    }

    private static SiteContext sedimentarySite(
            ServerWorld world,
            long worldSeed,
            int centerX,
            int centerZ,
            SiteKey site,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        GeologyProvince province = site.province();
        SedimentarySuccessions.Succession sequence = SedimentarySuccessionSelector.selectForSite(
                worldSeed,
                province,
                site.siteX(),
                site.siteZ(),
                profiles,
                successions
        ).succession();
        SedimentaryStratigraphicField.Field base = SedimentaryStratigraphicField.forSite(
                worldSeed,
                site.siteX(),
                site.siteZ(),
                fieldProfiles.parametersFor(sequence.continuity())
        );
        TerrainAwareStructuralField.Field field = ChunkGeneratorTerrainMorphologySampler.structuralField(
                world,
                centerX,
                centerZ,
                province,
                base
        );
        SedimentaryContactPlanner.Plan plan = SedimentaryContactPlanner.plan(
                worldSeed,
                site.siteX(),
                site.siteZ(),
                sequence
        );
        ColumnResolver resolver = (x, z, structural) -> y -> {
            if (FaultDamageZone.contains(province, structural.tectonicColumn(), y)) {
                return new ResolvedSample("breccia", "fault_damage");
            }
            String lithology = base.sampleAtVerticalOffset(y, plan, structural.verticalOffset(y)).bed().lithology();
            return new ResolvedSample(lithology, "stratigraphic_bed");
        };
        return new SiteContext(province, field, resolver);
    }

    private static int columnIndex(int localX, int localZ) {
        return localX * CHUNK_SIZE + localZ;
    }

    public record Chunk(int startX, int startZ, Column[] columns) {
        public Chunk {
            columns = columns.clone();
            if (columns.length != CHUNK_SIZE * CHUNK_SIZE) {
                throw new IllegalArgumentException("province background chunk must contain 256 columns");
            }
        }

        public ResolvedSample sampleAt(int x, int y, int z) {
            return columnAt(x, z).sampleAt(y);
        }

        public String lithologyAt(int x, int y, int z) {
            return sampleAt(x, y, z).lithology();
        }

        public String bodyStyleAt(int x, int y, int z) {
            return sampleAt(x, y, z).bodyStyle();
        }

        public GeologyProvince provinceAt(int x, int y, int z) {
            return columnAt(x, z).provinceAt(y);
        }

        private Column columnAt(int x, int z) {
            int localX = x - startX;
            int localZ = z - startZ;
            if (localX < 0 || localX >= CHUNK_SIZE || localZ < 0 || localZ >= CHUNK_SIZE) {
                throw new IllegalArgumentException("coordinate is outside province background chunk");
            }
            return columns[columnIndex(localX, localZ)];
        }
    }

    public record Column(
            ResolvedColumn primary,
            ResolvedColumn neighbor,
            TerraneSuture.Contact contact
    ) {
        public Column {
            if (primary == null || (neighbor == null) != (contact == null)) {
                throw new IllegalArgumentException("background column must have primary and optional paired suture");
            }
        }

        public ResolvedSample sampleAt(int y) {
            return resolvedAt(y).sampleAtY().apply(y);
        }

        public String lithologyAt(int y) {
            return sampleAt(y).lithology();
        }

        public String bodyStyleAt(int y) {
            return sampleAt(y).bodyStyle();
        }

        public GeologyProvince provinceAt(int y) {
            return resolvedAt(y).province();
        }

        private ResolvedColumn resolvedAt(int y) {
            return contact == null || contact.usesPrimary(y) ? primary : neighbor;
        }
    }

    public record ResolvedSample(String lithology, String bodyStyle) {
        public ResolvedSample {
            if (lithology == null || lithology.isBlank() || bodyStyle == null || bodyStyle.isBlank()) {
                throw new IllegalArgumentException("background lithology and body style must not be blank");
            }
        }
    }

    public record ResolvedColumn(GeologyProvince province, IntFunction<ResolvedSample> sampleAtY) {
        public ResolvedColumn {
            if (province == null || sampleAtY == null) {
                throw new IllegalArgumentException("background province and semantic sampler must not be null");
            }
        }
    }

    private record SiteKey(GeologyProvince province, int siteX, int siteZ) {
    }

    private record SiteContext(
            GeologyProvince province,
            TerrainAwareStructuralField.Field field,
            ColumnResolver resolver
    ) {
        private ResolvedColumn resolve(int x, int z) {
            TerrainAwareStructuralField.Column structural = field.column(x, z);
            return new ResolvedColumn(province, resolver.column(x, z, structural));
        }
    }

    @FunctionalInterface
    private interface ColumnResolver {
        IntFunction<ResolvedSample> column(int x, int z, TerrainAwareStructuralField.Column structuralColumn);
    }
}
