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
    private static final String CONTACT_AUREOLE = "contact_aureole";

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
        if (!ready(experiment, profiles, successions, fieldProfiles)) {
            return Optional.empty();
        }

        int startX = Math.floorDiv(blockX, CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(blockZ, CHUNK_SIZE) * CHUNK_SIZE;
        return Optional.of(build(world, startX, startZ, profiles, successions, fieldProfiles));
    }

    static boolean ready(
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
        if (province != GeologyProvince.VOLCANIC_ARC
                && province != GeologyProvince.CRATONIC_SHIELD
                && province != GeologyProvince.OROGENIC_BELT) {
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
            return volcanicArcSite(world, worldSeed, site, province, architectureField);
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
                return new ResolvedColumn(
                        province,
                        y -> column.sample(y).lithology(),
                        y -> {
                            CratonicShieldModel.Sample sample = column.sample(y);
                            return new ResolvedSample(sample.lithology(), sample.bodyStyle());
                        }
                );
            };
            return new SiteContext(architectureField, resolver);
        }

        OrogenicBeltModel.Context model = OrogenicBeltModel.forSite(
                worldSeed,
                site.siteX(),
                site.siteZ(),
                world.getSeaLevel()
        );
        ColumnResolver resolver = (x, z, structural) -> {
            OrogenicBeltModel.Column column = model.column(x, z, 0.0);
            return new ResolvedColumn(
                    province,
                    y -> FaultDamageZone.contains(province, structural.tectonicColumn(), y)
                            ? "breccia"
                            : column.sample(y, structural.verticalOffset(y)).lithology(),
                    y -> {
                        if (FaultDamageZone.contains(province, structural.tectonicColumn(), y)) {
                            return new ResolvedSample("breccia", "fault_damage");
                        }
                        OrogenicBeltModel.Sample sample = column.sample(y, structural.verticalOffset(y));
                        return new ResolvedSample(sample.lithology(), sample.bodyStyle());
                    }
            );
        };
        return new SiteContext(architectureField, resolver);
    }

    private static SiteContext volcanicArcSite(
            ServerWorld world,
            long worldSeed,
            SiteKey site,
            GeologyProvince province,
            TerrainAwareStructuralField.Field architectureField
    ) {
        LithologyCatalog.Snapshot catalog = LithologyCatalog.current();
        if (!catalog.loaded()) {
            throw new IllegalStateException("volcanic arc runtime requires the lithology catalog");
        }
        VolcanicArcModel.Context model = VolcanicArcModel.forSite(
                worldSeed,
                site.siteX(),
                site.siteZ(),
                world.getSeaLevel()
        );
        ColumnResolver resolver = (x, z, structural) -> {
            VolcanicArcModel.Column column = model.column(x, z, structural.verticalOffset(0.0));
            return new ResolvedColumn(
                    province,
                    y -> volcanicLithology(column.sample(y), catalog),
                    y -> volcanicSample(column.sample(y), catalog)
            );
        };
        return new SiteContext(architectureField, resolver);
    }

    private static String volcanicLithology(
            VolcanicArcModel.Sample sample,
            LithologyCatalog.Snapshot catalog
    ) {
        return CONTACT_AUREOLE.equals(sample.bodyStyle())
                ? ContactMetamorphism.product(sample.lithology(), catalog)
                : sample.lithology();
    }

    private static ResolvedSample volcanicSample(
            VolcanicArcModel.Sample sample,
            LithologyCatalog.Snapshot catalog
    ) {
        String output = volcanicLithology(sample, catalog);
        return output.equals(sample.lithology())
                ? new ResolvedSample(output, sample.bodyStyle())
                : new ResolvedSample(output, sample.bodyStyle(), sample.lithology());
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
        ColumnResolver resolver = (x, z, structural) -> new ResolvedColumn(
                province,
                y -> {
                    if (FaultDamageZone.contains(province, structural.tectonicColumn(), y)) {
                        return "breccia";
                    }
                    return base.bedAtVerticalOffset(y, plan, structural.verticalOffset(y)).lithology();
                },
                y -> {
                    if (FaultDamageZone.contains(province, structural.tectonicColumn(), y)) {
                        return new ResolvedSample("breccia", "fault_damage");
                    }
                    String lithology = base.sampleAtVerticalOffset(y, plan, structural.verticalOffset(y)).bed().lithology();
                    return new ResolvedSample(lithology, "stratigraphic_bed");
                }
        );
        return new SiteContext(field, resolver);
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
            return columnAt(x, z).lithologyAt(y);
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
            return resolvedAt(y).lithologyAtY().apply(y);
        }

        public GeologyProvince provinceAt(int y) {
            return resolvedAt(y).province();
        }

        private ResolvedColumn resolvedAt(int y) {
            return contact == null || contact.usesPrimary(y) ? primary : neighbor;
        }
    }

    public record ResolvedSample(String lithology, String bodyStyle, String parentLithology) {
        public ResolvedSample(String lithology, String bodyStyle) {
            this(lithology, bodyStyle, null);
        }

        public ResolvedSample {
            if (lithology == null || lithology.isBlank()
                    || bodyStyle == null || bodyStyle.isBlank()
                    || parentLithology != null && parentLithology.isBlank()) {
                throw new IllegalArgumentException("background lithology/body style/parent must be valid");
            }
        }
    }

    public record ResolvedColumn(
            GeologyProvince province,
            IntFunction<String> lithologyAtY,
            IntFunction<ResolvedSample> sampleAtY
    ) {
        public ResolvedColumn {
            if (province == null || lithologyAtY == null || sampleAtY == null) {
                throw new IllegalArgumentException("background province and semantic samplers must not be null");
            }
        }
    }

    private record SiteKey(GeologyProvince province, int siteX, int siteZ) {
    }

    private record SiteContext(TerrainAwareStructuralField.Field field, ColumnResolver resolver) {
        private ResolvedColumn resolve(int x, int z) {
            return resolver.column(x, z, field.column(x, z));
        }
    }

    @FunctionalInterface
    private interface ColumnResolver {
        ResolvedColumn column(int x, int z, TerrainAwareStructuralField.Column structuralColumn);
    }
}
