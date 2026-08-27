package com.geostrata.geology;

import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

/** Samples the active terrain generator without loading neighboring chunks. */
public final class ChunkGeneratorTerrainMorphologySampler {
    public static final int DEFAULT_SAMPLE_SPACING_BLOCKS = 128;

    private ChunkGeneratorTerrainMorphologySampler() {
    }

    public static TerrainMorphologySample sample(ServerWorld world, int x, int z) {
        if (world == null) {
            throw new IllegalArgumentException("server world must not be null");
        }

        ServerChunkManager chunkManager = world.getChunkManager();
        ChunkGenerator generator = chunkManager.getChunkGenerator();
        NoiseConfig noiseConfig = chunkManager.getNoiseConfig();
        return sample(
                (sampleX, sampleZ) -> generator.getHeight(
                        sampleX,
                        sampleZ,
                        Heightmap.Type.OCEAN_FLOOR_WG,
                        world,
                        noiseConfig
                ),
                x,
                z,
                DEFAULT_SAMPLE_SPACING_BLOCKS
        );
    }

    static TerrainMorphologySample sample(HeightSource heights, int x, int z, int spacing) {
        if (heights == null) {
            throw new IllegalArgumentException("terrain height source must not be null");
        }
        if (spacing <= 0) {
            throw new IllegalArgumentException("terrain sample spacing must be positive");
        }

        return TerrainMorphologySample.fromCardinalHeights(
                heights.heightAt(x, z),
                heights.heightAt(x - spacing, z),
                heights.heightAt(x + spacing, z),
                heights.heightAt(x, z - spacing),
                heights.heightAt(x, z + spacing),
                spacing
        );
    }

    @FunctionalInterface
    interface HeightSource {
        double heightAt(int x, int z);
    }
}
