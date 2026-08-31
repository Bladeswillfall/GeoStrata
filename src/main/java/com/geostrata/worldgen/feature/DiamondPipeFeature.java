package com.geostrata.worldgen.feature;

import com.geostrata.GeoStrata;
import com.geostrata.geology.DiamondGeologyExperiment;
import com.geostrata.geology.DiamondGeologyPlanner;
import com.geostrata.geology.GeologyDeterminism;
import com.geostrata.geology.GeologyProvince;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.TerraneSuture;
import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Experimental, very rare kimberlite/lamproite feeder body with a restrained
 * tuff surface ring and an optional deep diamond halo.
 *
 * <p>Each chunk independently evaluates the same deterministic pipe cells and
 * writes only its own columns, so pipe continuity does not depend on chunk
 * generation order.</p>
 */
public final class DiamondPipeFeature extends Feature<DiamondPipeConfig> {
    private static final int CHUNK_SIZE = 16;
    private static final int SURFACE_RING_INNER = 6;
    private static final int SURFACE_RING_OUTER = 8;
    private static final long DIAMOND_BEARING_SALT = 0x94D049BB133111EBL;
    private static final long CLUSTER_Y_SALT = 0x369DEA0F31A53F85L;
    private static final long CLUSTER_ANGLE_SALT = 0xDB4F0B9175AE2165L;
    private static final long CLUSTER_RADIUS_SALT = 0xBBE0563303A4615FL;
    private static final long CLUSTER_SIZE_SALT = 0xA0F2EC75A1FE1575L;

    private static final TagKey<Block> SURFACE_REPLACEABLES = TagKey.of(
            RegistryKeys.BLOCK,
            GeoStrata.id("worldgen/diamond_surface_replaceables")
    );

    public DiamondPipeFeature(Codec<DiamondPipeConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DiamondPipeConfig> context) {
        DiamondGeologyExperiment.Snapshot experiment = DiamondGeologyExperiment.current();
        if (!experiment.loaded() || !experiment.enabled()) {
            return false;
        }

        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        int startX = Math.floorDiv(origin.getX(), CHUNK_SIZE) * CHUNK_SIZE;
        int startZ = Math.floorDiv(origin.getZ(), CHUNK_SIZE) * CHUNK_SIZE;
        int endX = startX + CHUNK_SIZE - 1;
        int endZ = startZ + CHUNK_SIZE - 1;
        long seed = world.getSeed();
        int pipePadding = DiamondGeologyPlanner.pipeSearchPaddingBlocks(world.getTopY() - world.getBottomY());
        DiamondGeologyPlanner.PipeKind kind = context.getConfig().kind();
        Chunk chunk = world.getChunk(Math.floorDiv(startX, CHUNK_SIZE), Math.floorDiv(startZ, CHUNK_SIZE));
        List<BlockBox> protectedStructurePieces = StructurePieceProtection.forChunk(world, chunk);

        int minCellX = Math.floorDiv(startX - pipePadding, DiamondGeologyPlanner.PIPE_CELL_SIZE);
        int maxCellX = Math.floorDiv(endX + pipePadding, DiamondGeologyPlanner.PIPE_CELL_SIZE);
        int minCellZ = Math.floorDiv(startZ - pipePadding, DiamondGeologyPlanner.PIPE_CELL_SIZE);
        int maxCellZ = Math.floorDiv(endZ + pipePadding, DiamondGeologyPlanner.PIPE_CELL_SIZE);

        int placed = 0;
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                DiamondGeologyPlanner.PipeCandidate candidate = DiamondGeologyPlanner.pipe(seed, cellX, cellZ, kind);
                if (!active(seed, candidate, experiment)) {
                    continue;
                }
                GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(
                        seed,
                        candidate.anchorX(),
                        candidate.anchorZ()
                );
                if (province.province() != GeologyProvince.CRATONIC_SHIELD || TerraneSuture.canCross(province)) {
                    continue;
                }
                placed += placeCandidate(
                        world,
                        context.getConfig(),
                        context.getRandom(),
                        candidate,
                        startX,
                        endX,
                        startZ,
                        endZ,
                        protectedStructurePieces
                );
            }
        }
        return placed > 0;
    }

    private static boolean active(
            long seed,
            DiamondGeologyPlanner.PipeCandidate candidate,
            DiamondGeologyExperiment.Snapshot experiment
    ) {
        return GeologyDeterminism.passesChance(
                experiment.pipeActivationChance(candidate.kind().id()),
                DiamondGeologyPlanner.pipeActivationRoll(seed, candidate)
        );
    }

    private static int placeCandidate(
            StructureWorldAccess world,
            DiamondPipeConfig config,
            Random random,
            DiamondGeologyPlanner.PipeCandidate candidate,
            int startX,
            int endX,
            int startZ,
            int endZ,
            List<BlockBox> protectedStructurePieces
    ) {
        int surfaceY = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, candidate.anchorX(), candidate.anchorZ()) - 1;
        if (surfaceY < world.getSeaLevel() - 4) {
            return 0;
        }

        int worldHeight = world.getTopY() - world.getBottomY();
        int deepY = Math.min(surfaceY - 12, world.getBottomY() + Math.max(8, (int) Math.round(worldHeight * 0.06)));
        if (deepY >= surfaceY - 8) {
            return 0;
        }

        int placed = placePipe(
                world,
                config,
                random,
                candidate,
                deepY,
                surfaceY,
                startX,
                endX,
                startZ,
                endZ,
                protectedStructurePieces
        );
        placed += placeSurfaceIndicator(
                world,
                config,
                random,
                candidate,
                startX,
                endX,
                startZ,
                endZ,
                protectedStructurePieces
        );
        placed += placeDeepDiamondHalo(
                world,
                candidate,
                deepY,
                surfaceY,
                startX,
                endX,
                startZ,
                endZ,
                protectedStructurePieces
        );
        return placed;
    }

    private static int placePipe(
            StructureWorldAccess world,
            DiamondPipeConfig config,
            Random random,
            DiamondGeologyPlanner.PipeCandidate candidate,
            int deepY,
            int surfaceY,
            int startX,
            int endX,
            int startZ,
            int endZ,
            List<BlockBox> protectedStructurePieces
    ) {
        int placed = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double height = Math.max(1.0, surfaceY - deepY);
        for (int y = deepY; y <= surfaceY; y++) {
            double progress = (y - deepY) / height;
            double centerX = candidate.anchorX() + candidate.tiltX() * (y - deepY);
            double centerZ = candidate.anchorZ() + candidate.tiltZ() * (y - deepY);
            double radius = candidate.baseRadius() + 1.8 * progress * progress;
            int minX = Math.max(startX, (int) Math.floor(centerX - radius));
            int maxX = Math.min(endX, (int) Math.ceil(centerX + radius));
            int minZ = Math.max(startZ, (int) Math.floor(centerZ - radius));
            int maxZ = Math.min(endZ, (int) Math.ceil(centerZ + radius));

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = x + 0.5 - centerX;
                    double dz = z + 0.5 - centerZ;
                    if (dx * dx + dz * dz > radius * radius
                            || StructurePieceProtection.contains(protectedStructurePieces, x, y, z)) {
                        continue;
                    }
                    mutable.set(x, y, z);
                    if (world.isOutOfHeightLimit(mutable) || !replaceWithPipeRock(world, mutable, config, random)) {
                        continue;
                    }
                    placed++;
                }
            }
        }
        return placed;
    }

    private static boolean replaceWithPipeRock(
            StructureWorldAccess world,
            BlockPos pos,
            DiamondPipeConfig config,
            Random random
    ) {
        BlockState existing = world.getBlockState(pos);
        OreFeatureConfig.Target target = config.targets().get(0);
        if (!target.target.test(existing, random)) {
            return false;
        }
        world.setBlockState(pos, target.state, Block.NOTIFY_LISTENERS);
        return true;
    }

    private static int placeSurfaceIndicator(
            StructureWorldAccess world,
            DiamondPipeConfig config,
            Random random,
            DiamondGeologyPlanner.PipeCandidate candidate,
            int startX,
            int endX,
            int startZ,
            int endZ,
            List<BlockBox> protectedStructurePieces
    ) {
        int placed = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int minX = Math.max(startX, candidate.anchorX() - SURFACE_RING_OUTER);
        int maxX = Math.min(endX, candidate.anchorX() + SURFACE_RING_OUTER);
        int minZ = Math.max(startZ, candidate.anchorZ() - SURFACE_RING_OUTER);
        int maxZ = Math.min(endZ, candidate.anchorZ() + SURFACE_RING_OUTER);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double distance = Math.hypot(x - candidate.anchorX(), z - candidate.anchorZ());
                boolean ring = distance >= SURFACE_RING_INNER && distance <= SURFACE_RING_OUTER;
                boolean vent = distance <= 2.25;
                if (!ring && !vent) {
                    continue;
                }

                int y = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
                mutable.set(x, y, z);
                if (world.isOutOfHeightLimit(mutable)
                        || StructurePieceProtection.contains(protectedStructurePieces, x, y, z)
                        || !world.getBlockState(mutable).isIn(SURFACE_REPLACEABLES)) {
                    continue;
                }
                if (ring) {
                    world.setBlockState(mutable, Blocks.TUFF.getDefaultState(), Block.NOTIFY_LISTENERS);
                } else if (replaceWithPipeRock(world, mutable, config, random)) {
                    placed++;
                    continue;
                } else {
                    continue;
                }
                placed++;
            }
        }
        return placed;
    }

    private static int placeDeepDiamondHalo(
            StructureWorldAccess world,
            DiamondGeologyPlanner.PipeCandidate candidate,
            int deepY,
            int surfaceY,
            int startX,
            int endX,
            int startZ,
            int endZ,
            List<BlockBox> protectedStructurePieces
    ) {
        long seed = world.getSeed();
        double bearingChance = candidate.kind() == DiamondGeologyPlanner.PipeKind.KIMBERLITE ? 0.75 : 0.55;
        double bearingRoll = DiamondGeologyPlanner.pipeClusterRoll(seed, candidate, -1, DIAMOND_BEARING_SALT);
        if (!GeologyDeterminism.passesChance(bearingChance, bearingRoll)) {
            return 0;
        }

        int worldHeight = world.getTopY() - world.getBottomY();
        int richMaxY = Math.min(surfaceY - 16, world.getBottomY() + Math.max(24, (int) Math.round(worldHeight * 0.18)));
        int richMinY = Math.max(world.getBottomY() + 5, deepY - 4);
        if (richMaxY < richMinY) {
            return 0;
        }

        int clusters = candidate.kind() == DiamondGeologyPlanner.PipeKind.KIMBERLITE ? 5 : 4;
        int placed = 0;
        for (int cluster = 0; cluster < clusters; cluster++) {
            double yRoll = DiamondGeologyPlanner.pipeClusterRoll(seed, candidate, cluster, CLUSTER_Y_SALT);
            int y = richMinY + (int) Math.floor(yRoll * (richMaxY - richMinY + 1));
            double angle = DiamondGeologyPlanner.pipeClusterRoll(seed, candidate, cluster, CLUSTER_ANGLE_SALT) * Math.PI * 2.0;
            double offset = 4.0 + DiamondGeologyPlanner.pipeClusterRoll(seed, candidate, cluster, CLUSTER_RADIUS_SALT) * 8.0;
            double centerX = candidate.anchorX() + candidate.tiltX() * (y - deepY) + Math.cos(angle) * offset;
            double centerZ = candidate.anchorZ() + candidate.tiltZ() * (y - deepY) + Math.sin(angle) * offset;
            int radius = DiamondGeologyPlanner.pipeClusterRoll(seed, candidate, cluster, CLUSTER_SIZE_SALT) < 0.22 ? 2 : 1;
            placed += placeDiamondCluster(
                    world,
                    centerX,
                    y,
                    centerZ,
                    radius,
                    startX,
                    endX,
                    startZ,
                    endZ,
                    protectedStructurePieces
            );
        }
        return placed;
    }

    static int placeDiamondCluster(
            StructureWorldAccess world,
            double centerX,
            int centerY,
            double centerZ,
            int radius,
            int startX,
            int endX,
            int startZ,
            int endZ,
            List<BlockBox> protectedStructurePieces
    ) {
        int placed = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int minX = Math.max(startX, (int) Math.floor(centerX - radius));
        int maxX = Math.min(endX, (int) Math.ceil(centerX + radius));
        int minZ = Math.max(startZ, (int) Math.floor(centerZ - radius));
        int maxZ = Math.min(endZ, (int) Math.ceil(centerZ + radius));
        int minY = Math.max(world.getBottomY(), centerY - radius);
        int maxY = Math.min(world.getTopY() - 1, centerY + radius);
        double radiusSq = radius * radius + 0.35;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    double dx = x + 0.5 - centerX;
                    double dy = y + 0.5 - centerY;
                    double dz = z + 0.5 - centerZ;
                    if (dx * dx + dy * dy + dz * dz > radiusSq
                            || StructurePieceProtection.contains(protectedStructurePieces, x, y, z)) {
                        continue;
                    }
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isIn(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                        world.setBlockState(mutable, Blocks.DEEPSLATE_DIAMOND_ORE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        placed++;
                    } else if (state.isIn(BlockTags.STONE_ORE_REPLACEABLES)) {
                        world.setBlockState(mutable, Blocks.DIAMOND_ORE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }
}
