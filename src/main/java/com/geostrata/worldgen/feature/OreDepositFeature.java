package com.geostrata.worldgen.feature;

import com.geostrata.block.GeoStrataBlocks;
import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.CorrelatedSedimentaryRuntime;
import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.FaultControlledOrePlanner;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.OreDepositCandidatePlanner;
import com.geostrata.geology.OreDepositExperiment;
import com.geostrata.geology.OreDepositGeometry;
import com.geostrata.geology.OreDiscoveryStringers;
import com.geostrata.geology.OreExposurePlacement;
import com.geostrata.geology.OreGrade;
import com.geostrata.geology.OreOccurrenceCatalog;
import com.geostrata.geology.ProvinceBackgroundRuntime;
import com.geostrata.geology.SedimentaryFieldProfiles;
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
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Experimental chunk-local consumer of GeoStrata's deterministic ore bodies.
 *
 * <p>The feature is registered in normal worldgen but is a no-op while the
 * server-data experiment remains disabled. Active bodies never write across a
 * chunk boundary from another chunk's feature invocation: each chunk evaluates
 * the same nearby deterministic proposals and mutates only its own columns.</p>
 */
public final class OreDepositFeature extends Feature<DefaultFeatureConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final int SEARCH_PADDING_BLOCKS = 224;
    private static final String STRUCTURAL_CONTINUITY = "regional";

    public OreDepositFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        OreDepositExperiment.Snapshot experiment = OreDepositExperiment.current();
        OreOccurrenceCatalog.Snapshot occurrences = OreOccurrenceCatalog.current();
        LithologyCatalog.Snapshot lithologies = LithologyCatalog.current();
        SedimentaryFieldProfiles.Snapshot fieldProfiles = SedimentaryFieldProfiles.current();
        if (!experiment.loaded()
                || !experiment.enabled()
                || !occurrences.loaded()
                || !lithologies.loaded()
                || !fieldProfiles.loaded()) {
            return false;
        }

        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        int endX = startX + CHUNK_SIZE - 1;
        int endZ = startZ + CHUNK_SIZE - 1;
        long worldSeed = world.toServerWorld().getSeed();
        double structuralCycleThickness = fieldProfiles
                .parametersFor(STRUCTURAL_CONTINUITY)
                .cycleThicknessBlocks();
        HostResolver hosts = HostResolver.forChunk(world, startX, startZ, lithologies);
        Chunk chunk = world.getChunk(Math.floorDiv(startX, CHUNK_SIZE), Math.floorDiv(startZ, CHUNK_SIZE));
        List<BlockBox> protectedStructurePieces = StructurePieceProtection.forChunk(world, chunk);

        int placed = 0;
        for (OreOccurrenceCatalog.Occurrence occurrence : occurrences.occurrences()) {
            placed += placeMaterial(
                    world,
                    worldSeed,
                    startX,
                    endX,
                    startZ,
                    endZ,
                    occurrence,
                    hosts,
                    structuralCycleThickness,
                    protectedStructurePieces
            );
        }
        return placed > 0;
    }

    private static int placeMaterial(
            StructureWorldAccess world,
            long worldSeed,
            int startX,
            int endX,
            int startZ,
            int endZ,
            OreOccurrenceCatalog.Occurrence occurrence,
            HostResolver hosts,
            double structuralCycleThickness,
            List<BlockBox> protectedStructurePieces
    ) {
        int horizontalCellSize = OreDepositCandidatePlanner.horizontalCellSize(occurrence.id());
        int minCellX = Math.floorDiv(startX - SEARCH_PADDING_BLOCKS, horizontalCellSize);
        int maxCellX = Math.floorDiv(endX + SEARCH_PADDING_BLOCKS, horizontalCellSize);
        int minCellY = Math.floorDiv(
                world.getBottomY() - SEARCH_PADDING_BLOCKS,
                OreDepositCandidatePlanner.VERTICAL_CELL_SIZE
        );
        int maxCellY = Math.floorDiv(
                world.getTopY() - 1 + SEARCH_PADDING_BLOCKS,
                OreDepositCandidatePlanner.VERTICAL_CELL_SIZE
        );
        int minCellZ = Math.floorDiv(startZ - SEARCH_PADDING_BLOCKS, horizontalCellSize);
        int maxCellZ = Math.floorDiv(endZ + SEARCH_PADDING_BLOCKS, horizontalCellSize);

        int placed = 0;
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                    OreDepositCandidatePlanner.Proposal proposal = proposalForCell(
                            worldSeed,
                            cellX,
                            cellY,
                            cellZ,
                            occurrence
                    );
                    if (!OreDepositExperiment.active(worldSeed, proposal)) {
                        continue;
                    }
                    FaultControlledOrePlanner.Binding binding = FaultControlledOrePlanner.bind(
                            worldSeed,
                            proposal,
                            structuralCycleThickness
                    );
                    proposal = binding.proposal();
                    if (!qualifiesLocation(world, worldSeed, occurrence, proposal)) {
                        continue;
                    }

                    OreDepositGeometry.Body body = binding.body(worldSeed);
                    OreDiscoveryStringers.Field discovery = OreDiscoveryStringers.forBody(body);
                    OreDepositGeometry.Bounds bounds = OreExposurePlacement.placementBounds(body, discovery);
                    if (!intersectsChunk(bounds, startX, endX, startZ, endZ, world)) {
                        continue;
                    }
                    placed += placeBody(
                            world,
                            startX,
                            endX,
                            startZ,
                            endZ,
                            occurrence,
                            body,
                            discovery,
                            bounds,
                            hosts,
                            protectedStructurePieces
                    );
                }
            }
        }
        return placed;
    }

    private static boolean qualifiesLocation(
            StructureWorldAccess world,
            long worldSeed,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositCandidatePlanner.Proposal proposal
    ) {
        GeologyProvince province = GeologyProvinceSampler.sample(
                worldSeed,
                proposal.anchorX(),
                proposal.anchorZ()
        ).province();
        return occurrence.provinceContexts().contains(province)
                && occurrence.terrainFilter().matches(ChunkGeneratorTerrainMorphologySampler.sample(
                        world.toServerWorld(),
                        proposal.anchorX(),
                        proposal.anchorZ()
                ));
    }

    private static OreDepositCandidatePlanner.Proposal proposalForCell(
            long worldSeed,
            int cellX,
            int cellY,
            int cellZ,
            OreOccurrenceCatalog.Occurrence occurrence
    ) {
        int horizontalCellSize = OreDepositCandidatePlanner.horizontalCellSize(occurrence.id());
        return OreDepositCandidatePlanner.propose(
                worldSeed,
                cellX * horizontalCellSize,
                cellY * OreDepositCandidatePlanner.VERTICAL_CELL_SIZE,
                cellZ * horizontalCellSize,
                occurrence
        );
    }

    private static boolean intersectsChunk(
            OreDepositGeometry.Bounds bounds,
            int startX,
            int endX,
            int startZ,
            int endZ,
            StructureWorldAccess world
    ) {
        return bounds.maxX() >= startX && bounds.minX() <= endX
                && bounds.maxZ() >= startZ && bounds.minZ() <= endZ
                && bounds.maxY() >= world.getBottomY() && bounds.minY() < world.getTopY();
    }

    private static int placeBody(
            StructureWorldAccess world,
            int startX,
            int endX,
            int startZ,
            int endZ,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositGeometry.Body body,
            OreDiscoveryStringers.Field discovery,
            OreDepositGeometry.Bounds bounds,
            HostResolver hosts,
            List<BlockBox> protectedStructurePieces
    ) {
        int minX = Math.max(startX, bounds.minX());
        int maxX = Math.min(endX, bounds.maxX());
        int minY = Math.max(world.getBottomY(), bounds.minY());
        int maxY = Math.min(world.getTopY() - 1, bounds.maxY());
        int minZ = Math.max(startZ, bounds.minZ());
        int maxZ = Math.min(endZ, bounds.maxZ());
        Set<String> validHosts = Set.copyOf(occurrence.hostLithologies());
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();

        int placed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (placeVoxel(
                            world,
                            occurrence,
                            body,
                            discovery,
                            hosts,
                            protectedStructurePieces,
                            validHosts,
                            mutable,
                            neighbor,
                            x,
                            y,
                            z
                    )) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static boolean placeVoxel(
            StructureWorldAccess world,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositGeometry.Body body,
            OreDiscoveryStringers.Field discovery,
            HostResolver hosts,
            List<BlockBox> protectedStructurePieces,
            Set<String> validHosts,
            BlockPos.Mutable mutable,
            BlockPos.Mutable neighbor,
            int x,
            int y,
            int z
    ) {
        if (StructurePieceProtection.contains(protectedStructurePieces, x, y, z)) {
            return false;
        }
        OreDepositGeometry.Sample sample = body.sample(x, y, z);
        boolean stringer = discovery.contains(x, y, z);
        boolean exposedFringe = !stringer
                && discovery.nearStringer(x, y, z)
                && touchesAir(world, neighbor, x, y, z);
        if (!sample.economic() && !sample.trace() && !stringer && !exposedFringe) {
            return false;
        }
        mutable.set(x, y, z);
        String host = hosts.resolve(mutable);
        if (host == null) {
            return false;
        }
        boolean discoveryOre = stringer || exposedFringe;
        boolean exposedTrace = sample.trace()
                && (exposedFringe || touchesAir(world, neighbor, x, y, z));
        boolean parentHost = validHosts.contains(host);
        OreGrade grade = parentHost
                ? OreExposurePlacement.placementGrade(sample, exposedTrace, discoveryOre)
                : discoveryOre ? OreGrade.POOR : null;
        if (grade == null) {
            return false;
        }
        world.setBlockState(
                mutable,
                GeoStrataBlocks.oreState(occurrence.id(), occurrence.capNaturalGrade(grade), host),
                Block.NOTIFY_LISTENERS
        );
        return true;
    }

    private static boolean touchesAir(
            StructureWorldAccess world,
            BlockPos.Mutable neighbor,
            int x,
            int y,
            int z
    ) {
        return isAir(world, neighbor, x + 1, y, z)
                || isAir(world, neighbor, x - 1, y, z)
                || isAir(world, neighbor, x, y + 1, z)
                || isAir(world, neighbor, x, y - 1, z)
                || isAir(world, neighbor, x, y, z + 1)
                || isAir(world, neighbor, x, y, z - 1);
    }

    private static boolean isAir(
            StructureWorldAccess world,
            BlockPos.Mutable pos,
            int x,
            int y,
            int z
    ) {
        if (y < world.getBottomY() || y >= world.getTopY()) {
            return false;
        }
        pos.set(x, y, z);
        return world.getBlockState(pos).isAir();
    }

    private static Map<Block, String> hostBlocks(LithologyCatalog.Snapshot lithologies) {
        Map<Block, String> result = new HashMap<>();
        for (LithologyCatalog.Entry entry : lithologies.entries()) {
            Identifier blockId = Identifier.tryParse(entry.block());
            if (blockId == null) {
                throw new IllegalStateException("Invalid lithology block id: " + entry.block());
            }
            Block block = Registries.BLOCK.getOrEmpty(blockId)
                    .orElseThrow(() -> new IllegalStateException("Missing lithology block: " + blockId));
            result.put(block, entry.id());
        }
        return Map.copyOf(result);
    }

    private static TagKey<Block> blockTag(String rawIdentifier) {
        Identifier id = Identifier.tryParse(rawIdentifier);
        if (id == null) {
            throw new IllegalStateException("Invalid block tag id: " + rawIdentifier);
        }
        return TagKey.of(RegistryKeys.BLOCK, id);
    }

    private record HostResolver(
            StructureWorldAccess world,
            Map<Block, String> directHosts,
            Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlatedSite,
            Optional<ProvinceBackgroundRuntime.Chunk> background,
            Optional<TagKey<Block>> virtualHostTag,
            int virtualMinY,
            int virtualMaxY
    ) {
        private static HostResolver forChunk(
                StructureWorldAccess world,
                int startX,
                int startZ,
                LithologyCatalog.Snapshot lithologies
        ) {
            Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlated = CorrelatedSedimentaryRuntime.resolve(
                    world.toServerWorld(),
                    startX + CHUNK_SIZE / 2,
                    startZ + CHUNK_SIZE / 2
            );
            Optional<ProvinceBackgroundRuntime.Chunk> background = ProvinceBackgroundRuntime.resolve(
                    world.toServerWorld(),
                    startX,
                    startZ
            );
            if (correlated.isEmpty() && background.isEmpty()) {
                return new HostResolver(
                        world,
                        hostBlocks(lithologies),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        1,
                        0
                );
            }

            CorrelatedSedimentaryExperiment.Snapshot experiment = CorrelatedSedimentaryExperiment.current();
            int minY = Math.max(
                    world.getBottomY(),
                    world.getSeaLevel() + experiment.verticalWindow().minOffsetBlocks()
            );
            int maxY = Math.min(
                    world.getTopY() - 1,
                    world.getSeaLevel() + experiment.verticalWindow().maxOffsetBlocks()
            );
            return new HostResolver(
                    world,
                    hostBlocks(lithologies),
                    correlated,
                    background,
                    Optional.of(blockTag(experiment.hostBlockTag())),
                    minY,
                    maxY
            );
        }

        private String resolve(BlockPos pos) {
            BlockState state = world.getBlockState(pos);
            String direct = directHosts.get(state.getBlock());
            if (direct != null) {
                return direct;
            }
            if (pos.getY() < virtualMinY || pos.getY() > virtualMaxY) {
                return null;
            }
            if (virtualHostTag.isEmpty() || !state.isIn(virtualHostTag.get())) {
                return null;
            }
            if (correlatedSite.isPresent()) {
                return correlatedSite.get().outputLithology(
                        world.getSeed(),
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        LithologyCatalog.current()
                );
            }
            return background.map(value -> value.lithologyAt(pos.getX(), pos.getY(), pos.getZ())).orElse(null);
        }
    }
}
