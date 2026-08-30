package com.geostrata.worldgen.feature;

import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceProfiles;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.SedimentaryContactPlanner;
import com.geostrata.geology.SedimentaryFieldProfiles;
import com.geostrata.geology.SedimentaryStratigraphicField;
import com.geostrata.geology.SedimentarySuccessions;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Late experimental fill for natural host stone not claimed by richer GeoStrata bodies.
 *
 * <p>Most provinces still use the temporary province-weighted matrix while their
 * architecture is validated. Volcanic arcs are the first province-specific path:
 * metamorphic basement cut by mafic dikes/sills and local rhyolitic bodies with
 * breccia halos. Existing GeoStrata bodies, ores, caves, fluids and structure-piece
 * footprints are preserved.</p>
 */
public final class ProvinceBackgroundFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;
    private static final int PALETTE_SIZE = 4;
    private static final String CONTINUITY = "regional";
    private static final List<String> VOLCANIC_ARC_LITHOLOGIES = List.of(
            "gneiss",
            "basalt",
            "rhyolite",
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
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!experiment.enabled() || !catalog.loaded() || !profiles.loaded() || !fieldProfiles.loaded()) {
            return false;
        }

        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        int centerX = startX + CHUNK_SIZE / 2;
        int centerZ = startZ + CHUNK_SIZE / 2;
        long worldSeed = world.getSeed();
        GeologyProvinceSampler.Sample provinceSample = GeologyProvinceSampler.sample(worldSeed, centerX, centerZ);
        GeologyProvince province = provinceSample.province();
        SedimentaryStratigraphicField.Field baseField = SedimentaryStratigraphicField.forSite(
                worldSeed,
                provinceSample.siteX(),
                provinceSample.siteZ(),
                fieldProfiles.parametersFor(CONTINUITY)
        );
        TerrainAwareStructuralField.Field field = ChunkGeneratorTerrainMorphologySampler.structuralField(
                world.toServerWorld(),
                centerX,
                centerZ,
                province,
                baseField
        );

        Map<String, BlockState> outputStates;
        ColumnResolver resolver;
        if (province == GeologyProvince.VOLCANIC_ARC) {
            outputStates = outputStates(VOLCANIC_ARC_LITHOLOGIES, catalog);
            VolcanicArcModel.Context volcanicArc = VolcanicArcModel.forSite(
                    worldSeed,
                    provinceSample.siteX(),
                    provinceSample.siteZ(),
                    world.getSeaLevel()
            );
            resolver = (x, z, structuralOffset) -> {
                VolcanicArcModel.Column column = volcanicArc.column(x, z, structuralOffset);
                return y -> column.sample(y).lithology();
            };
        } else {
            SedimentarySuccessions.Succession sequence = backgroundSequence(province, profiles, catalog);
            SedimentaryContactPlanner.Plan plan = SedimentaryContactPlanner.plan(
                    worldSeed,
                    provinceSample.siteX(),
                    provinceSample.siteZ(),
                    sequence
            );
            outputStates = outputStates(sequence.beds().stream()
                    .map(SedimentarySuccessions.Bed::lithology)
                    .toList(), catalog);
            resolver = (x, z, structuralOffset) -> y -> field.baseField()
                    .sampleAtVerticalOffset(y, plan, structuralOffset)
                    .bed()
                    .lithology();
        }

        TagKey<Block> hostTag = hostTag(experiment.hostBlockTag());
        int seaLevel = world.getSeaLevel();
        int minY = Math.max(
                world.getBottomY(),
                seaLevel + experiment.verticalWindow().minOffsetBlocks()
        );
        int maxY = Math.min(
                world.getTopY() - 1,
                seaLevel + experiment.verticalWindow().maxOffsetBlocks()
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
                field,
                resolver,
                outputStates
        ) > 0;
    }

    private static SedimentarySuccessions.Succession backgroundSequence(
            GeologyProvince province,
            GeologyProvinceProfiles.Snapshot profiles,
            LithologyCatalog.Snapshot catalog
    ) {
        List<LithologyCatalog.Entry> candidates = new ArrayList<>();
        for (LithologyCatalog.Entry entry : catalog.entries()) {
            if (entry.baselineFeature().endsWith("_ore")) {
                candidates.add(entry);
            }
        }
        candidates.sort(
                Comparator.<LithologyCatalog.Entry>comparingDouble(entry -> profiles.weight(province, entry.id()))
                        .reversed()
                        .thenComparing(LithologyCatalog.Entry::id)
        );
        if (candidates.size() < PALETTE_SIZE) {
            throw new IllegalStateException("Not enough ordinary lithologies for province background " + province.id());
        }

        List<SedimentarySuccessions.Bed> beds = candidates.stream()
                .limit(PALETTE_SIZE)
                .map(entry -> new SedimentarySuccessions.Bed(entry.id(), profiles.weight(province, entry.id())))
                .toList();
        return new SedimentarySuccessions.Succession(
                "province_background_" + province.id(),
                List.of(province),
                CONTINUITY,
                beds
        );
    }

    private static int replaceChunk(
            StructureWorldAccess world,
            int startX,
            int startZ,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            TerrainAwareStructuralField.Field field,
            ColumnResolver resolver,
            Map<String, BlockState> outputStates
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
                    field,
                    resolver,
                    outputStates,
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
            TerrainAwareStructuralField.Field field,
            ColumnResolver resolver,
            Map<String, BlockState> outputStates,
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
                    double verticalOffset = field.verticalOffset(worldX, worldZ);
                    IntFunction<String> lithologyAtY = resolver.column(worldX, worldZ, verticalOffset);
                    for (int localY = minLocalY; localY <= maxLocalY; localY++) {
                        BlockState existing = states.get(localX, localY, localZ);
                        if (!existing.isIn(hostTag)) {
                            continue;
                        }
                        int worldY = sectionBottomY + localY;
                        if (insideStructurePiece(protectedStructurePieces, worldX, worldY, worldZ)) {
                            continue;
                        }
                        BlockState replacement = outputStates.get(lithologyAtY.apply(worldY));
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

    @FunctionalInterface
    private interface ColumnResolver {
        IntFunction<String> column(int x, int z, double structuralOffset);
    }
}
