package com.geostrata.worldgen.feature;

import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.CratonicShieldModel;
import com.geostrata.geology.FaultDamageZone;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceProfiles;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.OrogenicBeltModel;
import com.geostrata.geology.SedimentaryContactPlanner;
import com.geostrata.geology.SedimentaryFieldProfiles;
import com.geostrata.geology.SedimentaryStratigraphicField;
import com.geostrata.geology.SedimentarySuccessionSelector;
import com.geostrata.geology.SedimentarySuccessions;
import com.geostrata.geology.TerraneSuture;
import com.geostrata.geology.TerrainAwareStructuralField;
import com.geostrata.geology.VolcanicArcModel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Late experimental fill for natural host stone not claimed by richer GeoStrata bodies.
 *
 * <p>Metamorphic/igneous provinces use their province-specific architecture. Sedimentary
 * Basin and Rift reuse the already-loaded succession selector rather than maintaining a
 * second synthetic rock-palette model. Existing GeoStrata bodies, ores, caves, fluids and
 * structure-piece footprints are preserved.</p>
 */
public final class ProvinceBackgroundFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;
    private static final String ARCHITECTURE_CONTINUITY = "regional";
    private static final List<String> VOLCANIC_ARC_LITHOLOGIES = List.of(
            "gneiss",
            "schist",
            "quartzite",
            "basalt",
            "rhyolite",
            "breccia"
    );
    private static final List<String> CRATONIC_SHIELD_LITHOLOGIES = List.of(
            "gneiss",
            "schist",
            "quartzite",
            "marble"
    );
    private static final List<String> OROGENIC_BELT_LITHOLOGIES = List.of(
            "gneiss",
            "schist",
            "slate",
            "quartzite",
            "marble",
            "breccia"
    );

    public ProvinceBackgroundFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        LithologyCatalog.Snapshot catalog = LithologyCatalog.current();
        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        SedimentarySuccessions.Snapshot successions = SedimentarySuccessions.current();
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!experiment.enabled()
                || !catalog.loaded()
                || !profiles.loaded()
                || !successions.loaded()
                || !fieldProfiles.loaded()) {
            return false;
        }

        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        int centerX = startX + CHUNK_SIZE / 2;
        int centerZ = startZ + CHUNK_SIZE / 2;
        long worldSeed = world.getSeed();
        GeologyProvinceSampler.Context provinceContext = GeologyProvinceSampler.context(
                worldSeed,
                startX,
                startZ,
                startX + CHUNK_SIZE - 1,
                startZ + CHUNK_SIZE - 1
        );
        ColumnBackground[] columnBackgrounds = columnBackgrounds(
                world,
                worldSeed,
                startX,
                startZ,
                centerX,
                centerZ,
                provinceContext,
                catalog,
                profiles,
                successions,
                fieldProfiles
        );

        TagKey<Block> hostTag = hostTag(experiment.hostBlockTag());
        int minY = Math.max(
                world.getBottomY(),
                world.getSeaLevel() + experiment.verticalWindow().minOffsetBlocks()
        );
        int maxY = Math.min(
                world.getTopY() - 1,
                world.getSeaLevel() + experiment.verticalWindow().maxOffsetBlocks()
        );
        if (minY > maxY) {
            return false;
        }

        return replaceChunk(
                world,
                startX,
                startZ,
                minY,
                maxY,
                hostTag,
                columnBackgrounds
        ) > 0;
    }

    private static ColumnBackground[] columnBackgrounds(
            StructureWorldAccess world,
            long worldSeed,
            int startX,
            int startZ,
            int centerX,
            int centerZ,
            GeologyProvinceSampler.Context provinceContext,
            LithologyCatalog.Snapshot catalog,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        ColumnBackground[] columns = new ColumnBackground[CHUNK_SIZE * CHUNK_SIZE];
        Map<SiteKey, Background> bySite = new HashMap<>();
        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = startX + localX;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldZ = startZ + localZ;
                GeologyProvinceSampler.Sample sample = provinceContext.sample(worldX, worldZ);
                SiteKey primaryKey = new SiteKey(sample.province(), sample.siteX(), sample.siteZ());
                Background primary = backgroundForSite(
                        bySite,
                        primaryKey,
                        world,
                        worldSeed,
                        centerX,
                        centerZ,
                        catalog,
                        profiles,
                        successions,
                        fieldProfiles
                );
                if (!TerraneSuture.canCross(sample)) {
                    columns[columnIndex(localX, localZ)] = new ColumnBackground(primary, null, null);
                    continue;
                }

                SiteKey neighborKey = new SiteKey(
                        sample.neighborProvince(),
                        sample.neighborSiteX(),
                        sample.neighborSiteZ()
                );
                Background neighbor = backgroundForSite(
                        bySite,
                        neighborKey,
                        world,
                        worldSeed,
                        centerX,
                        centerZ,
                        catalog,
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
                columns[columnIndex(localX, localZ)] = new ColumnBackground(primary, neighbor, contact);
            }
        }
        return columns;
    }

    private static Background backgroundForSite(
            Map<SiteKey, Background> cache,
            SiteKey site,
            StructureWorldAccess world,
            long worldSeed,
            int centerX,
            int centerZ,
            LithologyCatalog.Snapshot catalog,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        Background cached = cache.get(site);
        if (cached != null) {
            return cached;
        }
        Background created = background(
                world,
                worldSeed,
                centerX,
                centerZ,
                site,
                catalog,
                profiles,
                successions,
                fieldProfiles
        );
        cache.put(site, created);
        return created;
    }

    private static Background background(
            StructureWorldAccess world,
            long worldSeed,
            int centerX,
            int centerZ,
            SiteKey site,
            LithologyCatalog.Snapshot catalog,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentaryFieldProfiles.Snapshot fieldProfiles
    ) {
        GeologyProvince province = site.province();
        SedimentaryStratigraphicField.Field architectureBaseField = SedimentaryStratigraphicField.forSite(
                worldSeed,
                site.siteX(),
                site.siteZ(),
                fieldProfiles.parametersFor(ARCHITECTURE_CONTINUITY)
        );
        TerrainAwareStructuralField.Field architectureField = ChunkGeneratorTerrainMorphologySampler.structuralField(
                world.toServerWorld(),
                centerX,
                centerZ,
                province,
                architectureBaseField
        );

        if (province == GeologyProvince.VOLCANIC_ARC) {
            VolcanicArcModel.Context model = VolcanicArcModel.forSite(
                    worldSeed,
                    site.siteX(),
                    site.siteZ(),
                    world.getSeaLevel()
            );
            ColumnResolver resolver = (x, z, structuralColumn) -> {
                VolcanicArcModel.Column column = model.column(x, z, structuralColumn.verticalOffset(0.0));
                return y -> column.sample(y).lithology();
            };
            return new Background(architectureField, resolver, outputStates(VOLCANIC_ARC_LITHOLOGIES, catalog));
        }
        if (province == GeologyProvince.CRATONIC_SHIELD) {
            CratonicShieldModel.Context model = CratonicShieldModel.forSite(
                    worldSeed,
                    site.siteX(),
                    site.siteZ(),
                    world.getSeaLevel()
            );
            ColumnResolver resolver = (x, z, structuralColumn) -> {
                CratonicShieldModel.Column column = model.column(x, z, structuralColumn.verticalOffset(0.0));
                return y -> column.sample(y).lithology();
            };
            return new Background(architectureField, resolver, outputStates(CRATONIC_SHIELD_LITHOLOGIES, catalog));
        }
        if (province == GeologyProvince.OROGENIC_BELT) {
            OrogenicBeltModel.Context model = OrogenicBeltModel.forSite(
                    worldSeed,
                    site.siteX(),
                    site.siteZ(),
                    world.getSeaLevel()
            );
            ColumnResolver resolver = (x, z, structuralColumn) -> {
                OrogenicBeltModel.Column column = model.column(x, z, 0.0);
                return y -> FaultDamageZone.contains(province, structuralColumn.tectonicColumn(), y)
                        ? "breccia"
                        : column.sample(y, structuralColumn.verticalOffset(y)).lithology();
            };
            return new Background(architectureField, resolver, outputStates(OROGENIC_BELT_LITHOLOGIES, catalog));
        }
        return sedimentaryBackground(
                world,
                worldSeed,
                centerX,
                centerZ,
                site,
                catalog,
                profiles,
                successions,
                fieldProfiles
        );
    }

    private static Background sedimentaryBackground(
            StructureWorldAccess world,
            long worldSeed,
            int centerX,
            int centerZ,
            SiteKey site,
            LithologyCatalog.Snapshot catalog,
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
        SedimentaryStratigraphicField.Field baseField = SedimentaryStratigraphicField.forSite(
                worldSeed,
                site.siteX(),
                site.siteZ(),
                fieldProfiles.parametersFor(sequence.continuity())
        );
        TerrainAwareStructuralField.Field field = ChunkGeneratorTerrainMorphologySampler.structuralField(
                world.toServerWorld(),
                centerX,
                centerZ,
                province,
                baseField
        );
        SedimentaryContactPlanner.Plan plan = SedimentaryContactPlanner.plan(
                worldSeed,
                site.siteX(),
                site.siteZ(),
                sequence
        );
        Map<String, BlockState> states = outputStates(sequence.beds().stream()
                .map(SedimentarySuccessions.Bed::lithology)
                .distinct()
                .toList(), catalog);
        if (province == GeologyProvince.RIFT_PROVINCE) {
            states.put("breccia", outputState("breccia", catalog));
        }
        ColumnResolver resolver = (x, z, structuralColumn) -> y -> {
            if (FaultDamageZone.contains(province, structuralColumn.tectonicColumn(), y)) {
                return "breccia";
            }
            return baseField.sampleAtVerticalOffset(y, plan, structuralColumn.verticalOffset(y))
                    .bed()
                    .lithology();
        };
        return new Background(field, resolver, states);
    }

    private static int replaceChunk(
            StructureWorldAccess world,
            int startX,
            int startZ,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            ColumnBackground[] columnBackgrounds
    ) {
        Chunk chunk = world.getChunk(
                Math.floorDiv(startX, CHUNK_SIZE),
                Math.floorDiv(startZ, CHUNK_SIZE)
        );
        StructureAccessor structures = world.toServerWorld().getStructureAccessor();
        List<BlockBox> protectedStructurePieces = structures.getStructureStarts(chunk.getPos(), structure -> true).stream()
                .flatMap(start -> start.getChildren().stream())
                .map(piece -> piece.getBoundingBox())
                .toList();

        int placed = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) {
                continue;
            }

            int sectionBottomY = chunk.sectionIndexToCoord(sectionIndex) * SECTION_SIZE;
            int sectionMinY = Math.max(minY, sectionBottomY);
            int sectionMaxY = Math.min(maxY, sectionBottomY + SECTION_SIZE - 1);
            if (sectionMinY > sectionMaxY || !section.hasAny(state -> state.isIn(hostTag))) {
                continue;
            }

            placed += replaceSection(
                    section,
                    startX,
                    startZ,
                    sectionBottomY,
                    sectionMinY,
                    sectionMaxY,
                    hostTag,
                    columnBackgrounds,
                    protectedStructurePieces
            );
        }
        if (placed > 0) {
            chunk.setNeedsSaving(true);
        }
        return placed;
    }

    private static int replaceSection(
            ChunkSection section,
            int startX,
            int startZ,
            int sectionBottomY,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            ColumnBackground[] columnBackgrounds,
            List<BlockBox> protectedStructurePieces
    ) {
        int minLocalY = minY - sectionBottomY;
        int maxLocalY = maxY - sectionBottomY;
        PalettedContainer<BlockState> states = section.getBlockStateContainer();
        int placed = 0;
        section.lock();
        try {
            for (int localX = 0; localX < SECTION_SIZE; localX++) {
                int worldX = startX + localX;
                for (int localZ = 0; localZ < SECTION_SIZE; localZ++) {
                    int worldZ = startZ + localZ;
                    ColumnBackground column = columnBackgrounds[columnIndex(localX, localZ)];
                    TerrainAwareStructuralField.Column primaryStructural = column.primary().field().column(worldX, worldZ);
                    IntFunction<String> primaryLithology = column.primary().resolver()
                            .column(worldX, worldZ, primaryStructural);
                    TerrainAwareStructuralField.Column neighborStructural = null;
                    IntFunction<String> neighborLithology = null;
                    if (column.neighbor() != null) {
                        neighborStructural = column.neighbor().field().column(worldX, worldZ);
                        neighborLithology = column.neighbor().resolver()
                                .column(worldX, worldZ, neighborStructural);
                    }

                    for (int localY = minLocalY; localY <= maxLocalY; localY++) {
                        BlockState existing = states.get(localX, localY, localZ);
                        if (!existing.isIn(hostTag)) {
                            continue;
                        }
                        int worldY = sectionBottomY + localY;
                        if (insideStructurePiece(protectedStructurePieces, worldX, worldY, worldZ)) {
                            continue;
                        }

                        boolean usePrimary = column.contact() == null || column.contact().usesPrimary(worldY);
                        Background selected = usePrimary ? column.primary() : column.neighbor();
                        IntFunction<String> lithology = usePrimary ? primaryLithology : neighborLithology;
                        BlockState replacement = selected.outputStates().get(lithology.apply(worldY));
                        if (replacement != null && !existing.equals(replacement)) {
                            states.swapUnsafe(localX, localY, localZ, replacement);
                            placed++;
                        }
                    }
                }
            }
            if (placed > 0) {
                section.calculateCounts();
            }
        } finally {
            section.unlock();
        }
        return placed;
    }

    private static int columnIndex(int localX, int localZ) {
        return localX * CHUNK_SIZE + localZ;
    }

    private static boolean insideStructurePiece(List<BlockBox> boxes, int x, int y, int z) {
        for (BlockBox box : boxes) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, BlockState> outputStates(
            List<String> lithologies,
            LithologyCatalog.Snapshot catalog
    ) {
        Map<String, BlockState> states = new HashMap<>();
        for (String lithology : lithologies) {
            states.put(lithology, outputState(lithology, catalog));
        }
        return states;
    }

    private static BlockState outputState(String lithology, LithologyCatalog.Snapshot catalog) {
        LithologyCatalog.Entry entry = catalog.require(lithology);
        Identifier blockId = Identifier.tryParse(entry.block());
        if (blockId == null) {
            throw new IllegalStateException("Invalid background lithology block id: " + entry.block());
        }
        Block block = Registries.BLOCK.getOrEmpty(blockId)
                .orElseThrow(() -> new IllegalStateException("Missing background lithology block: " + blockId));
        return block.getDefaultState();
    }

    private static TagKey<Block> hostTag(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        if (id == null) {
            throw new IllegalStateException("Invalid background geology host block tag: " + rawIdentifier);
        }
        return TagKey.of(RegistryKeys.BLOCK, id);
    }

    private record SiteKey(GeologyProvince province, int siteX, int siteZ) {
    }

    private record ColumnBackground(
            Background primary,
            Background neighbor,
            TerraneSuture.Contact contact
    ) {
        private ColumnBackground {
            if (primary == null || (neighbor == null) != (contact == null)) {
                throw new IllegalArgumentException("column background must have primary and optional paired suture");
            }
        }
    }

    private record Background(
            TerrainAwareStructuralField.Field field,
            ColumnResolver resolver,
            Map<String, BlockState> outputStates
    ) {
    }

    @FunctionalInterface
    private interface ColumnResolver {
        IntFunction<String> column(int x, int z, TerrainAwareStructuralField.Column structuralColumn);
    }
}
