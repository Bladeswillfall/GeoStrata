package com.geostrata.geology;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Samples coarse terrain morphology from Minecraft's active {@link ChunkGenerator} without
 * loading neighboring chunks.
 *
 * <p>The adapter deliberately asks the generator for raw worldgen ocean-floor height rather
 * than inspecting already-generated surface blocks. This keeps structural diagnostics
 * deterministic from generator state and coordinates while allowing vanilla noise settings,
 * datapacks and terrain mods that participate through the active chunk generator to influence
 * the observations.</p>
 */
public final class ChunkGeneratorTerrainMorphologySampler {
    public static final int DEFAULT_SAMPLE_SPACING_BLOCKS = 128;

    private ChunkGeneratorTerrainMorphologySampler() {
    }

    public static TerrainMorphologySample sample(ServerWorld world, int x, int z) {
        return sample(world, x, z, DEFAULT_SAMPLE_SPACING_BLOCKS);
    }

    public static TerrainMorphologySample sample(ServerWorld world, int x, int z, int sampleSpacingBlocks) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }

        ServerChunkManager chunkManager = world.getChunkManager();
        ChunkGenerator generator = chunkManager.getChunkGenerator();
        NoiseConfig noiseConfig = chunkManager.getNoiseConfig();
        HeightSource heightSource = (sampleX, sampleZ) -> generator.getHeight(
                sampleX,
                sampleZ,
                Heightmap.Type.OCEAN_FLOOR_WG,
                world,
                noiseConfig
        );
        return sample(heightSource, x, z, sampleSpacingBlocks);
    }

    static TerrainMorphologySample sample(HeightSource heightSource, int x, int z, int sampleSpacingBlocks) {
        if (heightSource == null) {
            throw new IllegalArgumentException("terrain height source must not be null");
        }
        if (sampleSpacingBlocks <= 0) {
            throw new IllegalArgumentException("terrain sample spacing must be positive");
        }

        return TerrainMorphologySample.fromCardinalHeights(
                heightSource.heightAt(x, z),
                heightSource.heightAt(x - sampleSpacingBlocks, z),
                heightSource.heightAt(x + sampleSpacingBlocks, z),
                heightSource.heightAt(x, z - sampleSpacingBlocks),
                heightSource.heightAt(x, z + sampleSpacingBlocks),
                sampleSpacingBlocks
        );
    }

    @FunctionalInterface
    interface HeightSource {
        double heightAt(int x, int z);
    }
}
