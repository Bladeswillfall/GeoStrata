package com.geostrata.worldgen.feature;

import com.geostrata.GeoStrata;
import com.geostrata.block.GeoStrataBlocks;
import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.CorrelatedSedimentaryRuntime;
import com.geostrata.geology.FaultDamageZone;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.PetroleumBearingRockField;
import com.geostrata.geology.SedimentarySuccessions;
import com.geostrata.geology.TectonicStructuralField;
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
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Experimental chunk-local consumer of the correlated sedimentary field.
 *
 * <p>This feature type is safe to register while dormant. It performs no work
 * unless the server-data experiment contract is explicitly enabled and the
 * current chunk is owned by that experiment.</p>
 */
public final class CorrelatedSedimentaryFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_SIZE = 16;

    public CorrelatedSedimentaryFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> resolved = CorrelatedSedimentaryRuntime.resolve(
                world.toServerWorld(),
                origin.getX(),
                origin.getZ()
        );
        if (resolved.isEmpty()) {
            return false;
        }

        CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
        LithologyCatalog.Snapshot catalog = LithologyCatalog.current();
        if (!catalog.loaded()) {
            return false;
        }

        CorrelatedSedimentaryRuntime.TerrainAwareSite site = resolved.get();
        TagKey<Block> hostTag = hostTag(experiment.hostBlockTag());
        Map<String, BlockState> outputStates = outputStates(site.succession(), catalog);

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
                site,
                catalog,
                outputStates
        ) > 0;
    }

    /**
     * Mutates the current chunk section-by-section instead of probing the world
     * once for every possible Y coordinate. Empty/out-of-window sections and
     * palettes with no replaceable host state are skipped entirely.
     */
    private static int replaceChunk(
            StructureWorldAccess world,
            int startX,
            int startZ,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            CorrelatedSedimentaryRuntime.TerrainAwareSite site,
            LithologyCatalog.Snapshot catalog,
            Map<String, BlockState> outputStates
    ) {
        Chunk chunk = world.getChunk(
                Math.floorDiv(startX, CHUNK_SIZE),
                Math.floorDiv(startZ, CHUNK_SIZE)
        );
        List<BlockBox> protectedStructurePieces = StructurePieceProtection.forChunk(world, chunk);
        ColumnCache[] columns = new ColumnCache[CHUNK_SIZE * CHUNK_SIZE];
        GeologyProvinceSampler.Context provinceContext = provinceContext(world.getSeed(), startX, startZ, site);
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
            if (sectionMinY > sectionMaxY
                    || !section.hasAny(state -> replaceable(state, hostTag, site, catalog))) {
                continue;
            }

            placed += replaceSection(
                    world.getSeed(),
                    startX,
                    startZ,
                    section,
                    sectionBottomY,
                    sectionMinY,
                    sectionMaxY,
                    hostTag,
                    site,
                    catalog,
                    outputStates,
                    columns,
                    provinceContext,
                    protectedStructurePieces
            );
        }
        if (placed > 0) {
            chunk.setNeedsSaving(true);
        }
        return placed;
    }

    private static GeologyProvinceSampler.Context provinceContext(
            long worldSeed,
            int startX,
            int startZ,
            CorrelatedSedimentaryRuntime.TerrainAwareSite site
    ) {
        if (site.ownership().province() != GeologyProvince.OROGENIC_BELT) {
            return null;
        }
        return GeologyProvinceSampler.context(
                worldSeed,
                startX,
                startZ,
                startX + CHUNK_SIZE - 1,
                startZ + CHUNK_SIZE - 1
        );
    }

    /**
     * Writes directly to the section palette while holding its lock, then rebuilds
     * the section counters once if anything changed. This avoids repeating air,
     * fluid, and random-tick bookkeeping for every rock replacement while keeping
     * the section counters correct even if another mod extends the host tag.
     */
    private static int replaceSection(
            long worldSeed,
            int startX,
            int startZ,
            ChunkSection section,
            int sectionBottomY,
            int minY,
            int maxY,
            TagKey<Block> hostTag,
            CorrelatedSedimentaryRuntime.TerrainAwareSite site,
            LithologyCatalog.Snapshot catalog,
            Map<String, BlockState> outputStates,
            ColumnCache[] columns,
            GeologyProvinceSampler.Context provinceContext,
            List<BlockBox> protectedStructurePieces
    ) {
        int minLocalY = minY - sectionBottomY;
        int maxLocalY = maxY - sectionBottomY;
        PalettedContainer<BlockState> states = section.getBlockStateContainer();
        int placed = 0;
        section.lock();
        try {
            for (int localX = 0; localX < SECTION_SIZE; localX++) {
                for (int localZ = 0; localZ < SECTION_SIZE; localZ++) {
                    placed += replaceSectionColumn(
                            worldSeed,
                            startX,
                            startZ,
                            sectionBottomY,
                            localX,
                            localZ,
                            minLocalY,
                            maxLocalY,
                            states,
                            hostTag,
                            site,
                            catalog,
                            outputStates,
                            columns,
                            provinceContext,
                            protectedStructurePieces
                    );
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

    private static int replaceSectionColumn(
            long worldSeed,
            int startX,
            int startZ,
            int sectionBottomY,
            int localX,
            int localZ,
            int minLocalY,
            int maxLocalY,
            PalettedContainer<BlockState> states,
            TagKey<Block> hostTag,
            CorrelatedSedimentaryRuntime.TerrainAwareSite site,
            LithologyCatalog.Snapshot catalog,
            Map<String, BlockState> outputStates,
            ColumnCache[] columns,
            GeologyProvinceSampler.Context provinceContext,
            List<BlockBox> protectedStructurePieces
    ) {
        int x = startX + localX;
        int z = startZ + localZ;
        int columnIndex = localX * CHUNK_SIZE + localZ;
        ColumnCache column = columns[columnIndex];
        int placed = 0;
        for (int localY = minLocalY; localY <= maxLocalY; localY++) {
            BlockState existing = states.get(localX, localY, localZ);
            if (!replaceable(existing, hostTag, site, catalog)) {
                continue;
            }

            int y = sectionBottomY + localY;
            if (StructurePieceProtection.contains(protectedStructurePieces, x, y, z)) {
                continue;
            }
            if (column == null) {
                column = new ColumnCache(
                        site.column(worldSeed, x, z, provinceContext),
                        site.field().tectonicField().column(x, z),
                        PetroleumBearingRockField.column(worldSeed, x, z)
                );
                columns[columnIndex] = column;
            }
            String lithology = FaultDamageZone.contains(site.ownership().province(), column.tectonic(), y)
                    ? "breccia"
                    : column.geology().outputLithology(y, catalog);
            BlockState replacement = petroleumState(
                    lithology,
                    column.petroleum(),
                    replacementState(lithology, catalog, outputStates)
            );
            if (!existing.equals(replacement)) {
                states.swapUnsafe(localX, localY, localZ, replacement);
                placed++;
            }
        }
        return placed;
    }

    private static BlockState petroleumState(
            String lithology,
            PetroleumBearingRockField.Column petroleum,
            BlockState fallback
    ) {
        return switch (petroleum.expression(lithology)) {
            case OIL_SHALE -> GeoStrataBlocks.OIL_SHALE.getDefaultState();
            case OIL_SANDS -> GeoStrataBlocks.OIL_SANDS.getDefaultState();
            case NONE -> fallback;
        };
    }

    private static BlockState replacementState(
            String lithology,
            LithologyCatalog.Snapshot catalog,
            Map<String, BlockState> outputStates
    ) {
        BlockState replacement = outputStates.get(lithology);
        if (replacement == null) {
            replacement = outputState(lithology, catalog);
            outputStates.put(lithology, replacement);
        }
        return replacement;
    }

    private static boolean replaceable(
            BlockState existing,
            TagKey<Block> hostTag,
            CorrelatedSedimentaryRuntime.TerrainAwareSite site,
            LithologyCatalog.Snapshot catalog
    ) {
        if (existing.isIn(hostTag)) {
            return true;
        }
        if (site.ownership().province() != GeologyProvince.OROGENIC_BELT) {
            return false;
        }

        Identifier id = Registries.BLOCK.getId(existing.getBlock());
        if (!GeoStrata.MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        LithologyCatalog.Entry entry = catalog.byId().get(id.getPath());
        return entry != null && "metamorphic".equals(entry.rockClass());
    }

    private static TagKey<Block> hostTag(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        if (id == null) {
            throw new IllegalStateException("Invalid correlated experiment host block tag: " + rawIdentifier);
        }
        return TagKey.of(RegistryKeys.BLOCK, id);
    }

    private static Map<String, BlockState> outputStates(
            SedimentarySuccessions.Succession succession,
            LithologyCatalog.Snapshot catalog
    ) {
        Set<String> lithologies = new LinkedHashSet<>();
        for (SedimentarySuccessions.Bed bed : succession.beds()) {
            lithologies.add(bed.lithology());
        }

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
            throw new IllegalStateException("Invalid correlated lithology block id: " + entry.block());
        }
        Block block = Registries.BLOCK.getOrEmpty(blockId)
                .orElseThrow(() -> new IllegalStateException("Missing correlated lithology block: " + blockId));
        return block.getDefaultState();
    }

    private record ColumnCache(
            CorrelatedSedimentaryRuntime.Column geology,
            TectonicStructuralField.Column tectonic,
            PetroleumBearingRockField.Column petroleum
    ) {
    }
}
