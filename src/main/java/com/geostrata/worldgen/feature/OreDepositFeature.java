package com.geostrata.worldgen.feature;

import com.geostrata.block.GeoStrataBlocks;
import com.geostrata.geology.CorrelatedSedimentaryExperiment;
import com.geostrata.geology.CorrelatedSedimentaryRuntime;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.OreDepositCandidatePlanner;
import com.geostrata.geology.OreDepositExperiment;
import com.geostrata.geology.OreDepositGeometry;
import com.geostrata.geology.OreOccurrenceCatalog;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
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
    private static final int SEARCH_PADDING_BLOCKS = 128;

    public OreDepositFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        OreDepositExperiment.Snapshot experiment = OreDepositExperiment.current();
        OreOccurrenceCatalog.Snapshot occurrences = OreOccurrenceCatalog.current();
        LithologyCatalog.Snapshot lithologies = LithologyCatalog.current();
        if (!experiment.loaded() || !experiment.enabled() || !occurrences.loaded() || !lithologies.loaded()) {
            return false;
        }

        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        int endX = startX + CHUNK_SIZE - 1;
        int endZ = startZ + CHUNK_SIZE - 1;
        long worldSeed = world.toServerWorld().getSeed();
        HostResolver hosts = HostResolver.forChunk(world, startX, startZ, lithologies);

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
                    hosts
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
            HostResolver hosts
    ) {
        int minCellX = Math.floorDiv(startX - SEARCH_PADDING_BLOCKS, OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE);
        int maxCellX = Math.floorDiv(endX + SEARCH_PADDING_BLOCKS, OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE);
        int minCellY = Math.floorDiv(
                world.getBottomY() - SEARCH_PADDING_BLOCKS,
                OreDepositCandidatePlanner.VERTICAL_CELL_SIZE
        );
        int maxCellY = Math.floorDiv(
                world.getTopY() - 1 + SEARCH_PADDING_BLOCKS,
                OreDepositCandidatePlanner.VERTICAL_CELL_SIZE
        );
        int minCellZ = Math.floorDiv(startZ - SEARCH_PADDING_BLOCKS, OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE);
        int maxCellZ = Math.floorDiv(endZ + SEARCH_PADDING_BLOCKS, OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE);

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
                    GeologyProvince province = GeologyProvinceSampler.sample(
                            worldSeed,
                            proposal.anchorX(),
                            proposal.anchorZ()
                    ).province();
                    if (!occurrence.provinceContexts().contains(province)) {
                        continue;
                    }

                    OreDepositGeometry.Body body = OreDepositGeometry.forProposal(worldSeed, proposal);
                    OreDepositGeometry.Bounds bounds = body.bounds();
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
                            bounds,
                            hosts
                    );
                }
            }
        }
        return placed;
    }

    private static OreDepositCandidatePlanner.Proposal proposalForCell(
            long worldSeed,
            int cellX,
            int cellY,
            int cellZ,
            OreOccurrenceCatalog.Occurrence occurrence
    ) {
        return OreDepositCandidatePlanner.propose(
                worldSeed,
                cellX * OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE,
                cellY * OreDepositCandidatePlanner.VERTICAL_CELL_SIZE,
                cellZ * OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE,
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
            OreDepositGeometry.Bounds bounds,
            HostResolver hosts
    ) {
        int minX = Math.max(startX, bounds.minX());
        int maxX = Math.min(endX, bounds.maxX());
        int minY = Math.max(world.getBottomY(), bounds.minY());
        int maxY = Math.min(world.getTopY() - 1, bounds.maxY());
        int minZ = Math.max(startZ, bounds.minZ());
        int maxZ = Math.min(endZ, bounds.maxZ());
        Set<String> validHosts = Set.copyOf(occurrence.hostLithologies());

        int placed = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    OreDepositGeometry.Sample sample = body.sample(x, y, z);
                    if (!sample.economic()) {
                        continue;
                    }
                    mutable.set(x, y, z);
                    String host = hosts.resolve(mutable);
                    if (host == null || !validHosts.contains(host)) {
                        continue;
                    }
                    world.setBlockState(
                            mutable,
                            GeoStrataBlocks.oreState(occurrence.id(), sample.grade(), host),
                            Block.NOTIFY_LISTENERS
                    );
                    placed++;
                }
            }
        }
        return placed;
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
            Optional<TagKey<Block>> correlatedHostTag,
            int correlatedMinY,
            int correlatedMaxY
    ) {
        private static HostResolver forChunk(
                StructureWorldAccess world,
                int startX,
                int startZ,
                LithologyCatalog.Snapshot lithologies
        ) {
            Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> site = CorrelatedSedimentaryRuntime.resolve(
                    world.toServerWorld(),
                    startX + CHUNK_SIZE / 2,
                    startZ + CHUNK_SIZE / 2
            );
            if (site.isEmpty()) {
                return new HostResolver(world, hostBlocks(lithologies), Optional.empty(), Optional.empty(), 1, 0);
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
                    site,
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
            if (pos.getY() < correlatedMinY || pos.getY() > correlatedMaxY || correlatedSite.isEmpty()) {
                return null;
            }
            if (correlatedHostTag.isEmpty() || !state.isIn(correlatedHostTag.get())) {
                return null;
            }
            return correlatedSite.get().sample(pos.getX(), pos.getY(), pos.getZ()).bed().lithology();
        }
    }
}
